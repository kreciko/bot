package pl.kordek.forex.bot.strategy;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

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



	public IchimokuRules(int index,BaseBarSeries series, BaseBarSeries parentSeries) {
		super();
		this.series = series;
		this.parentSeries = parentSeries;
		this.index = index;

		createRules();
	}

	private void createRules() {
		// INDICATORS
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		ClosePriceIndicator closePriceParent = new ClosePriceIndicator(parentSeries);

		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);

		IchimokuTenkanSenIndicator tenkanSenParent = new IchimokuTenkanSenIndicator(parentSeries, 9);
		IchimokuKijunSenIndicator kijunSenParent = new IchimokuKijunSenIndicator(parentSeries, 26);
		IchimokuSenkouSpanAIndicator senkouSpanAParent = new IchimokuSenkouSpanAIndicator(parentSeries, tenkanSenParent,
				kijunSenParent);
		IchimokuSenkouSpanBIndicator senkouSpanBParent = new IchimokuSenkouSpanBIndicator(parentSeries, 52);

		// Try to get data from a previous bar for confirmation
		PreviousValueIndicator closePricePrevious = new PreviousValueIndicator(closePrice);
		PreviousValueIndicator kijunSenPrevious = new PreviousValueIndicator(kijunSen);
		PreviousValueIndicator senkouSpanAPrevious = new PreviousValueIndicator(senkouSpanA);
		PreviousValueIndicator senkouSpanBPrevious = new PreviousValueIndicator(senkouSpanB);


		// RULES
		priceOverCloud = new OverIndicatorRule(closePrice, senkouSpanA)
				.and(new OverIndicatorRule(closePrice, senkouSpanB));
		priceUnderCloud = new UnderIndicatorRule(closePrice, senkouSpanA)
				.and(new UnderIndicatorRule(closePrice, senkouSpanB));
		tenkanSenUnderCloud = new UnderIndicatorRule(tenkanSen, senkouSpanA)
				.and(new UnderIndicatorRule(tenkanSen, senkouSpanB));
		tenkanSenOverCloud = new OverIndicatorRule(tenkanSen, senkouSpanA)
				.and(new OverIndicatorRule(tenkanSen, senkouSpanB));
		tenkanCrossesKijunDownRule = new CrossedDownIndicatorRule(tenkanSen, kijunSen);
		tenkanCrossesKijunUpRule = new CrossedUpIndicatorRule(tenkanSen, kijunSen);


		// chikou span has empty values for 26 bars
		chikouOverPrice = new OverIndicatorRule(new ConstantIndicator<>(series, chikouSpan.getValue(index - 26)),
				closePrice.getValue(index - 26));
		chikouUnderPrice = new UnderIndicatorRule(
				new ConstantIndicator<>(series, chikouSpan.getValue(index - 26)),
				closePrice.getValue(index - 26));

		cloudBullish = new OverIndicatorRule(senkouSpanA, senkouSpanB);
		cloudBearish = new UnderIndicatorRule(senkouSpanA, senkouSpanB);
		Rule parentCloudBullish = new OverIndicatorRule(senkouSpanAParent, senkouSpanBParent);
		Rule parentCloudBearish = new UnderIndicatorRule(senkouSpanAParent, senkouSpanBParent);
		trendBullishConfirmed = new BooleanRule(parentCloudBullish.isSatisfied(parentSeries.getEndIndex()) && cloudBullish.isSatisfied(series.getEndIndex()));
		trendBearishConfirmed = new BooleanRule(parentCloudBearish.isSatisfied(parentSeries.getEndIndex()) && cloudBearish.isSatisfied(series.getEndIndex()));
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

	public Rule getChikouOverPrice() {
		return chikouOverPrice;
	}


	public Rule getChikouUnderPrice() {
		return chikouUnderPrice;
	}

	public Rule getCloudBullish() {
		return cloudBullish;
	}

	public Rule getCloudBearish() {
		return cloudBearish;
	}

	public Rule getTrendBullishConfirmed() {
		return trendBullishConfirmed;
	}

	public Rule getTrendBearishConfirmed() {
		return trendBearishConfirmed;
	}

	public Rule getTenkanCrossesKijunDownRule() {
		return tenkanCrossesKijunDownRule;
	}

	public Rule getTenkanCrossesKijunUpRule() {
		return tenkanCrossesKijunUpRule;
	}




}
