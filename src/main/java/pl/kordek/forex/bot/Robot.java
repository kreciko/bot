package pl.kordek.forex.bot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Order;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.StopLossATRSmartIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.BlackListOperation;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.strategy.StrategyBuilder;
import pl.kordek.forex.bot.strategy.StrategyTester;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.records.TradeTransInfoRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.MarginLevelResponse;
import pro.xstore.api.message.response.MarginTradeResponse;
import pro.xstore.api.message.response.SymbolResponse;
import pro.xstore.api.sync.SyncAPIConnector;

public class Robot {

	private final int TYPE_OF_OPERATION_BUY = 0;
	private final int TYPE_OF_OPERATION_SELL = 1;
	private final double EXAMPLARY_VOLUME = 0.5;

	private BaseBarSeries series = null;
	private BaseBarSeries helperSeries = null;
	private TradingRecord longTradingRecord = null;
	private TradingRecord shortTradingRecord = null;
	private SymbolResponse symbolResponse = null;
	private double strategyStrength = 0.0;

	//if null then the operation is not blacklisted
	private BlackListOperation blackListOperation = null;

	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	private List<TradeRecord> openedPositions = null;

	private SyncAPIConnector connector = null;

	private HashMap<String, HashMap<String, BigDecimal>> winningRatioMap = null;

	private RobotUtilities utilities = null;


	public Robot(BaseBarSeries series, BaseBarSeries helperSeries, TradingRecord longTradingRecord, TradingRecord shortTradingRecord,
			 HashMap<String, HashMap<String, BigDecimal>> winningRatioMap, BlackListOperation bListOp,
			List<TradeRecord> openedPositions, SymbolResponse symbolResponse, SyncAPIConnector connector)  {
		this.series = series;
		this.helperSeries = helperSeries;
		this.openedPositions = openedPositions;
		this.symbolResponse = symbolResponse;
		this.connector = connector;
		this.longTradingRecord = longTradingRecord;
		this.shortTradingRecord = shortTradingRecord;
		this.blackListOperation = bListOp;

		this.winningRatioMap = winningRatioMap;
		this.utilities = new RobotUtilities(symbolResponse, connector);
	}

	public boolean runRobotIteration() throws XTBCommunicationException, InterruptedException {
			String symbol = symbolResponse.getSymbol().getSymbol();
			int endIndex = series.getEndIndex();

			StrategyBuilder stratBuilder = new StrategyBuilder(endIndex, series, helperSeries);

			List<Strategy> longStrategies = stratBuilder.buildLongStrategies();
			List<Strategy> shortStrategies = stratBuilder.buildShortStrategies();

			if(!Configuration.runTest) {
				//check for long. take blacklist into consideration
				if(blackListOperation == null || blackListOperation.getTypeOfOperation() == TYPE_OF_OPERATION_SELL){
					if(checkForPositions(endIndex, symbol, longStrategies, TRADE_OPERATION_CODE.BUY)) {
						return true;
					}
				}
				//check for short.  take blacklist into consideration
				if(blackListOperation == null || blackListOperation.getTypeOfOperation() == TYPE_OF_OPERATION_BUY) {
					if(checkForPositions(endIndex, symbol, shortStrategies, TRADE_OPERATION_CODE.SELL)) {
						return true;
					}
				}
			}
			else {
				if(Configuration.runTestFX.equals(symbol)) {
					StrategyTester tester = new StrategyTester(series, helperSeries);
					tester.strategyTest(endIndex-Configuration.testedIndex, symbol);
				}
			}
			return false;
	}

	private boolean checkForPositions(int endIndex, String symbol, List<Strategy> baseStrategies,
			TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException, InterruptedException {
		TradingRecord tradingRecord = operationCode == TRADE_OPERATION_CODE.BUY ? longTradingRecord : shortTradingRecord;
		String strategyType = operationCode == TRADE_OPERATION_CODE.BUY ? "LONG" : "SHORT";
		int typeOfOperation = operationCode == TRADE_OPERATION_CODE.BUY ? 0 : 1;

		boolean shouldEnter = utilities.checkShouldEnter(endIndex, tradingRecord, baseStrategies, winningRatioMap, symbol);
		boolean shouldExit = utilities.checkShouldExit(endIndex, tradingRecord, baseStrategies);
		boolean isSymbolOpenedXTB = openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol));
		boolean openedSymbolOperationValid = openedPositions.stream().filter(e -> e.getSymbol().equals(symbol)).anyMatch(e -> e.getCmd() == typeOfOperation);

