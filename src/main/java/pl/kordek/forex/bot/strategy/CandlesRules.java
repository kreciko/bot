package pl.kordek.forex.bot.strategy;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.candles.ThreeBlackCrowsIndicator;
import org.ta4j.core.indicators.candles.ThreeWhiteSoldiersIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;

public class CandlesRules {
	private Rule threeWhiteSoldiersRule;
	private Rule threeBlackCrowsRule;
	private Rule bearishEngulfingRule;
	private Rule bullishEngulfingRule;


	public CandlesRules(BaseBarSeries series) {
		BearishEngulfingIndicator bearishEngulfingInd = new BearishEngulfingIndicator(series);
		BullishEngulfingIndicator bullishEngulfingInd = new BullishEngulfingIndicator(series);
		ThreeBlackCrowsIndicator threeBlackCrowsInd = new ThreeBlackCrowsIndicator(series, 14, 1.0);
		ThreeWhiteSoldiersIndicator threeWhiteSoldiersInd = new ThreeWhiteSoldiersIndicator(series, 14, DoubleNum.valueOf(1));

		bearishEngulfingRule = new BooleanIndicatorRule(bearishEngulfingInd);
		bullishEngulfingRule = new BooleanIndicatorRule(bullishEngulfingInd);
		threeBlackCrowsRule = new BooleanIndicatorRule(threeBlackCrowsInd);
		threeWhiteSoldiersRule = new BooleanIndicatorRule(threeWhiteSoldiersInd);
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


}
