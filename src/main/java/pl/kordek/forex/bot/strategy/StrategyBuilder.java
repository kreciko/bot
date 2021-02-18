package pl.kordek.forex.bot.strategy;

import java.util.ArrayList;
import java.util.List;

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
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
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
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;


public class StrategyBuilder {

	private ClosePriceIndicator closePrice;
	private PreviousValueIndicator<Num> prevClosePrice;
	private EMAIndicator trendLine;

	private IchimokuRules ichimokuRules;
	private CandlesRules candlesRules;

	private MACDIndicator macd;
	private EMAIndicator signal;

	private DonchianChannelLowerIndicator donchianLower;
	private DonchianChannelUpperIndicator donchianUpper;



	public StrategyBuilder(int index, BaseBarSeries series, BaseBarSeries helperSeries) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}

		this.closePrice = new ClosePriceIndicator(series);
		this.prevClosePrice = new PreviousValueIndicator<>(closePrice);
		this.trendLine = new EMAIndicator(closePrice, 200);
		this.ichimokuRules = new IchimokuRules(index, series, helperSeries);
		this.candlesRules = new CandlesRules(series);
		this.macd = new MACDIndicator(closePrice, 12 , 26);
		this.signal = new EMAIndicator(macd, 9);
		this.donchianLower = new DonchianChannelLowerIndicator(series, 20);
		this.donchianUpper = new DonchianChannelUpperIndicator(series, 20);
	}

	public List<Strategy> buildLongStrategies() {
		//MACD Strategy
        Rule macdEntry = new OverIndicatorRule(closePrice, trendLine).and(new CrossedUpIndicatorRule(macd, signal))
                .and(new UnderIndicatorRule(macd, DoubleNum.valueOf(0)));

        //Donchian Strategy
        Rule donchianEntry = new OverIndicatorRule(closePrice, trendLine);

        //Ichimoku Strategy
        Rule priceOverCloud = ichimokuRules.getPriceOverCloud();
        Rule tenkanCrossesKijunUp = ichimokuRules.getTenkanCrossesKijunUpRule();
        Rule cloudBullish = ichimokuRules.getCloudBullish();
        Rule ichimokuEntry = cloudBullish.and(priceOverCloud).and(tenkanCrossesKijunUp)
        		.and(new OverIndicatorRule(closePrice, trendLine)).and(new OverIndicatorRule(closePrice, ichimokuRules.getTenkanSen()));


        List<Strategy> strategies = new ArrayList<>();

        strategies.add(new BaseStrategy("Ichimoku", ichimokuEntry, new BooleanRule(false)));
        strategies.add(new BaseStrategy("MACD", macdEntry, new BooleanRule(false)));


		return strategies;
	}

	public List<Strategy> buildShortStrategies() {
        //MACD Strategy
		Rule macdEntry = new UnderIndicatorRule(closePrice, trendLine).and(new CrossedDownIndicatorRule(macd, signal))
                .and(new OverIndicatorRule(macd, DoubleNum.valueOf(0)));

        //Ichimoku Strategy
        Rule priceUnderCloud = ichimokuRules.getPriceUnderCloud();
        Rule tenkanCrossesKijunDown = ichimokuRules.getTenkanCrossesKijunDownRule();
        Rule cloudBearish = ichimokuRules.getCloudBearish();
        Rule ichimokuEntry = cloudBearish.and(priceUnderCloud).and(tenkanCrossesKijunDown)
        		.and(new UnderIndicatorRule(closePrice, trendLine)).and(new UnderIndicatorRule(closePrice, ichimokuRules.getTenkanSen()));

		List<Strategy> strategies = new ArrayList<>();

        strategies.add(new BaseStrategy("Ichimoku", ichimokuEntry, new BooleanRule(false)));
        strategies.add(new BaseStrategy("MACD", macdEntry, new BooleanRule(false)));

		return strategies;
	}


	//
	public static double assessStrategyStrength(TRADE_OPERATION_CODE operationCode, BaseBarSeries series) {

		CandlesRules candlesRules = new CandlesRules(series);
		Rule strategyStrong = operationCode == TRADE_OPERATION_CODE.BUY ? candlesRules.getLongSignalsPrevailRule(2) : candlesRules.getShortSignalsPrevailRule(2);
		Rule strategyWeak = operationCode == TRADE_OPERATION_CODE.BUY ? candlesRules.getShortSignalsPrevailRule(2) : candlesRules.getLongSignalsPrevailRule(2);

		if(strategyStrong.isSatisfied(series.getEndIndex())) {
			return 1.5;
		}
		else if(strategyWeak.isSatisfied(series.getEndIndex())) {
			return 0.5;
		}

		return 1.0;
	}
}
