package pl.kordek.forex.bot.strategy;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BearishHaramiIndicator;
import org.ta4j.core.indicators.candles.BearishPinBarIndicator;
import org.ta4j.core.indicators.candles.BearishShrinkingCandlesIndicator;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BullishHaramiIndicator;
import org.ta4j.core.indicators.candles.BullishPinBarIndicator;
import org.ta4j.core.indicators.candles.BullishShrinkingCandlesIndicator;
import org.ta4j.core.indicators.candles.ThreeBlackCrowsIndicator;
import org.ta4j.core.indicators.candles.ThreeWhiteSoldiersIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.SatisfiedCountIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.IsHighestRule;
import org.ta4j.core.trading.rules.IsLowestRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

public class CandlesRulesBuilder {
	private BaseBarSeries series;
	private Rule threeWhiteSoldiersRule;
	private Rule threeBlackCrowsRule;
	private Rule bearishEngulfingRule;
	private Rule bearishEngulfingPrevRule;
	private Rule bullishEngulfingRule;
	private Rule bullishEngulfingPrevRule;
	private Rule bearishHaramiRule;
	private Rule bullishHaramiRule;
	private Rule bearishPinBarRule;
	private Rule bullishPinBarRule;
	private Rule bullishShrinkingCandlesRule;
	private Rule bearishShrinkingCandlesRule;
	private Rule customPriceActionLongRule;
	private Rule longSignalsPrevailRule;
	private Rule customPriceActionShortRule;
	private Rule shortSignalsPrevailRule;

	private SumIndicator longEntrySignals;
	private SumIndicator shortEntrySignals;

	public CandlesRulesBuilder(BaseBarSeries series) {
		this.series = series;


		BearishEngulfingIndicator bearishEngulfingInd = new BearishEngulfingIndicator(series);
		BullishEngulfingIndicator bullishEngulfingInd = new BullishEngulfingIndicator(series);
		PreviousValueIndicator<Boolean> bearishEngulfingIndPrev = new PreviousValueIndicator<>(bearishEngulfingInd);
		PreviousValueIndicator<Boolean> bullishEngulfingIndPrev = new PreviousValueIndicator<>(bullishEngulfingInd);

		ThreeBlackCrowsIndicator threeBlackCrowsInd = new ThreeBlackCrowsIndicator(series, 14, 1.0);
		ThreeWhiteSoldiersIndicator threeWhiteSoldiersInd = new ThreeWhiteSoldiersIndicator(series, 14, DoubleNum.valueOf(1));

		BearishHaramiIndicator bearishHaramiIndicator = new BearishHaramiIndicator(series);
		BullishHaramiIndicator bullishHaramiIndicator = new BullishHaramiIndicator(series);

		BearishPinBarIndicator bearishPinBarIndicator = new BearishPinBarIndicator(series, 0.6);
		BullishPinBarIndicator bullishPinBarIndicator = new BullishPinBarIndicator(series, 0.6);

		BullishShrinkingCandlesIndicator bullishShrinkingCandlesIndicator = new BullishShrinkingCandlesIndicator(series, 3, true);
		BearishShrinkingCandlesIndicator bearishShrinkingCandlesIndicator = new BearishShrinkingCandlesIndicator(series, 3, true);

		bearishEngulfingRule = new BooleanIndicatorRule(bearishEngulfingInd);
		bullishEngulfingRule = new BooleanIndicatorRule(bullishEngulfingInd);
		bearishEngulfingPrevRule = new BooleanIndicatorRule(bearishEngulfingIndPrev);
		bullishEngulfingPrevRule = new BooleanIndicatorRule(bullishEngulfingIndPrev);
		threeBlackCrowsRule = new BooleanIndicatorRule(threeBlackCrowsInd);
		threeWhiteSoldiersRule = new BooleanIndicatorRule(threeWhiteSoldiersInd);
		bullishHaramiRule = new BooleanIndicatorRule(bullishHaramiIndicator);
		bearishHaramiRule = new BooleanIndicatorRule(bearishHaramiIndicator);
		bullishPinBarRule = new BooleanIndicatorRule(bullishPinBarIndicator);
		bearishPinBarRule = new BooleanIndicatorRule(bearishPinBarIndicator);
		bullishShrinkingCandlesRule = new BooleanIndicatorRule(bullishShrinkingCandlesIndicator);
		bearishShrinkingCandlesRule = new BooleanIndicatorRule(bearishShrinkingCandlesIndicator);

		createCustomPriceActionRules();
	}

