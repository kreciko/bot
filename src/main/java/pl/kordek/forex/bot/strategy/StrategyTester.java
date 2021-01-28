package pl.kordek.forex.bot.strategy;

import java.math.BigDecimal;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BullishShrinkingCandlesIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.StopLossSmartIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.IsFallingRule;
import org.ta4j.core.trading.rules.IsHighestRule;
import org.ta4j.core.trading.rules.IsLowestRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import pl.kordek.forex.bot.constants.Configuration;

public class StrategyTester {
	private BaseBarSeries series = null;
	private BaseBarSeries helperSeries = null;

	public StrategyTester(BaseBarSeries series, BaseBarSeries helperSeries) {
		this.series = series;
		this.helperSeries = helperSeries;
	}

	public void strategyTest(int index, String symbol) {

		Strategy longStrategy = StrategyBuilder.buildLongStrategy(index, series, helperSeries);
		Strategy shortStrategy = StrategyBuilder.buildShortStrategy(index, series, helperSeries);

		OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		System.out.println("Should enter short for: " + symbol + " index:" + index + " price:" + closePrice.getValue(index)
				+ " = " + shortStrategy.shouldEnter(index));
		System.out.println("Should enter long for: " + symbol + " index:" + index + " price:" + closePrice.getValue(index)
		+ " = " + longStrategy.shouldEnter(index));

		CandlesRulesBuilder candlesRules = new CandlesRulesBuilder(series);


		BullishEngulfingIndicator bullishEngulfingCandle = new BullishEngulfingIndicator(series);
		BearishEngulfingIndicator bearishEngulfingCandle = new BearishEngulfingIndicator(series);
		Rule bullishEngulfingCandleExist = candlesRules.getBullishEngulfingRule();
		Rule bearishEngulfingCandleExist = candlesRules.getBearishEngulfingRule();

		Rule bullishShrinkingCandlesRule = candlesRules.getBullishShrinkingCandlesRule();
		Rule bearishShrinkingCandlesRule = candlesRules.getBearishShrinkingCandlesRule();
		Rule bullishPin = candlesRules.getBullishPinBarRule();
		Rule bearishPin = candlesRules.getBearishPinBarRule();

		IchimokuRules ichimokuRules = new IchimokuRules(index, series, helperSeries);

		OrderType orderType = OrderType.BUY;
		StopLossSmartIndicator slIndicator = new StopLossSmartIndicator(series, Configuration.stopLossPrc, OrderType.BUY, 7);
		double stopLossWithoutPrecision = slIndicator.getValue(index).doubleValue();//StopLossHelper.getNewStopLoss(index, series, orderType, 7).doubleValue();

		LowPriceIndicator lowPrice = new LowPriceIndicator(series);
		HighPriceIndicator highPrice = new HighPriceIndicator(series);

		IsLowestRule isLowest = new IsLowestRule(lowPrice, 7);
		IsHighestRule isHighest = new IsHighestRule(highPrice, 7);

		Rule customLong = candlesRules.getCustomPriceActionLongRule();
		Rule customShort = candlesRules.getCustomPriceActionShortRule();

		 Rule trendBullishConfirmed= ichimokuRules.getTrendBullishConfirmed();
		 Rule trendBearishConfirmed= ichimokuRules.getTrendBearishConfirmed();

		System.out.println("symbol: " + symbol);
		System.out.println("bullish engulfing: " + bullishEngulfingCandleExist.isSatisfied(index));
		System.out.println("bearish engulfing: " + bearishEngulfingCandleExist.isSatisfied(index));
		System.out.println("bullish pin: " + bullishPin.isSatisfied(index));
		System.out.println("bearish pin: " + bearishPin.isSatisfied(index));
		System.out.println("bullish shrinking candles: " + bullishShrinkingCandlesRule.isSatisfied(index));
		System.out.println("bearish shrinking candles: " + bearishShrinkingCandlesRule.isSatisfied(index));

		System.out.println("StopLoss for index for orderType "+orderType+": " + stopLossWithoutPrecision);

		System.out.println("is highest: " + isHighest.isSatisfied(index));
		System.out.println("is lowest: " + isLowest.isSatisfied(index));

		System.out.println("custom long satisfied: " + customLong.isSatisfied(index));
		System.out.println("custom short satisfied: " + customShort.isSatisfied(index));

		System.out.println("trend bullish confirmed: " + trendBullishConfirmed.isSatisfied(index));
		System.out.println("trend bearish confirmed: " + trendBearishConfirmed.isSatisfied(index));

		//System.out.println("StopLoss prc for index for orderType "+orderType+": " + stopLossWithoutPrc);

		System.out.println("Long Entry Signal Count: "+ candlesRules.getLongEntrySignals().getValue(index)+
				". Short Entry Signal Count:"+ candlesRules.getShortEntrySignals().getValue(index));
	}

	public void strategyTest2( OrderType orderType, int index) {
		TradingRecord testTradingRecord = new BaseTradingRecord(orderType);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));

		boolean entered = testTradingRecord.enter(index, series.getBar(index).getClosePrice(),
				DoubleNum.valueOf(Configuration.volume));

		System.out.println("stop loss satisfied: " + signalOut.isSatisfied(series.getEndIndex(), testTradingRecord));

	}

	void stopLossCalcTest(int index) {

	}
}
