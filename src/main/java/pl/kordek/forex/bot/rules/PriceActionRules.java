package pl.kordek.forex.bot.rules;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.candles.BearishPinBarIndicator;
import org.ta4j.core.indicators.candles.BearishShrinkingCandlesIndicator;
import org.ta4j.core.indicators.candles.BullishPinBarIndicator;
import org.ta4j.core.indicators.candles.BullishShrinkingCandlesIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.rules.*;
import pl.kordek.forex.bot.indicator.SatisfiedPriceActionIndicators;

public class PriceActionRules {
	private SatisfiedPriceActionIndicators satisfiedPriceActionIndicators;

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
	private Rule customPriceActionShortRule;
	private Rule priceActionNotTooDynamic;
	private Rule marketNotChoppy;


	public PriceActionRules(BaseBarSeries series, BaseBarSeries parentSeries){
		this(series, parentSeries,7);
	}
	public PriceActionRules(BaseBarSeries series, BaseBarSeries parentSeries, int scannedBarCount) {
		satisfiedPriceActionIndicators = new SatisfiedPriceActionIndicators(series,parentSeries,scannedBarCount);

		ClosePriceIndicator closePrice = satisfiedPriceActionIndicators.getClosePrice();

		IsFallingRule isFalling = new IsFallingRule(closePrice, 1);
        IsRisingRule isRising = new IsRisingRule(closePrice, 1);

		bearishEngulfingRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getBearishEngulfingInd());
		bullishEngulfingRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getBullishEngulfingInd());
		bearishEngulfingPrevRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getBearishEngulfingIndPrev());
		bullishEngulfingPrevRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getBullishEngulfingIndPrev());
		threeBlackCrowsRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getThreeBlackCrowsInd());
		threeWhiteSoldiersRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getThreeWhiteSoldiersInd());
		bullishHaramiRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getBullishHaramiIndicator());
		bearishHaramiRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getBearishHaramiIndicator());
		bullishPinBarRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getPrevBullishPinBarIndicator()).and(isRising);
		bearishPinBarRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getPrevBearishPinBarIndicator()).and(isFalling);
		bullishShrinkingCandlesRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getBullishShrinkingCandlesIndicator());
		bearishShrinkingCandlesRule = new BooleanIndicatorRule(satisfiedPriceActionIndicators.getBearishShrinkingCandlesIndicator());

		ATRIndicator atr = new ATRIndicator(series,14);
		EMAIndicator atrAverage = new EMAIndicator(atr, 50);
		HighestValueIndicator highestAtr = new HighestValueIndicator(atr, 1000);
		LowestValueIndicator lowestAtr = new LowestValueIndicator(atr, 1000);
		Indicator atrDiff = TransformIndicator.multiply(new DifferenceIndicator(highestAtr,lowestAtr), 0.2);
		Indicator highestTradableAtr = new DifferenceIndicator(highestAtr, atrDiff);
		Indicator lowestTradableAtr = new SumIndicator(lowestAtr, atrDiff);

		priceActionNotTooDynamic = new InPipeRule(atr, highestTradableAtr,lowestTradableAtr);

		marketNotChoppy = new OverIndicatorRule(satisfiedPriceActionIndicators.getChoppyMarketIndicator(), 2);
		createCustomPriceActionRules();
	}

	private void createCustomPriceActionRules() {
		//trend check
		ClosePriceIndicator closePrice = satisfiedPriceActionIndicators.getClosePrice();
		EMAIndicator shortEma = new EMAIndicator(closePrice, 7);
        EMAIndicator longEma = new EMAIndicator(closePrice, 14);

		customPriceActionLongRule = (new UnderIndicatorRule(closePrice, longEma)
				.or(new CrossedUpIndicatorRule(closePrice, longEma)))
				.and((bullishPinBarRule.and(getLongSignalsPrevailRule(2)))
						.or(bullishEngulfingRule.and(getLongSignalsPrevailRule(1))));

		customPriceActionShortRule = (new OverIndicatorRule(closePrice, longEma)
				.or(new CrossedDownIndicatorRule(closePrice, longEma)))
				.and((bearishPinBarRule.and(getShortSignalsPrevailRule(2)))
						.or(bearishEngulfingRule.and(getShortSignalsPrevailRule(1))));
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

	public Rule getLongSignalsPrevailRule(int minDifference) {
		//we subtract 1 because its "OverIndicator"
		return new OverIndicatorRule(satisfiedPriceActionIndicators.getLongMinusShortSignals(), DoubleNum.valueOf(minDifference - 1));
	}

	public Rule getShortSignalsPrevailRule(int minDifference) {
		return new OverIndicatorRule(satisfiedPriceActionIndicators.getShortMinusLongSignals(), DoubleNum.valueOf(minDifference - 1));
	}

	public SumIndicator getLongEntrySignals() {
		return satisfiedPriceActionIndicators.getLongEntrySignals();
	}

	public SumIndicator getShortEntrySignals() {
		return satisfiedPriceActionIndicators.getShortEntrySignals();
	}

	public Rule getPriceActionNotTooDynamic() {
		return priceActionNotTooDynamic;
	}

	public Rule getMarketNotChoppy() {
		return marketNotChoppy;
	}
}
