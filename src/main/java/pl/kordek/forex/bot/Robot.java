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

	private BaseBarSeries series = null;
	private BaseBarSeries parentSeries = null;
	private TradingRecord longTradingRecord = null;
	private TradingRecord shortTradingRecord = null;
	private SymbolResponse symbolResponse = null;

	//if null then the operation is not blacklisted
	private BlackListOperation blackListOperation = null;

	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	private List<TradeRecord> openedPositions = null;

	private SyncAPIConnector connector = null;

	private static double volume = 0.05;


	public Robot(BaseBarSeries series, BaseBarSeries parentSeries, TradingRecord longTradingRecord, TradingRecord shortTradingRecord, BlackListOperation bListOp,
			List<TradeRecord> openedPositions, SymbolResponse symbolResponse, SyncAPIConnector connector) {
		this.series = series;
		this.parentSeries = parentSeries;
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

			Strategy longStrategy = StrategyBuilder.buildLongStrategy(endIndex, series, parentSeries);
			Strategy shortStrategy = StrategyBuilder.buildShortStrategy(endIndex, series, parentSeries);

			//check for long. take blacklist into consideration
			if(blackListOperation == null || blackListOperation.getTypeOfOperation() == TYPE_OF_OPERATION_SELL){
				checkForPositions(endIndex, symbol, longStrategy, shortStrategy, TRADE_OPERATION_CODE.BUY);
			}


			//check for short.  take blacklist into consideration
			if(blackListOperation == null || blackListOperation.getTypeOfOperation() == TYPE_OF_OPERATION_BUY) {
				checkForPositions(endIndex, symbol, shortStrategy, longStrategy, TRADE_OPERATION_CODE.SELL);
			}

//			StrategyTester tester = new StrategyTester(series, parentSeries);
//			tester.strategyTest(endIndex-1, symbol);

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
			if(!isEnoughMargin()) {
				System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol + " but not enough margin");
				return false;
			}
			System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol);
			boolean entered = tradingRecord.enter(endIndex, series.getLastBar().getClosePrice(),
					DoubleNum.valueOf(volume));
			if (entered) {
				openPosition(symbol, operationCode);
				Thread.sleep(250);
				return true;
			} else {
				System.out.println(new Date() + ": Didn't enter "+strategyType+" position for: " + symbol);
			}
		}
		if(isSymbolOpenedXTB && openedSymbolOperationValid) {
			updateStopLoss(operationCode);
			Thread.sleep(250);
			if (shouldExit/* || shouldOppositeEnter*/) {
				System.out.println(new Date() + ": "+strategyType + " strategy should EXIT on " + symbol + ". Exit strategy valid:" + shouldExit
						+ ". Should opposite enter:" + shouldOppositeEnter);
				boolean exited = tradingRecord.exit(endIndex, series.getLastBar().getClosePrice(),
						DoubleNum.valueOf(volume));
				if (exited) {
					closePosition(symbol, operationCode);
					Thread.sleep(250);
					return true;
				} else {
					System.out.println(new Date() + ": Didn't exit "+strategyType+" position for: " + symbol);
				}
			}
		}

		return false;
	}

	private void openPosition(String symbol, TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		// Our strategy should enter

		try {
			enterXTB(operationCode);
			System.out.println(new Date() + ": Opened in XTB successfully");
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e1) {
			System.out.println(new Date() + ": Failed to open in XTB" + symbol);
			throw new XTBCommunicationException("Couldn't open the position in XTB due to communication problems: "+symbol);
		}
	}

	private void closePosition(String symbol, TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		try {
			exitXTB(operationCode);
			System.out.println(new Date() + ": Closed in XTB successfully");
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e1) {
			System.out.println(new Date() + ": Failed to close in XTB" + symbol);
			throw new XTBCommunicationException("Couldn't close the position in XTB due to communication problems: "+symbol);
		}
	}

	private void enterXTB(TRADE_OPERATION_CODE operationCode) throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		OrderType orderType = operationCode == TRADE_OPERATION_CODE.BUY ? OrderType.BUY : OrderType.SELL;

		BigDecimal stopLossBD = calculateStopLoss(orderType);


		double stopLoss = 0.0;
		double takeProfit = 0.0;

		//if can't calculate stop loss by finding the lowest/highest value then set stop loss by price percentage
		if(stopLossBD.doubleValue() == 0.0) {
			stopLoss = calculateStopLossPrc(orderType, Configuration.stopLossPrc).doubleValue();
			takeProfit = calculateTakeProfitPrc(orderType, Configuration.takeProfitPrc).doubleValue();
		}
		else {
			stopLoss = stopLossBD.doubleValue();
			takeProfit = calculateTakeProfit(orderType, stopLossBD).doubleValue();
		}

		//set take profit to 0 for now
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getBid(), stopLoss , 0.0 , symbolRecord.getSymbol(), volume, 0L, "" , 0L);

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory.executeTradeTransactionCommand(connector,
				ttInfoRecord);

	}

	private void exitXTB(TRADE_OPERATION_CODE operationCode) throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);
		if(tr==null) return;

		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
				TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory
				.executeTradeTransactionCommand(connector, ttInfoRecord);

	}

	private void updateStopLoss(TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		try {
			updateStopLossXTB(operationCode);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e) {
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
		double stopLoss = calculateStopLoss(orderType).doubleValue();

		//stop loss is 0.0 when the newest candle is the lowest/highest
		if(stopLoss == 0.0) return;

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

	private Double getMarginNeeded() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		MarginTradeResponse marginTradeResponse;
		marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, symbolResponse.getSymbol().getSymbol(), volume);
        return marginTradeResponse.getMargin();
	}

	private boolean isEnoughMargin() throws XTBCommunicationException {
		Double marginFree = 0.0;
		Double marginNeeded = 0.0;
		try {
			marginFree = getMarginFree();
			marginNeeded = getMarginNeeded();
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e) {
			throw new XTBCommunicationException("Couldn't execute is enough margin check");
		}

		return marginFree > marginNeeded;
	}

	private BigDecimal calculateStopLossPrc(OrderType orderType, Double percentage){
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
        BigDecimal stopLossRatioPrc = orderType == OrderType.BUY ? BigDecimal.valueOf(100L).subtract(BigDecimal.valueOf(percentage)) : BigDecimal.valueOf(100L).add(BigDecimal.valueOf(percentage));
		BigDecimal stopLossRatio = stopLossRatioPrc.divide(BigDecimal.valueOf(100L));
        Double price = orderType == OrderType.BUY ? symbolResponse.getSymbol().getBid() : symbolResponse.getSymbol().getAsk();
        BigDecimal priceBigDecimal = BigDecimal.valueOf(price);
        BigDecimal stopLossPrice = priceBigDecimal.multiply(stopLossRatio).setScale(precisionNumber, RoundingMode.HALF_UP);

        return stopLossPrice;
    }

	private BigDecimal calculateStopLoss(OrderType orderType){
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
        double stopLossWithoutPrecision = StrategyBuilder.getNewStopLoss(series, orderType);
        BigDecimal stopLoss = BigDecimal.valueOf(stopLossWithoutPrecision).scaleByPowerOfTen(-precisionNumber);
        return stopLoss;
    }


	private BigDecimal calculateTakeProfitPrc(OrderType orderType, Double percentage){
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
        BigDecimal takeProfitRatioPrc = orderType == OrderType.BUY ? BigDecimal.valueOf(100L).add(BigDecimal.valueOf(percentage)) : BigDecimal.valueOf(100L).subtract(BigDecimal.valueOf(percentage));
		BigDecimal takeProfitRatio = takeProfitRatioPrc.divide(BigDecimal.valueOf(100L));
        Double price = orderType == OrderType.BUY ? symbolResponse.getSymbol().getAsk() : symbolResponse.getSymbol().getBid();
        BigDecimal priceBigDecimal = BigDecimal.valueOf(price);
        BigDecimal takeProfitPrice = priceBigDecimal.multiply(takeProfitRatio).setScale(precisionNumber, RoundingMode.HALF_UP);

        return takeProfitPrice;
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


	public static void setVolume(Double vol) {
		volume = vol;
	}

}
