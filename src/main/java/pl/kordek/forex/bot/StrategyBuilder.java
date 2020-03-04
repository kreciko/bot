package pl.kordek.forex.bot;

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
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

public class StrategyBuilder {
	static Strategy buildLongStrategy(int endIndex, BaseBarSeries series) {
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
		
		//chikou span has empty values for 26 bars
		Rule chikouOverPrice = new OverIndicatorRule(new ConstantIndicator(series, chikouSpan.getValue(endIndex-26)), closePrice.getValue(endIndex-26));

		Rule signalA = priceCrossesKijunUpRule;
		Rule signalB = new CrossedUpIndicatorRule(closePrice, senkouSpanA);

		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));
		
		Rule entryRule = chikouOverPrice.and(priceOverCloud).and(signalA.or(signalB));
		Rule exitRule = signalOut;
		return new BaseStrategy(entryRule, exitRule);
	}
	
	static Strategy buildShortStrategy(int endIndex, BaseBarSeries series) {
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
		
		//chikou span has empty values for 26 bars
		Rule chikouUnderPrice = new UnderIndicatorRule(new ConstantIndicator(series, chikouSpan.getValue(endIndex-26)), closePrice.getValue(endIndex-26));

		Rule signalA = priceCrossesKijunDownRule;
		Rule signalB = new CrossedDownIndicatorRule(closePrice, senkouSpanA);

		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));
		
		Rule entryRule = chikouUnderPrice.and(priceUnderCloud).and(signalA.or(signalB));
		Rule exitRule = signalOut;
		return new BaseStrategy(entryRule, exitRule);
	}
	
	static Strategy buildStrategySMA(BaseBarSeries series) {
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
