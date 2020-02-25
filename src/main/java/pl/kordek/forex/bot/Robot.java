package pl.kordek.forex.bot;

import java.util.List;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import pl.kordek.forex.bot.constants.Configuration;
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

	public void runRobotIteration() {
			String symbol = symbolResponse.getSymbol().getSymbol();
			
			Strategy longStrategy = buildLongStrategy();
			Strategy shortStrategy = buildShortStrategy();

			int endIndex = series.getEndIndex();
			checkForLongPositions(endIndex, symbol, longStrategy);
			checkForShortPositions(endIndex, symbol, shortStrategy);
		
	}
	
	private void checkForLongPositions(int endIndex, String symbol, Strategy longStrategy) {

		//if the short trades are not closed it can result in premature buy of the opened position
		if (shortTradingRecord.getLastOrder() == null || !shortTradingRecord.getLastOrder().isSell()) {
			if (longStrategy.shouldEnter(endIndex, longTradingRecord) ) {
				System.out.println("Long strategy should ENTER on " + symbol);
				boolean entered = longTradingRecord.enter(endIndex, series.getLastBar().getClosePrice(), DoubleNum.valueOf(Configuration.volume));
                if (entered) {
                	openPosition(symbol, OrderType.BUY);
                }
			} else if (longStrategy.shouldExit(endIndex, longTradingRecord)) {
				System.out.println("Long strategy should EXIT on " + symbol);
				boolean exited = longTradingRecord.exit(endIndex, series.getLastBar().getClosePrice(), DoubleNum.valueOf(Configuration.volume));
                if (exited) {
                	closePosition(symbol, OrderType.BUY);
                }
			}
		}
	}
	
	private void checkForShortPositions(int endIndex, String symbol, Strategy shortStrategy) {
		
		//if the long trades are not closed it can result in premature sell of the opened position
		if (longTradingRecord.getLastOrder() == null || !longTradingRecord.getLastOrder().isBuy()) {
			if (shortStrategy.shouldEnter(endIndex, shortTradingRecord)) {
				System.out.println("Short strategy should ENTER on " + symbol);
				boolean entered = shortTradingRecord.enter(endIndex, series.getLastBar().getClosePrice(), DoubleNum.valueOf(Configuration.volume));
                if (entered) {
                	openPosition(symbol, OrderType.SELL);
                }
			} else if (shortStrategy.shouldExit(endIndex, shortTradingRecord)) {
				System.out.println("Short strategy should EXIT on " + symbol);
				boolean exited = shortTradingRecord.exit(endIndex, series.getLastBar().getClosePrice(), DoubleNum.valueOf(Configuration.volume));
                if (exited) {
                	closePosition(symbol, OrderType.SELL);
                }
			}
		}
	}
	
	private void openPosition(String symbol, OrderType orderType) {
		// Our strategy should enter
		if (openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol))) {
			System.out.println("Failed to open "+ orderType + " for" + symbol + ". Symbol already in the XTB trading list");
			return;
		}

		try {
			if(orderType.equals(OrderType.BUY)) enterBuyXTB();
			else enterSellXTB();
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e1) {
			System.out.println("Failed to open " + symbol);
		}
	}
	
	private void closePosition(String symbol, OrderType orderType) {
		// Our strategy should exit
		if (!openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol))) {
			System.out.println("Failed to close " + orderType + " for" + symbol + ". Symbol not in the XTB trading list");
			return;
		}
		try {
			if(orderType.equals(OrderType.BUY)) exitBuyXTB();
			else exitSellXTB();
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e1) {
			System.out.println("Failed to close " + symbol);
		}
	}

	private Strategy buildLongStrategy() {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);

		Rule priceCrossesKijunUpRule = new CrossedUpIndicatorRule(closePrice, kijunSen);
		Rule priceOverCloud = new OverIndicatorRule(closePrice, senkouSpanA)
				.and(new OverIndicatorRule(closePrice, senkouSpanB));
		Rule chikouOverPrice = new OverIndicatorRule(chikouSpan, closePrice);

		Rule signalA = priceCrossesKijunUpRule.and(priceOverCloud);
		Rule signalB = new CrossedUpIndicatorRule(closePrice, senkouSpanA).and(priceOverCloud);

		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));
		
		Rule entryRule = chikouOverPrice.and(signalA.or(signalB));
		Rule exitRule = signalOut;
		return new BaseStrategy(entryRule, exitRule);
	}
	
	private Strategy buildShortStrategy() {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);

		Rule priceCrossesKijunDownRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
		Rule priceUnderCloud = new UnderIndicatorRule(closePrice, senkouSpanA)
				.and(new UnderIndicatorRule(closePrice, senkouSpanB));
		Rule chikouUnderPrice = new UnderIndicatorRule(chikouSpan, closePrice);

		Rule signalA = priceCrossesKijunDownRule.and(priceUnderCloud);
		Rule signalB = new CrossedDownIndicatorRule(closePrice, senkouSpanB).and(priceUnderCloud);

		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));
		
		Rule entryRule = chikouUnderPrice.and(signalA.or(signalB));
		Rule exitRule = signalOut;
		return new BaseStrategy(entryRule, exitRule);
	}
	
	private Strategy buildStrategySMA() {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

		SMAIndicator shortSma = new SMAIndicator(closePrice, 9);
		SMAIndicator longSma = new SMAIndicator(closePrice, 26);

		Rule entryRule = null;
		Rule exitRule = null;

		entryRule = new CrossedUpIndicatorRule(shortSma, longSma);
		exitRule = new CrossedDownIndicatorRule(shortSma, longSma);

		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.01));

		return new BaseStrategy(entryRule, signalOut);
	}

	private void enterBuyXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		//Double stopLoss = calculateStopLossInPips(sr).doubleValue();	
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.BUY,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getAsk(), 0.0, 0.0, symbolRecord.getSymbol(), Configuration.volume, 0L, "" , 0L);
		

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory.executeTradeTransactionCommand(connector,
				ttInfoRecord);
		
		System.out.println("Opened buy position for: " + symbolRecord.getSymbol() + ". successfully");
	}

	private void exitBuyXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.BUY,
				TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory
				.executeTradeTransactionCommand(connector, ttInfoRecord);
		
		System.out.println("Closed buy position for: " + tr.getSymbol() + ". successfully");
	}
	
	private void enterSellXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		//Double stopLoss = calculateStopLossInPips(sr).doubleValue();	
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.SELL,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getBid(), 0.0, 0.0, symbolRecord.getSymbol(), Configuration.volume, 0L, "" , 0L);
		

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory.executeTradeTransactionCommand(connector,
				ttInfoRecord);
		
		System.out.println("Opened sell position for: " + symbolRecord.getSymbol() + ". successfully");
	}

	private void exitSellXTB() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);
		
		if(tr==null) return;
		
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(TRADE_OPERATION_CODE.SELL,
				TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());

		TradeTransactionResponse tradeTransactionResponse = APICommandFactory
				.executeTradeTransactionCommand(connector, ttInfoRecord);
		
		System.out.println("Closed sell position for: " + tr.getSymbol() + ". successfully");
	}


	

}
