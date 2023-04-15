package pl.kordek.forex.bot.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import io.jenetics.BitGene;
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

import pl.kordek.forex.bot.api.BrokerAPI;
import pl.kordek.forex.bot.checker.TradeQualityChecker;
import pl.kordek.forex.bot.domain.BackTestInfo;
import pl.kordek.forex.bot.domain.PositionInfo;
import pl.kordek.forex.bot.domain.SymbolResponseInfo;
import pl.kordek.forex.bot.domain.TradeInfo;
import pl.kordek.forex.bot.indicator.BollingerBandsIndicators;
import pl.kordek.forex.bot.indicator.GeneralIndicators;
import pl.kordek.forex.bot.indicator.IchimokuIndicators;
import pl.kordek.forex.bot.indicator.MACDIndicators;
import pl.kordek.forex.bot.rules.PriceActionRules;
import pl.kordek.forex.bot.rules.IchimokuRules;
import pl.kordek.forex.bot.utils.MyStrategyCharts;
import pro.xstore.api.message.error.APICommunicationException;

public class StrategyTester {
	private BaseBarSeries series = null;
	private BaseBarSeries parentSeries = null;
	private BrokerAPI api = null;

	public StrategyTester(BrokerAPI api, BaseBarSeries series, BaseBarSeries parentSeries) {
		this.series = series;
		this.parentSeries = parentSeries;
		this.api = api;
	}

	public void strategyTest(int index, String symbol, HashMap<String,HashMap<String, BackTestInfo>> winRatioStrategyMap) {

		Indicator<Num> donchianIndLong =
				new DonchianChannelLowerIndicator(series, 20);
		Indicator<Num> donchianIndShort =
				new DonchianChannelUpperIndicator(series, 20);

		LongStrategyBuilder longStrategyBuilder = new LongStrategyBuilder(series, parentSeries, donchianIndLong, false, false);
		ShortStrategyBuilder shortStrategyBuilder = new ShortStrategyBuilder(series, parentSeries, donchianIndShort, false, false);


		List<Strategy> longStrategies = longStrategyBuilder.getStrategyList();
		List<Strategy> shortStrategies = shortStrategyBuilder.getStrategyList();

		OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

		int shortSignals = 0;
		int longSignals = 0;
		System.out.println("End Index :"+index);
		for(int i= index; i>=index-100; i--){
			for(Strategy shortStrat : shortStrategies) {
				if(shortStrat.shouldEnter(i)) {
					shortSignals++;
					System.out.println(shortStrat.getName() + " Should enter short for: " + symbol + " index:" + i + " price:" + closePrice.getValue(i)
							+ " = " + shortStrat.shouldEnter(i));


				}
			}
			for(Strategy longStrat : longStrategies) {
				if(longStrat.shouldEnter(i)) {
					longSignals++;
					System.out.println(longStrat.getName() + " Should enter long for: " + symbol + " index:" + i + " price:" + closePrice.getValue(i)
							+ " = " + longStrat.shouldEnter(i));
				}

			}
		}
		System.out.println("Short entry signals :"+shortSignals + " . Long entry signals :"+longSignals);


		TradeType tradeType = TradeType.BUY;

		System.out.println();
		System.out.println("Symbol: " + symbol);
		System.out.println("Close Price: " + closePrice.getValue(index));

		//bollingerTest(index);
		//priceActionTest2(index);
		macdTest(index);
		//ichimokuTest(index);
		askForCharts();
		//testProtector();
	}

