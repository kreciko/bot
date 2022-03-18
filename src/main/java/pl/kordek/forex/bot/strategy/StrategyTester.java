package pl.kordek.forex.bot.strategy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.EMASmartIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.donchian.DonchianFallingBarCountIndicator;
import org.ta4j.core.indicators.donchian.DonchianIsFallingIndicator;
import org.ta4j.core.indicators.donchian.DonchianIsRisingIndicator;
import org.ta4j.core.indicators.donchian.DonchianRisingBarCountIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.*;

import pl.kordek.forex.bot.api.XTBOperations;
import pl.kordek.forex.bot.api.XTBSymbolOperations;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.indicator.GeneralIndicators;
import pl.kordek.forex.bot.indicator.IchimokuIndicators;
import pl.kordek.forex.bot.indicator.MACDIndicators;
import pl.kordek.forex.bot.rules.PriceActionRules;
import pl.kordek.forex.bot.rules.IchimokuRules;
import pl.kordek.forex.bot.utils.MyStrategyCharts;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.sync.SyncAPIConnector;

public class StrategyTester {
	private BaseBarSeries series = null;
	private BaseBarSeries parentSeries = null;
	private SyncAPIConnector connector = null;

	public StrategyTester(BaseBarSeries series, BaseBarSeries parentSeries, SyncAPIConnector connector) {
		this.series = series;
		this.parentSeries = parentSeries;
		this.connector = connector;
	}

	public void strategyTest(int index, String symbol) {
		StrategyBuilderOld stratBuilder = new StrategyBuilderOld(index, series, parentSeries);


		Indicator<Num> donchianIndLong =
				new DonchianChannelLowerIndicator(series, 20);
		Indicator<Num> donchianIndShort =
				new DonchianChannelUpperIndicator(series, 20);

		LongStrategyBuilder longStrategyBuilder = new LongStrategyBuilder(series, parentSeries, donchianIndLong, false);
		ShortStrategyBuilder shortStrategyBuilder = new ShortStrategyBuilder(series, parentSeries, donchianIndShort, false);


		List<Strategy> longStrategies = longStrategyBuilder.getStrategyList();
		List<Strategy> shortStrategies = shortStrategyBuilder.getStrategyList();

		OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

//		for(Strategy shortStrat : shortStrategies) {
//			System.out.println(shortStrat.getName()+" Should enter short for: " + symbol + " index:" + index + " price:" + closePrice.getValue(index)
//					+ " = " + shortStrat.shouldEnter(index));
//		}
//		for(Strategy longStrat : longStrategies) {
//			System.out.println(longStrat.getName()+" Should enter long for: " + symbol + " index:" + index + " price:" + closePrice.getValue(index)
//				+ " = " + longStrat.shouldEnter(index));
//		}

		System.out.println("End Index :"+index);
		for(int i= index; i>=index-200; i--){
			for(Strategy shortStrat : shortStrategies) {
				if(shortStrat.shouldEnter(i))
				System.out.println(shortStrat.getName()+" Should enter short for: " + symbol + " index:" + i + " price:" + closePrice.getValue(i)
						+ " = " + shortStrat.shouldEnter(i));
			}
			for(Strategy longStrat : longStrategies) {
				if(longStrat.shouldEnter(i))
				System.out.println(longStrat.getName()+" Should enter long for: " + symbol + " index:" + i + " price:" + closePrice.getValue(i)
						+ " = " + longStrat.shouldEnter(i));
			}
		}


		TradeType tradeType = TradeType.BUY;

		System.out.println();
		System.out.println("Symbol: " + symbol);
		PriceActionRules priceActionRules = new PriceActionRules(series, parentSeries);
		System.out.println("Long sig:"+priceActionRules.getLongEntrySignals().getValue(index));
		System.out.println("Short sig:"+priceActionRules.getShortEntrySignals().getValue(index));
		System.out.println("Long signals prevail (diff 0):" +priceActionRules.getLongSignalsPrevailRule(0).isSatisfied(index));
		System.out.println("PA not too dynamic:" +priceActionRules.getPriceActionNotTooDynamic().isSatisfied(index));

		//macdTest(index);
	}

