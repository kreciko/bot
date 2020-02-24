package pl.kordek.forex.bot;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Order;
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
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;

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
import pro.xstore.api.message.response.TradesResponse;
import pro.xstore.api.sync.SyncAPIConnector;

public class Robot {

	private BaseBarSeries series = null;
	private TradingRecord tradingRecord = null;
	private SymbolResponse symbolResponse = null;
	
	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	private List<TradeRecord> openedPositions;
	
	private SyncAPIConnector connector = null;

	public Robot(BaseBarSeries series, TradingRecord tradingRecord, List<TradeRecord> openedPositions, SymbolResponse symbolResponse, SyncAPIConnector connector) {
		this.series = series;
		this.openedPositions = openedPositions;
		this.symbolResponse = symbolResponse;
		this.connector = connector;
		this.tradingRecord = tradingRecord;
		
		// Initializing the trading history if necessary
		if(tradingRecord == null) {
			this.tradingRecord = new BaseTradingRecord();
			System.out.println("Init trading record for " + symbolResponse.getSymbol().getSymbol());
		}
	}

	public TradingRecord runRobotIteration() {
			String symbol = symbolResponse.getSymbol().getSymbol();
			
			Strategy strategy = buildStrategySMA();

			int endIndex = series.getEndIndex();
			if (strategy.shouldEnter(endIndex, tradingRecord)) {
				System.out.println("Strategy should ENTER on " + symbol);
				boolean entered = tradingRecord.enter(endIndex, series.getLastBar().getClosePrice(), DoubleNum.valueOf(Configuration.volume));
                if (entered) {
                	openPosition(symbol);
                }
			} else if (strategy.shouldExit(endIndex, tradingRecord)) {
				System.out.println("Strategy should EXIT on " + symbol);
				boolean exited = tradingRecord.exit(endIndex, series.getLastBar().getClosePrice(), DoubleNum.valueOf(Configuration.volume));
                if (exited) {
                	closePosition(symbol);
                }
			}
			return tradingRecord;
			
		
	}
	
	private void openPosition(String symbol) {
		// Our strategy should enter
		int endIndex = series.getEndIndex();	
		if (openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol))) {
			System.out.println("Failed to open " + symbol + ". Symbol already in the trading record");
			return;
		}

		try {
			enterBuyXTB();
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e1) {
			System.out.println("Failed to open " + symbol);
		}
	}
	
	private void closePosition(String symbol) {
		// Our strategy should exit
		int endIndex = series.getEndIndex();
		if (!openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol))) {
			System.out.println("Failed to close " + symbol + ". Symbol not in the trading record");
			return;
		}
		try {
			exitBuyXTB();
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e1) {
			System.out.println("Failed to close " + symbol);
		}
	}

	private Strategy buildStrategy() {
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


	

}
