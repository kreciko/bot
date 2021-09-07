package pl.kordek.forex.bot;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.ta4j.core.*;
import org.ta4j.core.indicators.ATRIndicator;

import pl.kordek.forex.bot.api.XTBOperations;
import pl.kordek.forex.bot.api.XTBSymbolOperations;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.RobotInfo;
import pl.kordek.forex.bot.exceptions.SerializationFailedException;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.utils.FinishOperations;
import pl.kordek.forex.bot.utils.InitOperations;
import pl.kordek.forex.bot.utils.IterationOperations;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.response.SymbolResponse;
import pro.xstore.api.sync.SyncAPIConnector;


/**
 * Hello world!
 *
 */
public class App {
	private static HashMap<PERIOD_CODE, Duration> durationMap;
	private static BaseBarSeries series = null;
	private static BaseBarSeries parentSeries = null;

	private static List<String> spreadTooLargeSymbols = new ArrayList<>();

	private static HashMap<String, HashMap<String, BigDecimal>> winningRatioMap = null;

	private static RobotInfo robotInfo;

	public static void main(String[] args) {
		if(!Configuration.runBot) {
			return;
		}
		String robotInfoFileLocation = args.length == 0 ? "robot-info.bin" : args[0];
		try {
			init(robotInfoFileLocation);

			SyncAPIConnector connector = new SyncAPIConnector(Configuration.server);

			XTBOperations baseApi = new XTBOperations(connector);
			baseApi.loginToXTB();
			List<TradeRecord> openedPositions = baseApi.getOpenedPositions();

			for (String symbol : Configuration.instrumentsFX) {
				XTBSymbolOperations symbolApi = new XTBSymbolOperations(connector, symbol);

				IterationOperations iterationOperations = new IterationOperations();
				series = iterationOperations.populateBaseBarSeries(symbolApi,
						durationMap.get(Configuration.candlePeriod), Configuration.candlePeriod, Configuration.sinceDateDt);
				parentSeries = iterationOperations.populateBaseBarSeries(symbolApi,
						durationMap.get(Configuration.parentCandlePeriod), Configuration.parentCandlePeriod, Configuration.sinceDateDt);


				int endIndex = series.getEndIndex();
				boolean exitedLong = exitIfNeeded(endIndex, symbol, robotInfo.getLongTradingRecordMap().get(symbol), openedPositions);
				boolean exitedShort = exitIfNeeded(endIndex, symbol, robotInfo.getShortTradingRecordMap().get(symbol), openedPositions);
				boolean exited = exitedLong || exitedShort;

				//skip symbol, too wide spread or updated trade record
				if(!checkTradePossible(exited, isSpreadAcceptable(symbolApi.getSr()))) {
					continue;
				}

				Robot robot = new Robot(symbolApi, series, parentSeries, robotInfo
						, winningRatioMap);
				if(robot.runRobotIteration()) {
					break;
				}
			}

			FinishOperations finishOperations = new FinishOperations(robotInfo);
			finishOperations.updateBlackList(connector, durationMap.get(PERIOD_CODE.PERIOD_H1));

			finishOperations.serializeFiles(robotInfoFileLocation);

			finishOperations.printIterationInfos(baseApi.getOpenedPositions(), spreadTooLargeSymbols);
		} catch (XTBCommunicationException xtbEx) {
			System.out.println(new Date() +": XTB Exception:" + xtbEx.getMessage());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println(new Date() +": Other Exception:" + e.getStackTrace());
			return;
		}

	}

	private static void init(String robotInfoFileLocation) throws InterruptedException, SerializationFailedException, IOException {
		//if we don't run test then wait longer for the data to be updated in the XTB system
		if(Configuration.runTest) {
			Thread.sleep(Configuration.testWaitingTime);
		}
		else
			Thread.sleep(Configuration.waitingTime);
		InitOperations initOperations = new InitOperations();

		robotInfo = initOperations.deserializeFiles(robotInfoFileLocation);
		winningRatioMap = initOperations.initWinningRatios();
		durationMap = initOperations.buildDurationMap();
	}


	private static boolean exitIfNeeded(int endIndex, String symbol, TradingRecord tradingRecord,  List<TradeRecord> openedPositionsList) {
		if(null == tradingRecord) {
			System.out.println(new Date() +": There shouldn't be null trading records!");
		}

		//check if trading record has a trade that is opened (not new), but it's not existing in XTB
		if(!tradingRecord.getCurrentTrade().isNew()
				&& !openedPositionsList.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol))) {
			System.out.println(new Date() + ": Trade record was outdated for symbol "+symbol+". Updating the trading record");
			boolean exited = tradingRecord.exit(endIndex);

			if(!exited) {
				System.out.println(new Date() + ": Exit not successful");
			}
			return true;
		}
		return false;
	}

	private static boolean checkTradePossible(boolean exited, boolean isSpreadAcceptable){
		return !exited && isSpreadAcceptable && !Configuration.runTest;
	}

	private static boolean isSpreadAcceptable(SymbolResponse sr) {
		ATRIndicator atr = new ATRIndicator(series, 14);
		BigDecimal atrVal = BigDecimal.valueOf(atr.getValue(series.getEndIndex()).doubleValue());
		BigDecimal spread = BigDecimal.valueOf(sr.getSymbol().getSpreadRaw());

		BigDecimal atrVsSpread = spread.divide(atrVal, 2, RoundingMode.HALF_UP);

		if(atrVsSpread.doubleValue() > Configuration.acceptableSpreadVsAtr) {
			spreadTooLargeSymbols.add(sr.getSymbol().getSymbol()+":"+atrVsSpread.doubleValue());
			return false;
		}
		return true;
	}




}