	private void testProtector(){

		try {
			TradeQualityChecker qualityChecker = new TradeQualityChecker(api);
			SymbolResponseInfo sRes = api.getSymbolResponseInfo();
			TradeInfo tradeInfo = new TradeInfo(series,TradeType.BUY, new BigDecimal(1.14034), new BigDecimal(1.14033)/*new BigDecimal(1.06619)*/,BigDecimal.ZERO,null,0.05,"sth");
			boolean risky = qualityChecker.checkTradeNotTooRisky(tradeInfo);
			System.out.println("Risky? :"+risky);
		} catch (APICommunicationException e) {
			e.printStackTrace();
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

	void priceActionTest2(int index){
		ATRIndicator atr = new ATRIndicator(series,14);
		PreviousValueIndicator<Num> prevAtr = new PreviousValueIndicator(atr);
		TRIndicator tr = new TRIndicator(series);
		Rule entryBarNotTooBig = new UnderIndicatorRule(tr, TransformIndicator.multiply(prevAtr, 15.0));

		System.out.println("TR: "+tr.getValue(index));
		System.out.println("Atr: "+atr.getValue(index));
		System.out.println("Prev Atr: "+prevAtr.getValue(index));
		BigDecimal div = BigDecimal.valueOf(tr.getValue(index).doubleValue()).
				divide(BigDecimal.valueOf(prevAtr.getValue(index).doubleValue()),2, RoundingMode.HALF_UP);
		System.out.println("TR Div by prev ATR: "+ div.toString());
		System.out.println("Not too big: "+entryBarNotTooBig.isSatisfied(index));
	}

	void bollingerTest(int index){
		BollingerBandsIndicators bbInds = new BollingerBandsIndicators(series, parentSeries);
		ClosePriceIndicator closePriceIndicator = bbInds.getClosePrice();
		BollingerBandsLowerIndicator lowerBB = bbInds.getLowBBand();
		BollingerBandsUpperIndicator upperBB = bbInds.getUpBBand();

		System.out.println("Lower: "+lowerBB.getValue(index));
		System.out.println("Upper: "+upperBB.getValue(index));
		System.out.println("Crossed Up: "+ new CrossedUpIndicatorRule(closePriceIndicator,upperBB).isSatisfied(index));
		System.out.println("Crossed Down: "+new CrossedDownIndicatorRule(closePriceIndicator,lowerBB).isSatisfied(index));
		System.out.println("Trend: "+(new OverIndicatorRule(closePriceIndicator,bbInds.getTrendLine200()).isSatisfied(index) ? "Bullish" : "Bearish"));

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
		System.out.println("SenkouSpanA: "+ichimokuInd.getSenkouSpanA().getValue(index));
		System.out.println("SenkouSpanB: "+ichimokuInd.getSenkouSpanB().getValue(index));
		System.out.println("Cloud bullish: "+ichimokuRules.getCloudBullish().isSatisfied(index));
		System.out.println("Cloud bearish: "+ichimokuRules.getCloudBearish().isSatisfied(index));
		System.out.println("Tenkan crosses kijun: "+ichimokuRules.getTenkanCrossesKijunUpRule().isSatisfied(index));
		System.out.println("Tenkan crosses kijun down: "+ichimokuRules.getTenkanCrossesKijunDownRule().isSatisfied(index));
		System.out.println("Price under Tenkan: "+new UnderIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTenkanSen()).isSatisfied(index));
	}

	void macdTest(int index){
		MACDIndicators macdInd = new MACDIndicators(series, parentSeries);

		System.out.println("MACD: "+macdInd.getMacd().getValue(index));
		System.out.println("Signal: "+ macdInd.getSignal().getValue(index));
		System.out.println("Below 0: "+ new UnderIndicatorRule(macdInd.getMacd(), DoubleNum.valueOf(0)).isSatisfied(index));
		System.out.println("CrossedUp: "+ new CrossedUpIndicatorRule(macdInd.getMacd(), macdInd.getSignal()).isSatisfied(index));
		System.out.println("CrossedDown: "+ new CrossedDownIndicatorRule(macdInd.getMacd(), macdInd.getSignal()).isSatisfied(index));
		System.out.println("Over smart trendline 200: "+ new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartTrendLine200()).isSatisfied(index)+ " trendline val: "+macdInd.getSmartTrendLine200().getValue(index));
		System.out.println("Over smart parent trendline 50: "+ new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartParentTrendLine50()).isSatisfied(index)+ " trendline val: "+macdInd.getSmartParentTrendLine50().getValue(index));
		System.out.println("Under smart trendline 200: "+ new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartTrendLine200()).isSatisfied(index));
		System.out.println("Under smart parent trendline 50: "+ new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartParentTrendLine50()).isSatisfied(index));
	}


	private void askForCharts() {
//		System.out.println("Do you wish to display trade chart (y/n) ?");
//		Scanner sc = new Scanner(System.in);
//		String ans = sc.nextLine();
//		if(ans.equals("y")){
			List<Indicator<Num>> inds = new ArrayList<>();

			ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
			ClosePriceIndicator closePriceParent = new ClosePriceIndicator(parentSeries);
			GeneralIndicators genInds = new GeneralIndicators(series, parentSeries);

			Indicator trendLine = genInds.getSmartTrendLine200();
			Indicator parentTrendLine = genInds.getSmartParentTrendLine50();
			ParentIndicator parentTrendLineTest = new ParentIndicator(new EMASmartIndicator(closePriceParent, 50),4);
			ParentIndicator closePriceParentTest = new ParentIndicator(closePriceParent, 4);
			ParentIndicator parentTrendLineTest2 = genInds.getSmartParentTrendLine50();
			MACDIndicators macdIndicators = new MACDIndicators(series, parentSeries);
			IchimokuIndicators ichimokuIndicators = new IchimokuIndicators(series, parentSeries);
			Indicator senkouSpanA = ichimokuIndicators.getSenkouSpanA();
			Indicator senkouSpanB = ichimokuIndicators.getSenkouSpanB();
			BollingerBandsIndicators bbInds = new BollingerBandsIndicators(series, parentSeries);

//			inds.add(closePrice);
//			inds.add(bbInds.getLowBBand());
//			inds.add(bbInds.getUpBBand());

			inds.add(macdIndicators.getMacd());
			inds.add(macdIndicators.getSignal());



			//inds.add(trendLine);
			//inds.add(genInds.getSmartParentTrendLine50());
			MyStrategyCharts.buildIndicatorChart(series, inds,  "trends "+series.getName());
//		}


	}
}
