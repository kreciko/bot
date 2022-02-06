package pl.kordek.forex.bot.rules;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import pl.kordek.forex.bot.indicator.IchimokuIndicators;

public class IchimokuRules {
	private BaseBarSeries series;
	private BaseBarSeries parentSeries;

	private int index;

	// RULES
	private Rule priceOverCloud;
	private Rule priceUnderCloud;
	private Rule tenkanSenUnderCloud;
	private Rule tenkanSenOverCloud;
	private Rule chikouOverPrice;
	private Rule chikouUnderPrice;
	private Rule cloudBullish;
	private Rule cloudBearish;
	private Rule trendBullishConfirmed;
	private Rule trendBearishConfirmed;
	private Rule tenkanCrossesKijunDownRule;
	private Rule tenkanCrossesKijunUpRule;
	private Rule tenkanOverCloudRule;
	private Rule tenkanUnderCloudRule;

	private IchimokuIndicators ichimokuInd;




	public IchimokuRules(int index,BaseBarSeries series, BaseBarSeries parentSeries) {
		super();
		this.series = series;
		this.parentSeries = parentSeries;
		this.index = index;


		ichimokuInd = new IchimokuIndicators(series, parentSeries);

		createRules();
	}

	private void createRules() {
		// INDICATORS
		ClosePriceIndicator closePrice = ichimokuInd.getClosePrice();
		IchimokuTenkanSenIndicator tenkanSen = ichimokuInd.getTenkanSen();


		// RULES
		priceOverCloud = new OverIndicatorRule(closePrice, ichimokuInd.getSenkouSpanA())
				.and(new OverIndicatorRule(closePrice, ichimokuInd.getSenkouSpanB()));
		priceUnderCloud = new UnderIndicatorRule(closePrice, ichimokuInd.getSenkouSpanA())
				.and(new UnderIndicatorRule(closePrice, ichimokuInd.getSenkouSpanB()));
		tenkanSenUnderCloud = new UnderIndicatorRule(tenkanSen, ichimokuInd.getSenkouSpanA())
				.and(new UnderIndicatorRule(tenkanSen, ichimokuInd.getSenkouSpanB()));
		tenkanSenOverCloud = new OverIndicatorRule(tenkanSen, ichimokuInd.getSenkouSpanA())
				.and(new OverIndicatorRule(tenkanSen, ichimokuInd.getSenkouSpanB()));
		tenkanCrossesKijunDownRule = new CrossedDownIndicatorRule(tenkanSen, ichimokuInd.getKijunSen());
		tenkanCrossesKijunUpRule = new CrossedUpIndicatorRule(tenkanSen, ichimokuInd.getKijunSen());


		// chikou span has empty values for 26 bars
		chikouOverPrice = new OverIndicatorRule(new ConstantIndicator<>(series, ichimokuInd.getChikouSpan().getValue(index - 26)),
				closePrice.getValue(index - 26));
		chikouUnderPrice = new UnderIndicatorRule(
				new ConstantIndicator<>(series, ichimokuInd.getChikouSpan().getValue(index - 26)),
				closePrice.getValue(index - 26));

		cloudBullish = new OverIndicatorRule(ichimokuInd.getSenkouSpanA(), ichimokuInd.getSenkouSpanB());
		cloudBearish = new UnderIndicatorRule(ichimokuInd.getSenkouSpanA(), ichimokuInd.getSenkouSpanB());


		tenkanOverCloudRule = new OverIndicatorRule(tenkanSen, ichimokuInd.getSenkouSpanB())
				.and(new OverIndicatorRule(tenkanSen, ichimokuInd.getSenkouSpanA()));
		tenkanUnderCloudRule = new UnderIndicatorRule(tenkanSen, ichimokuInd.getSenkouSpanB())
				.and(new UnderIndicatorRule(tenkanSen, ichimokuInd.getSenkouSpanA()));
	}

	public Rule getPriceUnderCloud() {
		return priceUnderCloud;
	}

	public Rule getPriceOverCloud() {
		return priceOverCloud;
	}

	public Rule getTenkanSenUnderCloud() {
		return tenkanSenUnderCloud;
	}

	public Rule getTenkanSenOverCloud() {
		return tenkanSenOverCloud;
	}


	public Rule getCloudBullish() {
		return cloudBullish;
	}

	public Rule getCloudBearish() {
		return cloudBearish;
	}


	public Rule getTenkanCrossesKijunDownRule() {
		return tenkanCrossesKijunDownRule;
	}

	public Rule getTenkanCrossesKijunUpRule() {
		return tenkanCrossesKijunUpRule;
	}

	public IchimokuIndicators getIchimokuInd() {
		return ichimokuInd;
	}
}
