package pl.kordek.forex.bot.strategy;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

public class StrategyBuilder {

	public static boolean isEntryLongWithConfirmationSatisfied(BaseBarSeries series, int... index) {
		//Try to get data from a previous bar for confirmation
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		int endIndex = series.getEndIndex();
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);
		
		Rule priceCrossesKijunUpRule = new CrossedUpIndicatorRule(closePrice, kijunSen);
		Rule priceOverKijunRule = new OverIndicatorRule(closePrice, kijunSen);
		Rule priceCrossesSpanAUpRule = new CrossedUpIndicatorRule(closePrice, senkouSpanA);
		Rule chikouOverPriceRule = new OverIndicatorRule(new ConstantIndicator<>(series, chikouSpan.getValue(endIndex-26)), closePrice.getValue(endIndex-26));
		Rule priceOverCloudRule = new OverIndicatorRule(closePrice, senkouSpanA)
				.and(new OverIndicatorRule(closePrice, senkouSpanB));
		boolean priceCrossedKijunUpLastBar = priceCrossesKijunUpRule.isSatisfied(endIndex-1);
		boolean priceCrossesSpanAUpLastBar = priceCrossesSpanAUpRule.isSatisfied(endIndex-1);
		boolean chikouOverPriceLastBar = chikouOverPriceRule.isSatisfied(endIndex);
		boolean priceOverCloudLastBar = priceOverCloudRule.isSatisfied(endIndex);
		boolean priceOverKijun = priceOverKijunRule.isSatisfied(endIndex);
		boolean priceOverCloud = priceOverCloudRule.isSatisfied(endIndex);
		
		boolean lastBarRulesSatisified = (priceCrossedKijunUpLastBar || priceCrossesSpanAUpLastBar) && chikouOverPriceLastBar && priceOverCloudLastBar;
		boolean currentRulesSatisfied = priceCrossedKijunUpLastBar ? priceOverKijun : priceOverCloud;
		
		return lastBarRulesSatisified && currentRulesSatisfied;
	}
	
	public static boolean isEntryShortWithConfirmationSatisfied(BaseBarSeries series) {
		//Try to get data from a previous bar for confirmation
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		int endIndex = series.getEndIndex();
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);
		
		Rule priceCrossesKijunDownRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
		Rule priceUnderKijunRule = new UnderIndicatorRule(closePrice, kijunSen);
		Rule priceCrossesSpanADownRule = new CrossedDownIndicatorRule(closePrice, senkouSpanA);
		Rule chikouUnderPriceRule = new UnderIndicatorRule(new ConstantIndicator<>(series, chikouSpan.getValue(endIndex-26)), closePrice.getValue(endIndex-26));
		Rule priceUnderCloudRule = new UnderIndicatorRule(closePrice, senkouSpanA)
				.and(new UnderIndicatorRule(closePrice, senkouSpanB));
		boolean priceCrossedKijunDownLastBar = priceCrossesKijunDownRule.isSatisfied(endIndex-1);
		boolean priceCrossesSpanADownLastBar = priceCrossesSpanADownRule.isSatisfied(endIndex-1);
		boolean chikouUnderPriceLastBar = chikouUnderPriceRule.isSatisfied(endIndex);
		boolean priceUnderCloudLastBar = priceUnderCloudRule.isSatisfied(endIndex);
		boolean priceUnderKijun = priceUnderKijunRule.isSatisfied(endIndex);
		boolean priceUnderCloud = priceUnderCloudRule.isSatisfied(endIndex);
		
		boolean lastBarRulesSatisified = (priceCrossedKijunDownLastBar || priceCrossesSpanADownLastBar) && chikouUnderPriceLastBar && priceUnderCloudLastBar;
		boolean currentRulesSatisfied = priceCrossedKijunDownLastBar ? priceUnderKijun : priceUnderCloud;
		
		return lastBarRulesSatisified && currentRulesSatisfied;
	}
	
	public static Strategy buildLongStrategy(int index, BaseBarSeries series) {
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
		Rule priceUnderCloud = new UnderIndicatorRule(closePrice, senkouSpanA)
				.and(new UnderIndicatorRule(closePrice, senkouSpanB));
		
		//chikou span has empty values for 26 bars
		Rule chikouOverPrice = new OverIndicatorRule(new ConstantIndicator<>(series, chikouSpan.getValue(index-26)), closePrice.getValue(index-26));

		
		Rule signalA = priceCrossesKijunUpRule;
		Rule signalB = new CrossedUpIndicatorRule(closePrice, senkouSpanA);

		Rule signalOutA = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));
		Rule signalOutB = priceUnderCloud;
		
		Rule entryRule = chikouOverPrice.and(priceOverCloud).and(signalA.or(signalB));
		Rule exitRule = signalOutA;
		return new BaseStrategy(entryRule, exitRule);
	}
	
	public static Strategy buildShortStrategy(int endIndex, BaseBarSeries series) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);

		Rule priceCrossesKijunDownRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
		Rule priceUnderCloud = new UnderIndicatorRule(closePrice, senkouSpanA)
				.and(new UnderIndicatorRule(closePrice, senkouSpanB));
		Rule priceOverCloud = new OverIndicatorRule(closePrice, senkouSpanA)
				.and(new OverIndicatorRule(closePrice, senkouSpanB));

		// chikou span has empty values for 26 bars
		Rule chikouUnderPrice = new UnderIndicatorRule(
				new ConstantIndicator<>(series, chikouSpan.getValue(endIndex - 26)),
				closePrice.getValue(endIndex - 26));

		// Try to get data from a previous bar for confirmation
		BaseBarSeries seriesMinusOne = series.getSubSeries(0, series.getEndIndex() - 1);
		ClosePriceIndicator closePriceMinusOne = new ClosePriceIndicator(seriesMinusOne);
		IchimokuKijunSenIndicator kijunSenMinusOne = new IchimokuKijunSenIndicator(seriesMinusOne, 26);
		IchimokuTenkanSenIndicator tenkanSenMinusOne = new IchimokuTenkanSenIndicator(seriesMinusOne, 9);
		IchimokuSenkouSpanAIndicator senkouSpanAMinusOne = new IchimokuSenkouSpanAIndicator(seriesMinusOne,
				tenkanSenMinusOne, kijunSenMinusOne);
		IchimokuSenkouSpanBIndicator senkouSpanBMinusOne = new IchimokuSenkouSpanBIndicator(seriesMinusOne, 52);
		Rule priceCrossesKijunDownMinusOneRule = new CrossedDownIndicatorRule(closePriceMinusOne, kijunSenMinusOne);
		Rule priceUnderKijunNowRule = new UnderIndicatorRule(closePrice, kijunSen);

		Rule signalANew = priceCrossesKijunDownMinusOneRule.and(priceUnderKijunNowRule);
		Rule signalBNew = new CrossedDownIndicatorRule(closePriceMinusOne, senkouSpanAMinusOne);

		Rule signalA = priceCrossesKijunDownRule;
		Rule signalB = new CrossedDownIndicatorRule(closePrice, senkouSpanA);

		Rule signalOutA = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));
		Rule signalOutB = priceOverCloud;

		Rule entryRule = chikouUnderPrice.and(priceUnderCloud).and(signalA.or(signalB));
		Rule exitRule = signalOutA;
		return new BaseStrategy(entryRule, exitRule);
	}
	
	
	public static Strategy buildStrategySMA(BaseBarSeries series) {
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
}