	private void testProtector(){

		try {
			XTBSymbolOperations api = new XTBSymbolOperations(connector, Configuration.runTestFX);
			List<TradeRecord> openedPos = api.getOpenedPositions();
			TradeRecord pos = openedPos.get(0);
			BigDecimal openPrice = BigDecimal.valueOf(pos.getOpen_price());
			BigDecimal sl = BigDecimal.valueOf(pos.getSl());
			BigDecimal vol = BigDecimal.valueOf(pos.getVolume());
			BigDecimal contractSize = BigDecimal.valueOf(api.getSr().getSymbol().getContractSize());

			BigDecimal stopLossInCash = contractSize.multiply(openPrice.subtract(sl).abs()).multiply(vol);
			BigDecimal stopLossInPln = stopLossInCash.multiply(BigDecimal.valueOf(3.97));
			System.out.println("Lol");

		} catch (XTBCommunicationException e) {
			e.printStackTrace();
		} catch (APIErrorResponse e) {
			e.printStackTrace();
		} catch (APICommunicationException e) {
			e.printStackTrace();
		} catch (APIReplyParseException e) {
			e.printStackTrace();
		} catch (APICommandConstructionException e) {
			e.printStackTrace();
		}

	}


	public void strategyShouldEnterForThelast(List<Strategy> longStrategies,List<Strategy> shortStrategies, String symbol, int index) {
		int candleAmount = series.getEndIndex()-index;

		System.out.println("Checking whether should enter in the last "+ candleAmount +" candles for :"+symbol);
		for(Strategy longStrat : longStrategies) {
			for(int i=index;i< series.getEndIndex();i++){
				if(longStrat.shouldEnter(i)){
					int candleAm = series.getEndIndex()-i;
					System.out.println(longStrat.getName()+" Should enter LONG at :"+candleAm+ "th candle from end");
				}
			}
		}
		for(Strategy shortStrat : shortStrategies) {
			for(int i=index;i< series.getEndIndex();i++){
				if(shortStrat.shouldEnter(i)){
					int candleAm = series.getEndIndex()-i;
					System.out.println(shortStrat.getName()+" Should enter SHORT at :"+candleAm+ "th candle from end");
				}
			}
		}
	}

	private void priceActionTest(int index) {
		PriceActionRules priceActionRules = new PriceActionRules(series, parentSeries);


		BullishEngulfingIndicator bullishEngulfingCandle = new BullishEngulfingIndicator(series);
		BearishEngulfingIndicator bearishEngulfingCandle = new BearishEngulfingIndicator(series);
		Rule bullishEngulfingCandleExist = priceActionRules.getBullishEngulfingRule();
		Rule bearishEngulfingCandleExist = priceActionRules.getBearishEngulfingRule();

		Rule bullishShrinkingCandlesRule = priceActionRules.getBullishShrinkingCandlesRule();
		Rule bearishShrinkingCandlesRule = priceActionRules.getBearishShrinkingCandlesRule();
		Rule bullishPin = priceActionRules.getBullishPinBarRule();
		Rule bearishPin = priceActionRules.getBearishPinBarRule();

		LowPriceIndicator lowPrice = new LowPriceIndicator(series);
		HighPriceIndicator highPrice = new HighPriceIndicator(series);

		IsLowestRule isLowest = new IsLowestRule(lowPrice, 7);
		IsHighestRule isHighest = new IsHighestRule(highPrice, 7);

		Rule customLong = priceActionRules.getCustomPriceActionLongRule();
		Rule customShort = priceActionRules.getCustomPriceActionShortRule();

		System.out.println("bullish engulfing: " + bullishEngulfingCandleExist.isSatisfied(index));
		System.out.println("bearish engulfing: " + bearishEngulfingCandleExist.isSatisfied(index));
		System.out.println("bullish pin rule: " + bullishPin.isSatisfied(index));
		System.out.println("bearish pin rule: " + bearishPin.isSatisfied(index));
		System.out.println("bullish shrinking candles: " + bullishShrinkingCandlesRule.isSatisfied(index));
		System.out.println("bearish shrinking candles: " + bearishShrinkingCandlesRule.isSatisfied(index));


		System.out.println("Long sig:"+priceActionRules.getLongEntrySignals().getValue(index));
		System.out.println("Short sig:"+priceActionRules.getShortEntrySignals().getValue(index));
		System.out.println("Long prevail:"+priceActionRules.getLongSignalsPrevailRule(3).isSatisfied(index));
		System.out.println("Short prevail:"+priceActionRules.getShortSignalsPrevailRule(3).isSatisfied(index));
//
		ATRIndicator atr = new ATRIndicator(series,14);
		System.out.println("ATR :"+ atr.getValue(index));
		System.out.println("Avg ATR :"+ new EMAIndicator(atr, 50).getValue(index));
		System.out.println("Avg ATRx1.5 :"+ TransformIndicator.multiply(new EMAIndicator(atr, 50), 1.5).getValue(index));
		System.out.println("price action not too dynamic "+ priceActionRules.getPriceActionNotTooDynamic());
	}

