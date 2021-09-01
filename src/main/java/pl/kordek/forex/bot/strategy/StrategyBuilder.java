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
import org.ta4j.core.indicators.EMASmartIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.donchian.DonchianFallingBarCountIndicator;
import org.ta4j.core.indicators.donchian.DonchianIsFallingIndicator;
import org.ta4j.core.indicators.donchian.DonchianIsRisingIndicator;
import org.ta4j.core.indicators.donchian.DonchianRisingBarCountIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import pl.kordek.forex.bot.rules.CandlesRules;
import pl.kordek.forex.bot.rules.IchimokuRules;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;


public class StrategyBuilder {

	private BaseBarSeries series;
	private ClosePriceIndicator closePrice;
	private PreviousValueIndicator<Num> prevClosePrice;
	private EMAIndicator trendLine200;
	private EMASmartIndicator smartTrendLine200;
	private ParentIndicator smartParentTrendLine50;

	private IchimokuRules ichimokuRules;
	private CandlesRules candlesRules;

	private MACDIndicator macd;
	private EMAIndicator signal;

	private DonchianChannelLowerIndicator donchianLower;
	private DonchianChannelUpperIndicator donchianUpper;

	private RSIIndicator rsi;




	public StrategyBuilder(int index, BaseBarSeries series, BaseBarSeries helperSeries) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}

		this.series = series;
		this.closePrice = new ClosePriceIndicator(series);
		this.prevClosePrice = new PreviousValueIndicator<>(closePrice);
		this.trendLine200 = new EMAIndicator(closePrice, 200);
		this.ichimokuRules = new IchimokuRules(index, series, helperSeries);
		this.candlesRules = new CandlesRules(series);
		this.macd = new MACDIndicator(closePrice, 12 , 26);
		this.signal = new EMAIndicator(macd, 9);
		this.donchianLower = new DonchianChannelLowerIndicator(series, 20);
		this.donchianUpper = new DonchianChannelUpperIndicator(series, 20);
		this.rsi = new RSIIndicator(closePrice, 14);
		this.smartTrendLine200 = new EMASmartIndicator(closePrice, 200);
		ClosePriceIndicator parentClosePrice = new ClosePriceIndicator(helperSeries);
		this.smartParentTrendLine50 =
				new ParentIndicator(new EMAIndicator(parentClosePrice, 50), 4);


	}

	public List<Strategy> buildLongStrategies() {
		CandlesRules candlesRules = new CandlesRules(series);
        Rule shortSignalsDontPrevail = candlesRules.getShortSignalsPrevailRule(1).negation();

		//MACD Strategy
        Rule macdEntry = new OverIndicatorRule(closePrice, trendLine200)
				.and(new OverIndicatorRule(closePrice, smartParentTrendLine50))
				.and(new CrossedUpIndicatorRule(macd, signal))
                .and(new UnderIndicatorRule(macd, DoubleNum.valueOf(0)))
				.and(shortSignalsDontPrevail);


        //Ichimoku Strategy
        Rule priceOverCloud = ichimokuRules.getPriceOverCloud();
        Rule tenkanOverCloud = ichimokuRules.getTenkanSenOverCloud();
        Rule tenkanCrossesKijunUp = ichimokuRules.getTenkanCrossesKijunUpRule();
        Rule cloudBullish = ichimokuRules.getCloudBullish();
        Rule ichimokuEntry = cloudBullish.and(priceOverCloud).and(tenkanOverCloud).and(tenkanCrossesKijunUp)
        		.and(new OverIndicatorRule(closePrice, trendLine200)).and(new OverIndicatorRule(closePrice, ichimokuRules.getTenkanSen()));


        //RSI Strategy
        Rule rsiEntry = new CrossedDownIndicatorRule(rsi, DoubleNum.valueOf(35))
				.and(new OverIndicatorRule(closePrice, smartTrendLine200))
				.and(new OverIndicatorRule(closePrice, smartParentTrendLine50))
				.and(shortSignalsDontPrevail);


        List<Strategy> strategies = new ArrayList<>();

        strategies.add(new BaseStrategy("RSI", ichimokuEntry, new BooleanRule(false)));
        strategies.add(new BaseStrategy("MACD", macdEntry, new BooleanRule(false)));
        strategies.add(createDonchianStrategy(OrderType.BUY));


		return strategies;
	}

	public List<Strategy> buildShortStrategies() {
		CandlesRules candlesRules = new CandlesRules(series);
        Rule longSignalsDontPrevail = candlesRules.getLongSignalsPrevailRule(1).negation();

        //MACD Strategy
		Rule macdEntry = new UnderIndicatorRule(closePrice, trendLine200)
				.and(new UnderIndicatorRule(closePrice, smartParentTrendLine50))
				.and(new CrossedDownIndicatorRule(macd, signal))
                .and(new OverIndicatorRule(macd, DoubleNum.valueOf(0)))
				.and(longSignalsDontPrevail);

        //Ichimoku Strategy
        Rule priceUnderCloud = ichimokuRules.getPriceUnderCloud();
        Rule tenkanUnderCloud = ichimokuRules.getTenkanSenUnderCloud();
        Rule tenkanCrossesKijunDown = ichimokuRules.getTenkanCrossesKijunDownRule();
        Rule cloudBearish = ichimokuRules.getCloudBearish();
        Rule ichimokuEntry = cloudBearish.and(priceUnderCloud).and(tenkanUnderCloud).and(tenkanCrossesKijunDown)
        		.and(new UnderIndicatorRule(closePrice, trendLine200))
				.and(new UnderIndicatorRule(closePrice, ichimokuRules.getTenkanSen()));

        //RSI Strategy
        Rule rsiEntry = new CrossedUpIndicatorRule(rsi, DoubleNum.valueOf(65))
				.and(new UnderIndicatorRule(closePrice, smartTrendLine200))
				.and(new UnderIndicatorRule(closePrice, smartParentTrendLine50))
				.and(longSignalsDontPrevail);

		List<Strategy> strategies = new ArrayList<>();

        strategies.add(new BaseStrategy("RSI", rsiEntry, new BooleanRule(false)));
        strategies.add(new BaseStrategy("MACD", macdEntry, new BooleanRule(false)));
        strategies.add(createDonchianStrategy(OrderType.SELL));

		return strategies;
	}

	private Strategy createDonchianStrategy(OrderType orderType) {
		CandlesRules candlesRules = new CandlesRules(series);
        Rule shortSignalsDontPrevail = candlesRules.getShortSignalsPrevailRule(1).negation();
        Rule longSignalsDontPrevail = candlesRules.getLongSignalsPrevailRule(1).negation();


		PreviousValueIndicator<Num> prevUpperD = new PreviousValueIndicator<>(donchianUpper);

		Indicator<Boolean> isUpperDRising = new DonchianIsRisingIndicator(donchianUpper);
		Indicator<Boolean> isUpperDFalling = new DonchianIsFallingIndicator(donchianUpper);
		Indicator<Boolean> isLowerDFalling = new DonchianIsFallingIndicator(donchianLower);
		Indicator<Boolean> isLowerDRising = new DonchianIsRisingIndicator(donchianLower);

		EMASmartIndicator smartTrendLine = new EMASmartIndicator(closePrice, 50);

		Rule donchianEntry = null;
		if(orderType == orderType.BUY) {
			PreviousValueIndicator<Boolean> wasUpperDFalling = new PreviousValueIndicator<>(isUpperDFalling);
			DonchianFallingBarCountIndicator upperDFallingCount = new DonchianFallingBarCountIndicator(prevUpperD);
			Rule wasLowerDFallingInTheMeantime =  new OverIndicatorRule(new SatisfiedCountIndicator(isLowerDFalling, upperDFallingCount), 0);


			donchianEntry = new OverIndicatorRule(closePrice, smartTrendLine)
					.and(new BooleanIndicatorRule(wasUpperDFalling))
					.and(new BooleanIndicatorRule(isUpperDRising))
					.and(wasLowerDFallingInTheMeantime).and(shortSignalsDontPrevail);
		}
		else {
			PreviousValueIndicator<Boolean> wasLowerDRising = new PreviousValueIndicator<>(isLowerDRising);
			DonchianRisingBarCountIndicator lowerDRisingCount = new DonchianRisingBarCountIndicator(prevUpperD);
			Rule wasUpperDRisingInTheMeantime =  new OverIndicatorRule(new SatisfiedCountIndicator(isUpperDRising, lowerDRisingCount), 0);

			donchianEntry = new UnderIndicatorRule(closePrice, smartTrendLine)
					.and(new BooleanIndicatorRule(wasLowerDRising))
					.and(new BooleanIndicatorRule(isLowerDFalling))
					.and(wasUpperDRisingInTheMeantime).and(longSignalsDontPrevail);
		}


        Rule entryRule = donchianEntry;
        Rule exitRule = new BooleanRule(false);

        return new BaseStrategy("Donchian", entryRule, exitRule);
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