		if (!isSymbolOpenedXTB && shouldEnter) {
			BigDecimal stopLoss = utilities.calculateStopLoss(operationCode, series);
			BigDecimal takeProfit = utilities.calculateTakeProfit(operationCode, stopLoss);
			System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol
					+ ". Bar close price "+series.getLastBar().getClosePrice() + ". Stop Loss: "+stopLoss.doubleValue()+ " Take Profit: " + takeProfit.doubleValue());

			this.strategyStrength = StrategyBuilder.assessStrategyStrength(operationCode, series);
			System.out.println(new Date() + ": Strategy strength: "+strategyStrength);
			if(!Configuration.considerStratetyStrength)
				strategyStrength = 1.0;

			Double volume = utilities.getOptimalVolume(symbol);
			multiplyVolByStrategyStrength(volume);


			if(!utilities.volumeAndSlChecks(volume, stopLoss.doubleValue())) {
				return false;
			}


			boolean entered = tradingRecord.enter(endIndex, series.getLastBar().getClosePrice(),
					DoubleNum.valueOf(EXAMPLARY_VOLUME));
			if (entered) {
				enterXTB(operationCode, stopLoss, takeProfit, volume);
				System.out.println(new Date() + ": Opened in XTB successfully");
				return true;
			} else {
				System.out.println(new Date() + ": Didn't enter "+strategyType+" position for: " + symbol);
			}
		}
		if(isSymbolOpenedXTB && openedSymbolOperationValid) {
			updateStopLossXTB(operationCode);
			if (shouldExit) {
				System.out.println(new Date() + ": "+strategyType + " strategy should EXIT on " + symbol + ". Exit strategy valid:" + shouldExit);
				boolean exited = tradingRecord.exit(endIndex, series.getLastBar().getClosePrice(),
						DoubleNum.valueOf(EXAMPLARY_VOLUME));
				if (exited) {
					exitXTB(operationCode);
					System.out.println(new Date() + ": Closed in XTB successfully");
					return true;
				} else {
					System.out.println(new Date() + ": Didn't exit "+strategyType+" position for: " + symbol);
				}
			}
		}

		return false;
	}



	private void enterXTB(TRADE_OPERATION_CODE operationCode, BigDecimal stopLoss, BigDecimal takeProfit, double volume) throws XTBCommunicationException {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getBid(), stopLoss.doubleValue() , takeProfit.doubleValue() , symbolRecord.getSymbol(), volume, 0L, "" , 0L);

		try {
			APICommandFactory.executeTradeTransactionCommand(connector,ttInfoRecord);
			Thread.sleep(250);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse | InterruptedException e1) {
			System.out.println(new Date() + ": Failed to open in XTB" + symbolRecord.getSymbol());
			throw new XTBCommunicationException("Couldn't open the position in XTB due to communication problems: "+symbolRecord.getSymbol());
		}
	}

	private void exitXTB(TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);
		try {
			if(tr==null) return;
			TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
					TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());
			APICommandFactory.executeTradeTransactionCommand(connector, ttInfoRecord);
			Thread.sleep(250);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse | InterruptedException e1) {
			System.out.println(new Date() + ": Failed to close in XTB" + tr.getSymbol());
			throw new XTBCommunicationException("Couldn't close the position in XTB due to communication problems: "+tr.getSymbol());
		}

	}

	private void updateStopLossXTB(TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		if(!Configuration.updateStopLoss) {
			return;
		}
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);

		if (tr == null || (tr.getCmd() == TYPE_OF_OPERATION_SELL && operationCode == TRADE_OPERATION_CODE.BUY)
				|| tr.getCmd() == TYPE_OF_OPERATION_BUY && operationCode == TRADE_OPERATION_CODE.SELL)
			return;

		//checks if profit is in proportion 1:1 with stop loss. If yes, setting sl to open price value to avoid risk
		if(!utilities.shouldUpdateStopLoss(BigDecimal.valueOf(tr.getSl()), BigDecimal.valueOf(tr.getTp()), operationCode))
			return;

		double stopLoss = tr.getOpen_price();

		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
				TRADE_TRANSACTION_TYPE.MODIFY, tr.getClose_price(), stopLoss, tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());
		try {
			APICommandFactory.executeTradeTransactionCommand(connector, ttInfoRecord);
			Thread.sleep(250);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse | InterruptedException e1) {
			throw new XTBCommunicationException("Couldn't update the stopLoss in XTB due to communication problems: "+tr.getSymbol());
		}
	}

	private void multiplyVolByStrategyStrength(Double volume) {
		volume = BigDecimal.valueOf(volume).multiply(BigDecimal.valueOf(strategyStrength)).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
	}
}