	void donchianTest(int index){
		DonchianChannelLowerIndicator donchianLower = new DonchianChannelLowerIndicator(series, 20);
		DonchianChannelUpperIndicator donchianUpper = new DonchianChannelUpperIndicator(series, 20);
		DonchianIsRisingIndicator isUpperDRising = new DonchianIsRisingIndicator(donchianUpper);
		DonchianIsFallingIndicator isUpperDFalling = new DonchianIsFallingIndicator(donchianUpper);
		DonchianIsFallingIndicator isLowerDFalling = new DonchianIsFallingIndicator(donchianLower);
		DonchianRisingBarCountIndicator dRisingCount = new DonchianRisingBarCountIndicator(donchianUpper);

		PreviousValueIndicator<Boolean> wasUpperDFalling = new PreviousValueIndicator<>(isUpperDFalling);
		PreviousValueIndicator<Num> prevUpperD = new PreviousValueIndicator<>(donchianUpper);
		DonchianFallingBarCountIndicator upperDFallingCount = new DonchianFallingBarCountIndicator(prevUpperD);

		Rule wasLowerDFallingInTheMeantime =  new OverIndicatorRule(new SatisfiedCountIndicator(isLowerDFalling, upperDFallingCount), 0);

		Rule donchianEntry = new BooleanIndicatorRule(wasUpperDFalling).and(new BooleanIndicatorRule(isUpperDRising))
				.and(wasLowerDFallingInTheMeantime);

		System.out.println("Donchian lower: " + donchianLower.getValue(index));
		System.out.println("Donchian upper: " + donchianUpper.getValue(index));
		System.out.println("Upper isUpperDRising: " + isUpperDRising.getValue(index));
		System.out.println("Upper wasUpperDFalling: " + wasUpperDFalling.getValue(index));
		System.out.println("Upper D Falling Count: " + upperDFallingCount.getValue(index));
		System.out.println("wasLowerDFallingInTheMeantime: " + wasLowerDFallingInTheMeantime.isSatisfied(index));
		System.out.println("Donchian entry: " + donchianEntry.isSatisfied(index));
	}

	void ichimokuTest(int index){
		IchimokuRules ichimokuRules = new IchimokuRules(index, series, parentSeries);
		IchimokuIndicators ichimokuInd = ichimokuRules.getIchimokuInd();

		System.out.println("TenkanSen: "+ichimokuInd.getTenkanSen().getValue(index));
		System.out.println("KijunSen: "+ichimokuInd.getKijunSen().getValue(index));
		System.out.println("Cloud bullish: "+ichimokuRules.getCloudBullish().isSatisfied(index));
		System.out.println("Tenkan crosses kijun: "+ichimokuRules.getTenkanCrossesKijunUpRule().isSatisfied(index));
	}

	void macdTest(int index){
		MACDIndicators macdInd = new MACDIndicators(series, parentSeries);

		System.out.println("MACD: "+macdInd.getMacd().getValue(index));
		System.out.println("Signal: "+ macdInd.getSignal().getValue(index));
		System.out.println("Below 0: "+ new UnderIndicatorRule(macdInd.getMacd(), DoubleNum.valueOf(0)).isSatisfied(index));
		System.out.println("CrossedUp: "+ new CrossedUpIndicatorRule(macdInd.getMacd(), macdInd.getSignal()).isSatisfied(index));
		System.out.println("CrossedDown: "+ new CrossedDownIndicatorRule(macdInd.getMacd(), macdInd.getSignal()).isSatisfied(index));
		System.out.println("Over smart trendline 200: "+ new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartTrendLine200()).isSatisfied(index));
		System.out.println("Over smart parent trendline 50: "+ new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartParentTrendLine50()).isSatisfied(index));
		System.out.println("Under smart trendline 200: "+ new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartTrendLine200()).isSatisfied(index));
		System.out.println("Under smart parent trendline 50: "+ new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartParentTrendLine50()).isSatisfied(index));
	}


	private void askForCharts(String symbol) {
		System.out.println("Do you wish to display trade chart (y/n) ?");
		Scanner sc = new Scanner(System.in);
		String ans = sc.nextLine();
		if(ans.equals("y")){
			List<Indicator<Num>> inds = new ArrayList<>();

			ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
			EMAIndicator avg14 = new EMAIndicator(closePrice, 14);
			StandardDeviationIndicator sd14 = new StandardDeviationIndicator(closePrice, 14);
			BollingerBandsMiddleIndicator middleBBand = new BollingerBandsMiddleIndicator(avg14);
			BollingerBandsLowerIndicator lowBBand = new BollingerBandsLowerIndicator(middleBBand, sd14);
			BollingerBandsUpperIndicator upBBand = new BollingerBandsUpperIndicator(middleBBand, sd14);
			inds.add(closePrice);
			inds.add(middleBBand);
			inds.add(lowBBand);
			inds.add(upBBand);

			MyStrategyCharts.buildIndicatorChart(series, inds, "bband "+symbol);
		}


	}
}
