package pl.kordek.forex.bot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Order;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.DoubleNum;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.BlackListOperation;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.strategy.StopLossHelper;
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
import pro.xstore.api.message.response.TradeTransactionResponse;
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

	//if null then the operation is not blacklisted
	private BlackListOperation blackListOperation = null;

	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	private List<TradeRecord> openedPositions = null;

	private SyncAPIConnector connector = null;



	public Robot(BaseBarSeries series, BaseBarSeries helperSeries, TradingRecord longTradingRecord, TradingRecord shortTradingRecord, BlackListOperation bListOp,
			List<TradeRecord> openedPositions, SymbolResponse symbolResponse, SyncAPIConnector connector) {
		this.series = series;
		this.helperSeries = helperSeries;
		this.openedPositions = openedPositions;
		this.symbolResponse = symbolResponse;
		this.connector = connector;
		this.longTradingRecord = longTradingRecord;
		this.shortTradingRecord = shortTradingRecord;
		this.blackListOperation = bListOp;
	}

	public void runRobotIteration() throws XTBCommunicationException, InterruptedException {
			String symbol = symbolResponse.getSymbol().getSymbol();

			int endIndex = series.getEndIndex();

			Strategy longStrategy = StrategyBuilder.buildLongStrategy(endIndex, series, helperSeries);
			Strategy shortStrategy = StrategyBuilder.buildShortStrategy(endIndex, series, helperSeries);

			if(!Configuration.runTest) {
				//check for long. take blacklist into consideration
				if(blackListOperation == null || blackListOperation.getTypeOfOperation() == TYPE_OF_OPERATION_SELL){
					checkForPositions(endIndex, symbol, longStrategy, shortStrategy, TRADE_OPERATION_CODE.BUY);
				}
				//check for short.  take blacklist into consideration
				if(blackListOperation == null || blackListOperation.getTypeOfOperation() == TYPE_OF_OPERATION_BUY) {
					checkForPositions(endIndex, symbol, shortStrategy, longStrategy, TRADE_OPERATION_CODE.SELL);
				}
			}
			else {
				if(Configuration.runTestFX.equals(symbol)) {
					StrategyTester tester = new StrategyTester(series, helperSeries);
					tester.strategyTest(endIndex-Configuration.testedIndex, symbol);
				}
			}
	}

	private boolean checkForPositions(int endIndex, String symbol, Strategy baseStrategy, Strategy oppositeStrategy,
			TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException, InterruptedException {
		TradingRecord tradingRecord = operationCode == TRADE_OPERATION_CODE.BUY ? longTradingRecord : shortTradingRecord;
		String strategyType = operationCode == TRADE_OPERATION_CODE.BUY ? "LONG" : "SHORT";
		int typeOfOperation = operationCode == TRADE_OPERATION_CODE.BUY ? 0 : 1;

		boolean shouldEnter = baseStrategy.shouldEnter(endIndex, tradingRecord);
		boolean shouldExit = baseStrategy.shouldExit(endIndex, tradingRecord);
		boolean shouldOppositeEnter = oppositeStrategy.shouldEnter(endIndex);
		boolean isSymbolOpenedXTB = openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol));
		boolean openedSymbolOperationValid = openedPositions.stream().filter(e -> e.getSymbol().equals(symbol)).anyMatch(e -> e.getCmd() == typeOfOperation);

		if (!isSymbolOpenedXTB && shouldEnter) {

			System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol + ". Bar close price "+series.getLastBar().getClosePrice());
			boolean entered = tradingRecord.enter(endIndex, series.getLastBar().getClosePrice(),
					DoubleNum.valueOf(EXAMPLARY_VOLUME));
			if (entered) {
				enterXTB(operationCode);
				System.out.println(new Date() + ": Opened in XTB successfully");
				return true;
			} else {
				System.out.println(new Date() + ": Didn't enter "+strategyType+" position for: " + symbol);
			}
		}
		if(isSymbolOpenedXTB && openedSymbolOperationValid) {
			if(Configuration.updateStopLoss)
				updateStopLoss(operationCode);
			if (shouldExit) {
				System.out.println(new Date() + ": "+strategyType + " strategy should EXIT on " + symbol + ". Exit strategy valid:" + shouldExit
						+ ". Should opposite enter:" + shouldOppositeEnter);
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


	private void enterXTB(TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		OrderType orderType = operationCode == TRADE_OPERATION_CODE.BUY ? OrderType.BUY : OrderType.SELL;
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		double optimalVolume = getOptimalVolume(symbolRecord.getSymbol());
		double strategyStrength = StrategyBuilder.assessStrategyStrength(orderType, series);
		System.out.println(new Date() + ": Strategy strength: "+strategyStrength);
		//strategyStrength = 1.0;
		double smartVolume = BigDecimal.valueOf(optimalVolume).multiply(BigDecimal.valueOf(strategyStrength)).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
		if(optimalVolume < 0.01) {
			System.out.println(new Date() + ": Calculated volume is below 0.01 for symbol: "+symbolRecord.getSymbol()+". Skipping the trade");
			return;
		}
		if(smartVolume < 0.01) {
			smartVolume = 0.01;
		}
		if(!isEnoughMargin(smartVolume)) {
			System.out.println(new Date() + ": "+orderType + " strategy should ENTER on " + symbolRecord.getSymbol() + " but not enough margin");
			return;
		}
		try {
			BigDecimal stopLoss = calculateStopLoss(orderType, series);

			BigDecimal takeProfit = calculateTakeProfit(orderType, stopLoss);

			if(stopLoss.doubleValue() == 0.0) {
				System.out.println(new Date() + ": Couldnt calculate the stoploss that would be below the max allowed percentage: "+Configuration.stopLossPrc+ ". Skipping trade");
				return;
			}
			TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
					TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getBid(), stopLoss.doubleValue() , takeProfit.doubleValue() , symbolRecord.getSymbol(), smartVolume, 0L, "" , 0L);

			TradeTransactionResponse tradeTransactionResponse = APICommandFactory.executeTradeTransactionCommand(connector,
					ttInfoRecord);
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
			TradeTransactionResponse tradeTransactionResponse = APICommandFactory
					.executeTradeTransactionCommand(connector, ttInfoRecord);
			Thread.sleep(250);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse | InterruptedException e1) {
			System.out.println(new Date() + ": Failed to close in XTB" + tr.getSymbol());
			throw new XTBCommunicationException("Couldn't close the position in XTB due to communication problems: "+tr.getSymbol());
		}

	}

	private void updateStopLoss(TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		try {
			updateStopLossXTB(operationCode);
			Thread.sleep(250);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse | InterruptedException e) {
			throw new XTBCommunicationException("Couldn't execute stop loss update");
		}
	}

	private void updateStopLossXTB(TRADE_OPERATION_CODE operationCode) throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);
		if (tr == null || (tr.getCmd() == TYPE_OF_OPERATION_SELL && operationCode == TRADE_OPERATION_CODE.BUY)
				|| tr.getCmd() == TYPE_OF_OPERATION_BUY && operationCode == TRADE_OPERATION_CODE.SELL)
			return;

		OrderType orderType = operationCode == TRADE_OPERATION_CODE.BUY ? OrderType.BUY : OrderType.SELL;

		BigDecimal stopLossBD = calculateStopLoss(orderType, series);

		double stopLoss = stopLossBD.doubleValue();



		//stop loss is 0.0 when the newest candle is the lowest/highest. Don't update if the calculation is the same as the current sl
		if(stopLoss == 0.0 || tr.getSl().equals(stopLoss)) return;

		double takeProfit = calculateTakeProfit(orderType, stopLossBD).doubleValue();

		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
				TRADE_TRANSACTION_TYPE.MODIFY, tr.getClose_price(), stopLoss, tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory
				.executeTradeTransactionCommand(connector, ttInfoRecord);

	}

	private Double getMarginFree() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		MarginLevelResponse marginLevelResponse;
        marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
        return marginLevelResponse.getMargin_free();
	}

	private Double getMarginNeeded(Double volume) throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		MarginTradeResponse marginTradeResponse;
		marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, symbolResponse.getSymbol().getSymbol(), volume);
        return marginTradeResponse.getMargin();
	}

	private boolean isEnoughMargin(Double volume) throws XTBCommunicationException {
		Double marginFree = 0.0;
		Double marginNeeded = 0.0;
		try {
			marginFree = getMarginFree();
			marginNeeded = getMarginNeeded(volume);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e) {
			throw new XTBCommunicationException("Couldn't execute is enough margin check");
		}

		return marginFree > marginNeeded;
	}



	private BigDecimal calculateStopLoss(OrderType orderType, BaseBarSeries series){
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
        BigDecimal stopLoss = StopLossHelper.getNewStopLoss(series, orderType, Configuration.stopLossBarCount).scaleByPowerOfTen(-precisionNumber);
        return stopLoss;
    }

	private BigDecimal calculateTrailingStopLoss(TradeRecord tr, BaseBarSeries series){
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
		BigDecimal closePrice = BigDecimal.valueOf(series.getLastBar().getClosePrice().doubleValue()).scaleByPowerOfTen(-precisionNumber);
		BigDecimal trail = BigDecimal.valueOf(tr.getSl()).subtract(new BigDecimal(tr.getClose_price()));

        return BigDecimal.valueOf(StopLossHelper.getNewTrailingStopLoss(closePrice, trail, new BigDecimal(tr.getSl())));
    }


	private BigDecimal calculateTakeProfit(OrderType orderType, BigDecimal stopLoss){
		BigDecimal takeProfitVsStopLossCoeffBD = BigDecimal.valueOf(Configuration.takeProfitVsStopLossCoeff);
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
		BigDecimal sellTP = BigDecimal.valueOf(symbolResponse.getSymbol().getBid())
				.subtract(stopLoss.subtract(BigDecimal.valueOf(symbolResponse.getSymbol().getBid())).multiply(takeProfitVsStopLossCoeffBD))
				.setScale(precisionNumber, RoundingMode.HALF_UP);
        BigDecimal buyTP = BigDecimal.valueOf(symbolResponse.getSymbol().getAsk())
        		.add(BigDecimal.valueOf(symbolResponse.getSymbol().getAsk()).subtract(stopLoss).multiply(takeProfitVsStopLossCoeffBD))
        		.setScale(precisionNumber, RoundingMode.HALF_UP);
		return orderType == OrderType.BUY ? buyTP : sellTP;
    }


	private double getOptimalVolume(String symbol) throws XTBCommunicationException {
		MarginLevelResponse marginLevelResponse;
        try {
			marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
			BigDecimal balance = BigDecimal.valueOf(marginLevelResponse.getBalance());
			BigDecimal balancePerTrade = balance.divide(BigDecimal.valueOf(5L), 2, RoundingMode.HALF_UP);

			BigDecimal optimalVolume = BigDecimal.valueOf(1);

			MarginTradeResponse marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, symbol, optimalVolume.doubleValue());
			BigDecimal marginRatio = balancePerTrade.divide(BigDecimal.valueOf(marginTradeResponse.getMargin()) , 2, RoundingMode.HALF_UP);

			optimalVolume = optimalVolume.multiply(marginRatio).setScale(2, RoundingMode.HALF_UP);
			Thread.sleep(250);
			return optimalVolume.doubleValue();
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse | InterruptedException e) {
			throw new XTBCommunicationException("Couldn't get optimal volume");
		}

	}

}
