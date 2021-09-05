package pl.kordek.forex.bot.strategy;

import java.util.List;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.EMASmartIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.donchian.DonchianFallingBarCountIndicator;
import org.ta4j.core.indicators.donchian.DonchianIsFallingIndicator;
import org.ta4j.core.indicators.donchian.DonchianIsRisingIndicator;
import org.ta4j.core.indicators.donchian.DonchianRisingBarCountIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.SatisfiedCountIndicator;
import org.ta4j.core.indicators.helpers.StopLossPrcSmartIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;
import org.ta4j.core.trading.rules.IsHighestRule;
import org.ta4j.core.trading.rules.IsLowestRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.indicator.IchimokuIndicators;
import pl.kordek.forex.bot.rules.PriceActionRules;
import pl.kordek.forex.bot.rules.IchimokuRules;

public class StrategyTester {
	private BaseBarSeries series = null;
	private BaseBarSeries parentSeries = null;

	public StrategyTester(BaseBarSeries series, BaseBarSeries parentSeries) {
		this.series = series;
		this.parentSeries = parentSeries;
	}

	public void strategyTest(int index, String symbol) {

		StrategyBuilderOld stratBuilder = new StrategyBuilderOld(index, series, parentSeries);

		List<Strategy> longStrategies = stratBuilder.buildLongStrategies();
		List<Strategy> shortStrategies = stratBuilder.buildShortStrategies();

		OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

		for(Strategy shortStrat : shortStrategies) {
			System.out.println(shortStrat.getName()+" Should enter short for: " + symbol + " index:" + index + " price:" + closePrice.getValue(index)
					+ " = " + shortStrat.shouldEnter(index));
		}
		for(Strategy longStrat : longStrategies) {
			System.out.println(longStrat.getName()+" Should enter long for: " + symbol + " index:" + index + " price:" + closePrice.getValue(index)
				+ " = " + longStrat.shouldEnter(index));
		}



		IchimokuRules ichimokuRules = new IchimokuRules(index, series, parentSeries);

		OrderType orderType = OrderType.BUY;
		StopLossPrcSmartIndicator slIndicator = new StopLossPrcSmartIndicator(series, Configuration.stopLossMaxPrcFX, OrderType.BUY, 7);
		double stopLossWithoutPrecision = slIndicator.getValue(index).doubleValue();//StopLossHelper.getNewStopLoss(index, series, orderType, 7).doubleValue();


		System.out.println("symbol: " + symbol);


		System.out.println("StopLoss for index for orderType "+orderType+": " + stopLossWithoutPrecision);

		ichimokuTest(index);
		EMASmartIndicator emaMagic = new EMASmartIndicator(closePrice, 200);
		EMASmartIndicator emaMagic50 = new EMASmartIndicator(closePrice, 50);
		System.out.println("Ema magic: " + emaMagic.getValue(index));
		System.out.println("Ema magic 50: " + emaMagic.getValue(index));


	}

	public void strategyTest2( OrderType orderType, int index) {
		TradingRecord testTradingRecord = new BaseTradingRecord(orderType);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));

		boolean entered = testTradingRecord.enter(index, series.getBar(index).getClosePrice(),
				DoubleNum.valueOf(Configuration.volume));

		System.out.println("stop loss satisfied: " + signalOut.isSatisfied(series.getEndIndex(), testTradingRecord));

	}

	private void indicatorTest(int index, ClosePriceIndicator closePrice) {
		MACDIndicator macd = new MACDIndicator(closePrice, 12 , 26);
		EMAIndicator signal = new EMAIndicator(macd, 9);
		System.out.println("MACD: " + macd.getValue(index));
		System.out.println("signal: " + signal.getValue(index));
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

		System.out.println("custom long satisfied: " + customLong.isSatisfied(index));
		System.out.println("custom short satisfied: " + customShort.isSatisfied(index));

		System.out.println("Long Entry Signal Count: "+ priceActionRules.getLongEntrySignals().getValue(index)+
				". Short Entry Signal Count:"+ priceActionRules.getShortEntrySignals().getValue(index));


		System.out.println("is highest: " + isHighest.isSatisfied(index));
		System.out.println("is lowest: " + isLowest.isSatisfied(index));
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
}