	private void createCustomPriceActionRules() {
		SatisfiedCountIndicator satisfiedBullishPinbars = new SatisfiedCountIndicator(new BullishPinBarIndicator(series, 0.6), 7);
		SatisfiedCountIndicator satisfiedBearishPinbars = new SatisfiedCountIndicator(new BearishPinBarIndicator(series, 0.6), 7);

		SatisfiedCountIndicator satisfiedBullishEngulfing = new SatisfiedCountIndicator(new BullishEngulfingIndicator(series), 7);
		SatisfiedCountIndicator satisfiedBearishEngulfing = new SatisfiedCountIndicator(new BearishEngulfingIndicator(series), 7);

		SatisfiedCountIndicator satisfiedBullishHarami = new SatisfiedCountIndicator(new BullishHaramiIndicator(series), 7);
		SatisfiedCountIndicator satisfiedBearishHarami = new SatisfiedCountIndicator(new BearishHaramiIndicator(series), 7);

		SatisfiedCountIndicator satisfiedBullishShrinkingCandles = new SatisfiedCountIndicator(new BullishShrinkingCandlesIndicator(series, 3, true), 7);
		SatisfiedCountIndicator satisfiedBearishShrinkingCandles = new SatisfiedCountIndicator(new BearishShrinkingCandlesIndicator(series, 3, true), 7);

		this.longEntrySignals = new SumIndicator(satisfiedBullishPinbars, satisfiedBullishEngulfing, satisfiedBullishHarami, satisfiedBearishShrinkingCandles);
		this.shortEntrySignals = new SumIndicator(satisfiedBearishPinbars, satisfiedBearishEngulfing, satisfiedBearishHarami, satisfiedBullishShrinkingCandles);

		DifferenceIndicator difference = new DifferenceIndicator(longEntrySignals, shortEntrySignals);

		LowPriceIndicator lowPrice = new LowPriceIndicator(series);
		HighPriceIndicator highPrice = new HighPriceIndicator(series);

		IsLowestRule isLowest = new IsLowestRule(lowPrice, 4);
		IsHighestRule isHighest = new IsHighestRule(highPrice, 4);

		HighestValueIndicator highestValue = new HighestValueIndicator(highPrice, 7);
		int highestIndex = highestValue.getHighestIndex(series.getEndIndex());

		//trend check
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		EMAIndicator shortEma = new EMAIndicator(closePrice, 7);
        EMAIndicator longEma = new EMAIndicator(closePrice, 14);


		longSignalsPrevailRule = new OverIndicatorRule(difference, DoubleNum.valueOf(1));
		customPriceActionLongRule = isLowest
				.and(bullishPinBarRule.or(bullishEngulfingRule)).and(new UnderIndicatorRule(closePrice, longEma));

		shortSignalsPrevailRule = new UnderIndicatorRule(difference, DoubleNum.valueOf(-1));
		customPriceActionShortRule = isHighest
				.and(bearishPinBarRule.or(bearishEngulfingRule)).and(new OverIndicatorRule(closePrice, longEma));
	}

	public Rule getThreeWhiteSoldiersRule() {
		return threeWhiteSoldiersRule;
	}
	public Rule getThreeBlackCrowsRule() {
		return threeBlackCrowsRule;
	}
	public Rule getBearishEngulfingRule() {
		return bearishEngulfingRule;
	}
	public Rule getBullishEngulfingRule() {
		return bullishEngulfingRule;
	}

	public Rule getBearishEngulfingPrevRule() {
		return bearishEngulfingPrevRule;
	}

	public Rule getBullishEngulfingPrevRule() {
		return bullishEngulfingPrevRule;
	}

	public Rule getBearishHaramiRule() {
		return bearishHaramiRule;
	}

	public Rule getBullishHaramiRule() {
		return bullishHaramiRule;
	}

	public Rule getBearishPinBarRule() {
		return bearishPinBarRule;
	}

	public Rule getBullishPinBarRule() {
		return bullishPinBarRule;
	}


	public Rule getBullishShrinkingCandlesRule() {
		return bullishShrinkingCandlesRule;
	}

	public Rule getBearishShrinkingCandlesRule() {
		return bearishShrinkingCandlesRule;
	}

	public Rule getCustomPriceActionLongRule() {
		return customPriceActionLongRule;
	}

	public Rule getCustomPriceActionShortRule() {
		return customPriceActionShortRule;
	}

	public Rule getLongSignalsPrevailRule() {
		return longSignalsPrevailRule;
	}

	public Rule getShortSignalsPrevailRule() {
		return shortSignalsPrevailRule;
	}

	public SumIndicator getLongEntrySignals() {
		return longEntrySignals;
	}

	public SumIndicator getShortEntrySignals() {
		return shortEntrySignals;
	}




}
