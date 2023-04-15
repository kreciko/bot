package pl.kordek.forex.bot;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.indicators.ATRIndicator;

import pl.kordek.forex.bot.api.BrokerAPI;
import pl.kordek.forex.bot.api.xtb.XTBAPIImpl;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.BackTestInfo;
import pl.kordek.forex.bot.domain.BlackListOperation;
import pl.kordek.forex.bot.domain.PositionInfo;
import pl.kordek.forex.bot.domain.RobotInfo;
import pl.kordek.forex.bot.exceptions.SerializationFailedException;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.strategy.StrategyTester;
import pl.kordek.forex.bot.utils.FinishOperations;
import pl.kordek.forex.bot.utils.InitOperations;


/**
 * Hello world!
 *
 */
public class App {
	private static final Logger logger = LogManager.getLogger(App.class);

	private static List<String> spreadTooLargeSymbols = new ArrayList<>();

	private static Set<String> checkedSymbols = new HashSet<>();

	private static HashMap<String, HashMap<String, BackTestInfo>> winningRatioMap = null;

	private static RobotInfo robotInfo;

	public static void main(String[] args) {
		if (!Configuration.runBot) {
			return;
		}
		String robotInfoFileLocation = args.length == 0 ? "robot-info.bin" : args[0];
		try {
			init(robotInfoFileLocation);

			BrokerAPI xtbApi = new XTBAPIImpl();
			List<PositionInfo> openedPositions = null;
			try {
				openedPositions = xtbApi.getOpenedPositions();
			}catch (Exception npe){
				logger.error("XTB screwed up. Couldn't get opened positions", npe);
				return;
			}



			List<String> openedSymbols = openedPositions.stream().map(e -> e.getSymbol()).collect(Collectors.toList());
			BaseBarSeries series = null;
			BaseBarSeries parentSeries = null;

			Set<String> instrumentsFX = Configuration.runTest ?
					Arrays.stream(Configuration.runTestFX).collect(Collectors.toSet()) :
					Arrays.stream(Configuration.instrumentsFX).collect(Collectors.toSet());

			for (String symbol : instrumentsFX) {
				xtbApi.initSymbolOperations(symbol);

				BaseBarSeries oldSeries = robotInfo.getBaseBarSeriesMap().get(symbol);
				do {
					series = xtbApi.getCharts(Configuration.sinceDateDt, Configuration.candlPeriod);

					boolean newBarAvailable = oldSeries == null || !series.getLastBar().getEndTime().equals(oldSeries.getLastBar().getEndTime());
					if (!newBarAvailable && !Configuration.runTest) {
						logger.debug("Symbol {} doesn't have updated info yet. Retrying in 1min", symbol);
						Thread.sleep(60000);
					} else
						break;
				} while (true);

				robotInfo.getBaseBarSeriesMap().remove(symbol);
				robotInfo.getBaseBarSeriesMap().put(symbol, series);
				parentSeries = xtbApi.getCharts(Configuration.sinceDateDt, Configuration.parentCandlPeriod);


				int endIndex = series.getEndIndex();
				boolean exitedLong = exitIfNeeded(endIndex, symbol, robotInfo.getLongTradingRecordMap().get(symbol), openedSymbols);
				boolean exitedShort = exitIfNeeded(endIndex, symbol, robotInfo.getShortTradingRecordMap().get(symbol), openedSymbols);
				boolean exited = exitedLong || exitedShort;
				boolean spreadAcceptable = isSpreadAcceptable(xtbApi.getSymbolResponseInfo().getSpreadRaw(), symbol, series);

				runTestsIfNeeded(xtbApi, symbol, series, parentSeries);

				if (!tradePossible(symbol, exited, spreadAcceptable, !Configuration.runTest)) {
					continue;
				}

				Robot robot = new Robot(xtbApi, symbol, series, parentSeries, robotInfo, winningRatioMap, openedPositions);
				boolean successfulTrade = robot.runRobotIteration();
				if (successfulTrade) {
					break;
				}
			}

			FinishOperations finishOperations = new FinishOperations(robotInfo);

			//Exceptional situation - a trade history method should be implemented as a partOfInterface
			finishOperations.updateBlackList(((XTBAPIImpl)xtbApi).getConnector(), Duration.ofHours(1));

			finishOperations.serializeFiles(robotInfoFileLocation);

			finishOperations.printIterationInfos(openedPositions, spreadTooLargeSymbols);
		} catch (XTBCommunicationException xtbEx) {
			logger.error("XTB Exception: {}", xtbEx.getMessage(), xtbEx);
		} catch(IOException ioe){
			if(ioe.getMessage().equals("No space left on device")){
				logger.error("No space left on device. Attempting restart");
				restartMachine();
			}
		} catch (Exception e) {
			logger.error("Other Exception: {}", e.getMessage(), e);
			cleanup(robotInfoFileLocation);
		}

	}

