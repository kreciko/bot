package pl.kordek.forex.bot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.DoubleNum;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
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

	private BaseBarSeries series = null;
	private TradingRecord longTradingRecord = null;
	private TradingRecord shortTradingRecord = null;
	private SymbolResponse symbolResponse = null;
	
	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	private List<TradeRecord> openedPositions;
	
	private SyncAPIConnector connector = null;
	
	private static double volume = 0.05;
	

	public Robot(BaseBarSeries series, TradingRecord longTradingRecord, TradingRecord shortTradingRecord,
			List<TradeRecord> openedPositions, SymbolResponse symbolResponse, SyncAPIConnector connector) {
		this.series = series;
		this.openedPositions = openedPositions;
		this.symbolResponse = symbolResponse;
		this.connector = connector;
		this.longTradingRecord = longTradingRecord;
		this.shortTradingRecord = shortTradingRecord;
	}

	public void runRobotIteration() throws XTBCommunicationException {
			String symbol = symbolResponse.getSymbol().getSymbol();
			
			int endIndex = series.getEndIndex();
			
			Strategy longStrategy = StrategyBuilder.buildLongStrategy(endIndex, series);
			Strategy shortStrategy = StrategyBuilder.buildShortStrategy(endIndex, series);
			
			//check for long
			checkForPositions(endIndex, symbol, longStrategy, shortStrategy, OrderType.BUY);
			
			//check for short
			checkForPositions(endIndex, symbol, shortStrategy, longStrategy, OrderType.SELL);
			
//			StrategyTester tester = new StrategyTester(series);
//			tester.strategyTest2(OrderType.SELL, endIndex-3);


	}
	
	private boolean checkForPositions(int endIndex, String symbol, Strategy baseStrategy, Strategy oppositeStrategy,
			OrderType orderType) throws XTBCommunicationException {
		TradingRecord tradingRecord = orderType == OrderType.BUY ? longTradingRecord : shortTradingRecord;
		String strategyType = orderType == OrderType.BUY ? "LONG" : "SHORT";
		boolean shouldEnter = baseStrategy.shouldEnter(endIndex, tradingRecord);
		boolean shouldExit = baseStrategy.shouldExit(endIndex, tradingRecord);
		boolean shouldOppositeEnter = oppositeStrategy.shouldEnter(endIndex);
		boolean isSymbolOpenedXTB = openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol)); 

		if (shouldEnter && !isSymbolOpenedXTB) {
			if(!isEnoughMargin()) {
				System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol + " but not enough margin");
				return false;
			}
			System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol);
			boolean entered = tradingRecord.enter(endIndex, series.getLastBar().getClosePrice(),
					DoubleNum.valueOf(volume));
			if (entered) {
				openPosition(symbol, orderType);
				return true;
			} else {
				System.out.println(new Date() + ": Didn't enter "+strategyType+" position for: " + symbol);
			}
		} else if ((shouldExit || shouldOppositeEnter) && isSymbolOpenedXTB) {
			System.out.println(new Date() + ": "+strategyType + " strategy should EXIT on " + symbol + ". Exit strategy valid:" + shouldExit
					+ ". Should opposite enter:" + shouldOppositeEnter);
			boolean exited = tradingRecord.exit(endIndex, series.getLastBar().getClosePrice(),
					DoubleNum.valueOf(volume));
			if (exited) {
				closePosition(symbol, orderType);
				return true;
			} else {
				System.out.println(new Date() + ": Didn't exit "+strategyType+" position for: " + symbol);
			}
		}
		return false;
	}
	
	private void openPosition(String symbol, OrderType orderType) throws XTBCommunicationException {
		// Our strategy should enter

		try {
			if(orderType.equals(OrderType.BUY)) enterBuyXTB();
			else enterSellXTB();
			System.out.println(new Date() + ": Opened in XTB successfully");
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e1) {
			System.out.println(new Date() + ": Failed to open in XTB" + symbol);
			throw new XTBCommunicationException("Couldn't open the position in XTB due to communication problems: "+symbol);
		}
	}
	
	private void closePosition(String symbol, OrderType orderType) throws XTBCommunicationException {
		try {
			if(orderType.equals(OrderType.BUY)) exitBuyXTB();
			else exitSellXTB();
			System.out.println(new Date() + ": Closed in XTB successfully");
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e1) {
			System.out.println(new Date() + ": Failed to close in XTB" + symbol);
			throw new XTBCommunicationException("Couldn't close the position in XTB due to communication problems: "+symbol);
		}
	}

	private void enterBuyXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		Double stopLoss = calculateStopLoss(OrderType.BUY, Configuration.stopLossPrc).doubleValue();	
		Double takeProfit = calculateTakeProfit(OrderType.BUY, Configuration.takeProfitPrc).doubleValue();
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.BUY,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getAsk(), stopLoss, takeProfit, symbolRecord.getSymbol(), volume, 0L, "" , 0L);
		

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory.executeTradeTransactionCommand(connector,
				ttInfoRecord);
		
	}

	private void exitBuyXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);
		if(tr==null) return;
		
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.BUY,
				TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory
				.executeTradeTransactionCommand(connector, ttInfoRecord);

	}
	
	private void enterSellXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		Double stopLoss = calculateStopLoss(OrderType.SELL, Configuration.stopLossPrc).doubleValue();
		Double takeProfit = calculateTakeProfit(OrderType.SELL, Configuration.takeProfitPrc).doubleValue();
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.SELL,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getBid(), stopLoss, takeProfit , symbolRecord.getSymbol(), volume, 0L, "" , 0L);
		

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory.executeTradeTransactionCommand(connector,
				ttInfoRecord);
		
	}

	private void exitSellXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);	
		if(tr==null) return;
		
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.SELL,
				TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());

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

	private BigDecimal calculateStopLoss(OrderType orderType, Double percentage){
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
        BigDecimal stopLossRatioPrc = orderType == OrderType.BUY ? BigDecimal.valueOf(100L).subtract(BigDecimal.valueOf(percentage)) : BigDecimal.valueOf(100L).add(BigDecimal.valueOf(percentage));		
		BigDecimal stopLossRatio = stopLossRatioPrc.divide(BigDecimal.valueOf(100L));
        Double price = orderType == OrderType.BUY ? symbolResponse.getSymbol().getBid() : symbolResponse.getSymbol().getAsk();
        BigDecimal priceBigDecimal = BigDecimal.valueOf(price);
        BigDecimal stopLossPrice = priceBigDecimal.multiply(stopLossRatio).setScale(precisionNumber, RoundingMode.HALF_UP);
        
        return stopLossPrice;
    }
	
	private BigDecimal calculateTakeProfit(OrderType orderType, Double percentage){
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
        BigDecimal takeProfitRatioPrc = orderType == OrderType.BUY ? BigDecimal.valueOf(100L).add(BigDecimal.valueOf(percentage)) : BigDecimal.valueOf(100L).subtract(BigDecimal.valueOf(percentage));		
		BigDecimal takeProfitRatio = takeProfitRatioPrc.divide(BigDecimal.valueOf(100L));
        Double price = orderType == OrderType.BUY ? symbolResponse.getSymbol().getAsk() : symbolResponse.getSymbol().getBid();
        BigDecimal priceBigDecimal = BigDecimal.valueOf(price);
        BigDecimal takeProfitPrice = priceBigDecimal.multiply(takeProfitRatio).setScale(precisionNumber, RoundingMode.HALF_UP);
        
        return takeProfitPrice;
    }
	
	public static void setVolume(Double vol) {
		volume = vol;
	}

}
