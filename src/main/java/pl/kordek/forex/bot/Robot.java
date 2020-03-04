package pl.kordek.forex.bot;

import java.util.Date;
import java.util.List;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

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
			
	//		strategyTest(endIndex,symbol);
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
			if(!isEnoughMargin(symbol)) {
				System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol + " but not enough margin");
				return false;
			}
			System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol);
			boolean entered = tradingRecord.enter(endIndex, series.getLastBar().getClosePrice(),
					DoubleNum.valueOf(Configuration.volume));
			if (entered) {
				openPosition(symbol, orderType);
				return true;
			} else {
				System.out.println(new Date() + ": Didn't enter "+strategyType+" position for: " + symbol);
			}
		} else if ((shouldExit || shouldOppositeEnter) && isSymbolOpenedXTB) {
			System.out.println(new Date() + ": "+strategyType + " strategy should EXIT on " + symbol + ". Should this exit:" + shouldExit
					+ ". Should opposite enter:" + shouldOppositeEnter);
			boolean exited = tradingRecord.exit(endIndex, series.getLastBar().getClosePrice(),
					DoubleNum.valueOf(Configuration.volume));
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
			System.out.println(new Date() + ": Failed to open " + symbol);
			throw new XTBCommunicationException("Couldn't open the position in XTB: "+symbol);
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
			throw new XTBCommunicationException("Couldn't close the position in XTB: "+symbol);
		}
	}

	private void enterBuyXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		//Double stopLoss = calculateStopLossInPips(sr).doubleValue();	
		//Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.BUY,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getAsk(), 0.0, 0.0, symbolRecord.getSymbol(), Configuration.volume, 0L, "" , 0L);
		

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory.executeTradeTransactionCommand(connector,
				ttInfoRecord);
		
	}

	private void exitBuyXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.BUY,
				TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory
				.executeTradeTransactionCommand(connector, ttInfoRecord);

	}
	
	private void enterSellXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		//Double stopLoss = calculateStopLossInPips(sr).doubleValue();	
		
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.SELL,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getBid(), 0.0, 0.0, symbolRecord.getSymbol(), Configuration.volume, 0L, "" , 0L);
		

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
	
	private Double getMarginNeeded(String symbol) throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		MarginTradeResponse marginTradeResponse;
		marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, symbol,Configuration.volume);
        return marginTradeResponse.getMargin();
	}
	
	private boolean isEnoughMargin(String symbol) throws XTBCommunicationException {
		Double marginFree = 0.0;
		Double marginNeeded = 0.0;
		try {
			marginFree = getMarginFree();
			marginNeeded = getMarginNeeded(symbol);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e) {
			throw new XTBCommunicationException("Couldn't execute is enough margin check");
		}
		
		return marginFree > marginNeeded;
	}


	private void strategyTest(int index, String symbol) {
		
		Strategy longStrategy = StrategyBuilder.buildLongStrategy(index, series);
		Strategy shortStrategy = StrategyBuilder.buildShortStrategy(index, series);
		
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		System.out.println("Should enter for: "+ symbol+ " index:"+index+ " price:"+closePrice.getValue(index)+ " = "+ longStrategy.shouldEnter(index));
		
		
		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);
		
		Rule priceUnderCloud = new UnderIndicatorRule(closePrice, senkouSpanA)
				.and(new UnderIndicatorRule(closePrice, senkouSpanB));
		Rule priceCrossesKijunDownRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
		Rule chikouUnderPrice = new UnderIndicatorRule(new ConstantIndicator(series, chikouSpan.getValue(index-26)), closePrice.getValue(index-26));
		Rule chikouOverPrice = new OverIndicatorRule(new ConstantIndicator(series, chikouSpan.getValue(index-26)), closePrice.getValue(index-26));
		Rule signalA = priceCrossesKijunDownRule;
		Rule signalB = new CrossedDownIndicatorRule(closePrice, senkouSpanB);

		System.out.println("price under cloud satisfied: "+priceUnderCloud.isSatisfied(index));
		System.out.println("price crosses down kijun: "+priceCrossesKijunDownRule.isSatisfied(index));
		System.out.println("chikou under price: "+chikouUnderPrice.isSatisfied(index));
		System.out.println("chikou over price: "+chikouOverPrice.isSatisfied(index));
		System.out.println("price crosses down span b: "+new CrossedDownIndicatorRule(closePrice, senkouSpanB).isSatisfied(index));
	}
	
	private void strategyTest2(int index) {
		TradingRecord testTradingRecord = new BaseTradingRecord();
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));
		
		boolean entered = testTradingRecord.enter(index, series.getBar(index).getClosePrice(), DoubleNum.valueOf(Configuration.volume));
		
		System.out.println("stop loss satisfied: "+signalOut.isSatisfied(series.getEndIndex(), testTradingRecord));

	}
	
	
	

}
