package pl.kordek.forex.bot.strategy;

import java.util.Optional;

import javax.naming.spi.DirStateFactory.Result;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.indicators.candles;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.ThreeBlackCrowsIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.NotRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


public class StrategyBuilder {

	public static double getNewStopLoss(BaseBarSeries series, OrderType orderType) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

		LowestValueIndicator lowest = null;
		HighestValueIndicator highest = null;
		LowPriceIndicator lowPrice = null;
		HighPriceIndicator highPrice = null;
		if(orderType == OrderType.BUY) {
			lowPrice = new LowPriceIndicator(series);
			lowest = new LowestValueIndicator(lowPrice, 14);
		} else {
			highPrice = new HighPriceIndicator(series);
			highest = new HighestValueIndicator(highPrice, 14);
		}

		Num result;
		int endIndex = series.getEndIndex();
		if(orderType == OrderType.BUY)
		{
			result = lowest.getValue(endIndex);
			int lowestIndex = lowest.getLowestIndex(endIndex);

			//dont return stoploss if the lowest value is the newest value. And if the lowest value is one of the rising candles
			if( result.compareTo(lowPrice.getValue(endIndex)) == 0 || (!series.getBar(lowestIndex).isBearish() && !series.getBar(lowestIndex-1).isBearish())) {
				return 0.0;
			}
		}
		else {
			result = highest.getValue(endIndex);
			int highestIndex = highest.getHighestIndex(endIndex);

			if( result.compareTo(highPrice.getValue(endIndex)) == 0 || (!series.getBar(highestIndex).isBullish() && !series.getBar(highestIndex-1).isBullish())) {
				return 0.0;
			}
		}


		return result.doubleValue();
	}

	public static Strategy buildLongStrategy(int index, BaseBarSeries series, BaseBarSeries parentSeries) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}

		IchimokuRules ichimokuRules = new IchimokuRules(index, series, parentSeries);
		CandlesRules candlesRules = new CandlesRules(series);

		//RULES IN
		Rule chikouOverPrice = ichimokuRules.getChikouOverPrice();
		Rule priceOverCloud = ichimokuRules.getPriceOverCloud();
		Rule trendBullishConfirmed = ichimokuRules.getTrendBullishConfirmed();
		Rule tenkanCrossesKijunUp = ichimokuRules.getTenkanCrossesKijunUpRule();

		//RULES OUT
		Rule tenkanUnderCloud = ichimokuRules.getTenkanSenUnderCloud();
		Rule bearishEngulfing = candlesRules.getBearishEngulfingRule();
		Rule threeBlackCrows = candlesRules.getThreeBlackCrowsRule();

		Rule entryRule = chikouOverPrice.and(priceOverCloud).and(trendBullishConfirmed)
				.and(tenkanCrossesKijunUp);
		Rule exitRule = bearishEngulfing.or(threeBlackCrows);
		return new BaseStrategy(entryRule, exitRule);
	}

	public static Strategy buildShortStrategy(int index, BaseBarSeries series, BaseBarSeries parentSeries) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}

		IchimokuRules ichimokuRules = new IchimokuRules(index, series, parentSeries);
		CandlesRules candlesRules = new CandlesRules(series);

		//RULES IN
		Rule chikouUnderPrice = ichimokuRules.getChikouUnderPrice();
		Rule priceUnderCloud = ichimokuRules.getPriceUnderCloud();
		Rule trendBearishConfirmed = ichimokuRules.getTrendBearishConfirmed();
		Rule tenkanCrossesKijunDown = ichimokuRules.getTenkanCrossesKijunDownRule();

		//RULES OUT
		Rule tenkanOverCloud = ichimokuRules.getTenkanSenOverCloud();
		Rule bullishEngulfing = candlesRules.getBullishEngulfingRule();
		Rule threeWhiteSoldiers = candlesRules.getThreeWhiteSoldiersRule();

		Rule entryRule = chikouUnderPrice.and(priceUnderCloud).and(trendBearishConfirmed).and(tenkanCrossesKijunDown);
		Rule exitRule = bullishEngulfing.or(threeWhiteSoldiers);
		return new BaseStrategy(entryRule, exitRule);
	}
}
