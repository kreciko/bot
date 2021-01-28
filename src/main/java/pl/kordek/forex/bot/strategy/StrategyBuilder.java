package pl.kordek.forex.bot.strategy;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.candles.BullishPinBarIndicator;
import org.ta4j.core.indicators.candles.CandleSizeIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.DifferencePercentage;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MultiplierIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.IsFallingRule;
import org.ta4j.core.trading.rules.IsRisingRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import pl.kordek.forex.bot.constants.Configuration;


public class StrategyBuilder {


	public static Strategy buildLongStrategy(int index, BaseBarSeries series, BaseBarSeries helperSeries) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		PreviousValueIndicator<Num> prevClosePrice = new PreviousValueIndicator<>(closePrice);
		IchimokuRules ichimokuRules = new IchimokuRules(index, series, helperSeries);
		CandlesRulesBuilder candlesRules = new CandlesRulesBuilder(series);

		//MACD Strategy
		MACDIndicator macd = new MACDIndicator(closePrice, 12 , 26);
        EMAIndicator signal = new EMAIndicator(macd, 9);
        EMAIndicator trendLine = new EMAIndicator(closePrice, 200);
        Rule macdEntry = new OverIndicatorRule(closePrice, trendLine).and(new CrossedUpIndicatorRule(macd, signal))
                .and(new UnderIndicatorRule(macd, DoubleNum.valueOf(0)));

      //Ichimoku Strategy
        Rule priceOverCloud = ichimokuRules.getPriceOverCloud();
        Rule tenkanCrossesKijunUp = ichimokuRules.getTenkanCrossesKijunUpRule();
        Rule trendBullishConfirmed= ichimokuRules.getTrendBullishConfirmed();
        Rule cloudBullish = ichimokuRules.getCloudBullish();

      //Candle
        Rule longSignalsPrevail = candlesRules.getLongSignalsPrevailRule();


        Rule entryRule = cloudBullish.and(priceOverCloud).and(candlesRules.getCustomPriceActionLongRule()).and(longSignalsPrevail);
		//Rule entryRule = trendBullishConfirmed.and(tenkanOverCloud).and(tenkanCrossesKijunUp.or(macdEntry));

		Rule exitRule = new BooleanRule(false);
		return new BaseStrategy(entryRule, exitRule);
	}

	public static Strategy buildShortStrategy(int index, BaseBarSeries series, BaseBarSeries helperSeries) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		PreviousValueIndicator<Num> prevClosePrice = new PreviousValueIndicator<>(closePrice);

		IchimokuRules ichimokuRules = new IchimokuRules(index, series, helperSeries);
		CandlesRulesBuilder candlesRules = new CandlesRulesBuilder(series);

		//MACD Strategy
		MACDIndicator macd = new MACDIndicator(closePrice, 12 , 26);
        EMAIndicator signal = new EMAIndicator(macd, 9);
        EMAIndicator trendLine = new EMAIndicator(closePrice, 200);
        Rule macdEntry = new UnderIndicatorRule(closePrice, trendLine).and(new CrossedDownIndicatorRule(macd, signal))
                .and(new OverIndicatorRule(macd, DoubleNum.valueOf(0)));

        //Ichimoku Strategy
        Rule priceUnderCloud = ichimokuRules.getPriceUnderCloud();
        Rule tenkanCrossesKijunDown = ichimokuRules.getTenkanCrossesKijunDownRule();
        Rule trendBearishConfirmed= ichimokuRules.getTrendBearishConfirmed();
        Rule cloudBearish = ichimokuRules.getCloudBearish();


        //Candle
        Rule shortSignalsPrevail = candlesRules.getShortSignalsPrevailRule();

        Rule entryRule = cloudBearish.and(priceUnderCloud).and(candlesRules.getCustomPriceActionShortRule()).and(shortSignalsPrevail);
        //Rule entryRule = trendBearishConfirmed.and(tenkanUnderCloud).and(tenkanCrossesKijunDown.or(macdEntry));
		Rule exitRule = new BooleanRule(false);

		return new BaseStrategy(entryRule, exitRule);
	}

	//
	public static double assessStrategyStrength(OrderType orderType, BaseBarSeries series) {

		CandlesRulesBuilder candlesRules = new CandlesRulesBuilder(series);
		Rule strategyStrong = orderType == OrderType.BUY ? candlesRules.getLongSignalsPrevailRule() : candlesRules.getShortSignalsPrevailRule();
		Rule strategyWeak = orderType == OrderType.BUY ? candlesRules.getShortSignalsPrevailRule() : candlesRules.getLongSignalsPrevailRule();

		if(strategyStrong.isSatisfied(series.getEndIndex())) {
			return 1.5;
		}
		else if(strategyWeak.isSatisfied(series.getEndIndex())) {
			return 0.5;
		}

		return 1.0;
	}
}