	private static void restartMachine(){
		Runtime runtime = Runtime.getRuntime();
		try {
			Process proc = runtime.exec("sudo reboot");
		} catch (IOException e) {
			logger.error("Restart failed");
		}
	}

	private static void cleanup(String robotInfoFileLocation){
		logger.info("Deleting robot file: "+robotInfoFileLocation);
		File robotInfoFile = new File(robotInfoFileLocation);
		if(robotInfoFile.exists())
			robotInfoFile.delete();
//		logger.info("Deleting log files");
//		File dir = new File(".");
//		File [] files = dir.listFiles(new FilenameFilter() {
//			@Override
//			public boolean accept(File dir, String name) {
//				return name.endsWith(".log");
//			}
//		});
//
//		for (File logfile : files) {
//			logfile.delete();
//		}
		logger.info("Delete successful");
	}

	private static void init(String robotInfoFileLocation) throws InterruptedException, SerializationFailedException, IOException {
		InitOperations initOperations = new InitOperations();

		robotInfo = initOperations.deserializeFiles(robotInfoFileLocation);
		winningRatioMap = initOperations.initWinningRatios();
	}


	private static boolean exitIfNeeded(int endIndex, String symbol, TradingRecord tradingRecord, List<String> openedSymbols) {
		if(null == tradingRecord) {
			logger.warn("Closing a trade failed! The tradingRecord for symbol {} is null",symbol);
			return false;
		}

		//check if trading record has a trade that is opened (not new), but it's not existing in XTB
		if(!tradingRecord.getCurrentPosition().isNew()
				&& !openedSymbols.stream().anyMatch(e -> e.equals(symbol))) {
			logger.info("Trade record was outdated for symbol {}. Updating the trading record", symbol);
			boolean exited = tradingRecord.exit(endIndex);
			if(!exited) {
				logger.error("Exit not successful");
			}
			return true;
		}
		return false;
	}

	private static void runTestsIfNeeded(BrokerAPI api, String currentSymbol, BaseBarSeries series, BaseBarSeries parentSeries){
		if(Configuration.runTest){
			StrategyTester tester = new StrategyTester(api, series, parentSeries);
			tester.strategyTest(series.getEndIndex()-Configuration.testedIndex, currentSymbol, winningRatioMap);
		}
	}

	private static boolean tradePossible(String symbol, boolean exited, boolean isSpreadAcceptable, boolean runningTest){
		if(exited){
			logger.info("Trade just exited for symbol {}. Skipping symbol", symbol);
		}
		return !exited && isSpreadAcceptable && runningTest;
	}

	private static boolean isSpreadAcceptable(Double spreadRaw, String symbol, BaseBarSeries series) {
		ATRIndicator atr = new ATRIndicator(series, 14);
		BigDecimal atrVal = BigDecimal.valueOf(atr.getValue(series.getEndIndex()).doubleValue());
		BigDecimal spread = BigDecimal.valueOf(spreadRaw);

		BigDecimal atrVsSpread = spread.divide(atrVal, 2, RoundingMode.HALF_UP);

		if(atrVsSpread.doubleValue() > Configuration.acceptableSpreadVsAtr) {
			spreadTooLargeSymbols.add(symbol+":"+atrVsSpread.doubleValue());
			return false;
		}
		return true;
	}




}
