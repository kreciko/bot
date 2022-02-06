package pl.kordek.forex.bot.strategy;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
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
import org.ta4j.core.rules.*;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.rules.PriceActionRules;
import pl.kordek.forex.bot.rules.IchimokuRules;


public class StrategyBuilderOld {

	private BaseBarSeries series;
	private ClosePriceIndicator closePrice;
	private PreviousValueIndicator<Num> prevClosePrice;
	private EMAIndicator trendLine200;
	private EMASmartIndicator smartTrendLine50;
	private EMASmartIndicator smartTrendLine200;
	private ParentIndicator smartParentTrendLine50;

	private IchimokuRules ichimokuRules;
	private PriceActionRules priceActionRules;

	private MACDIndicator macd;
	private EMAIndicator signal;

	private DonchianChannelLowerIndicator donchianLower;
	private DonchianChannelUpperIndicator donchianUpper;

	private RSIIndicator rsi;




	public StrategyBuilderOld(int index, BaseBarSeries series, BaseBarSeries helperSeries) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}

		this.series = series;
		this.closePrice = new ClosePriceIndicator(series);
		this.prevClosePrice = new PreviousValueIndicator<>(closePrice);
		this.trendLine200 = new EMAIndicator(closePrice, 200);
		this.ichimokuRules = new IchimokuRules(index, series, helperSeries);
		this.priceActionRules = new PriceActionRules(series, helperSeries);
		this.macd = new MACDIndicator(closePrice, 12 , 26);
		this.signal = new EMAIndicator(macd, 9);
		this.donchianLower = new DonchianChannelLowerIndicator(series, 20);
		this.donchianUpper = new DonchianChannelUpperIndicator(series, 20);
		this.rsi = new RSIIndicator(closePrice, 14);
		this.smartTrendLine50 = new EMASmartIndicator(closePrice, 50);
		this.smartTrendLine200 = new EMASmartIndicator(closePrice, 200);
		ClosePriceIndicator parentClosePrice = new ClosePriceIndicator(helperSeries);
		this.smartParentTrendLine50 =
				new ParentIndicator(new EMAIndicator(parentClosePrice, 50), 4);


	}

	public List<Strategy> buildLongStrategies() {
        Rule shortSignalsDontPrevail = priceActionRules.getShortSignalsPrevailRule(1).negation();
		Rule stopLossNotExeedingBounds = new IsEqualRule(
				new StopLossIndicator(donchianLower, series, TradeType.BUY, 5, 2), DoubleNum.valueOf(0)).negation();

		//MACD Strategy
        Rule macdEntry = new OverIndicatorRule(closePrice, trendLine200)
				.and(new OverIndicatorRule(closePrice, smartParentTrendLine50))
				.and(new CrossedUpIndicatorRule(macd, signal))
                .and(new UnderIndicatorRule(macd, DoubleNum.valueOf(0)))
				.and(shortSignalsDontPrevail)
				.and(stopLossNotExeedingBounds);


        //Ichimoku Strategy
        Rule priceOverCloud = ichimokuRules.getPriceOverCloud();
        Rule tenkanOverCloud = ichimokuRules.getTenkanSenOverCloud();
        Rule tenkanCrossesKijunUp = ichimokuRules.getTenkanCrossesKijunUpRule();
        Rule cloudBullish = ichimokuRules.getCloudBullish();
        Rule ichimokuEntry = cloudBullish
				.and(priceOverCloud)
				.and(tenkanOverCloud)
				.and(tenkanCrossesKijunUp)
				.and(new OverIndicatorRule(closePrice, ichimokuRules.getIchimokuInd().getTenkanSen()));


        //RSI Strategy
        Rule rsiEntry = new CrossedDownIndicatorRule(rsi, DoubleNum.valueOf(35))
				.and(new OverIndicatorRule(closePrice, smartTrendLine200))
				.and(new OverIndicatorRule(closePrice, smartParentTrendLine50))
				.and(shortSignalsDontPrevail)
				.and(stopLossNotExeedingBounds);

        //Price action Strategy
		Rule priceActionEntry = priceActionRules.getCustomPriceActionLongRule()
				.and(new OverIndicatorRule(closePrice, smartTrendLine50))
				.and(stopLossNotExeedingBounds);

		Rule donchianEntry = createDonchianEntry(TradeType.BUY);

		List<Strategy> strategies = getStrategies(macdEntry, rsiEntry, priceActionEntry, donchianEntry, ichimokuEntry);

		return strategies;
	}

	public List<Strategy> buildShortStrategies() {
        Rule longSignalsDontPrevail = priceActionRules.getLongSignalsPrevailRule(1).negation();
		Rule stopLossNotExceedingBounds = new IsEqualRule(
				new StopLossIndicator(donchianUpper, series, TradeType.SELL, 5, 2), DoubleNum.valueOf(0)).negation();


		//MACD Strategy
		Rule macdEntry = new UnderIndicatorRule(closePrice, trendLine200)
				.and(new UnderIndicatorRule(closePrice, smartParentTrendLine50))
				.and(new CrossedDownIndicatorRule(macd, signal))
                .and(new OverIndicatorRule(macd, DoubleNum.valueOf(0)))
				.and(longSignalsDontPrevail)
				.and(stopLossNotExceedingBounds);

        //Ichimoku Strategy
        Rule priceUnderCloud = ichimokuRules.getPriceUnderCloud();
        Rule tenkanUnderCloud = ichimokuRules.getTenkanSenUnderCloud();
        Rule tenkanCrossesKijunDown = ichimokuRules.getTenkanCrossesKijunDownRule();
        Rule cloudBearish = ichimokuRules.getCloudBearish();
        Rule ichimokuEntry = cloudBearish
				.and(priceUnderCloud)
				.and(tenkanUnderCloud)
				.and(tenkanCrossesKijunDown)
				.and(new UnderIndicatorRule(closePrice, ichimokuRules.getIchimokuInd().getTenkanSen()));

        //RSI Strategy
        Rule rsiEntry = new CrossedUpIndicatorRule(rsi, DoubleNum.valueOf(65))
				.and(new UnderIndicatorRule(closePrice, smartTrendLine200))
				.and(new UnderIndicatorRule(closePrice, smartParentTrendLine50))
				.and(longSignalsDontPrevail)
				.and(stopLossNotExceedingBounds);

		//Price action Strategy
		Rule priceActionEntry = priceActionRules.getCustomPriceActionShortRule()
				.and(new UnderIndicatorRule(closePrice, smartTrendLine50))
				.and(stopLossNotExceedingBounds);;

		Rule donchianEntry = createDonchianEntry(TradeType.SELL);

		List<Strategy> strategies = getStrategies(macdEntry, rsiEntry, priceActionEntry, donchianEntry, ichimokuEntry);

		return strategies;
	}

	private List<Strategy> getStrategies(Rule macdEntry, Rule rsiEntry, Rule priceActionEntry, Rule donchianEntry, Rule ichimokuEntry) {
		List<Strategy> strategies = new ArrayList<>();

		//strategies.add(new BaseStrategy("RSI", rsiEntry, new BooleanRule(false)));
		strategies.add(new BaseStrategy("MACD", macdEntry, new BooleanRule(false)));
		strategies.add(new BaseStrategy("PriceAction", priceActionEntry , new BooleanRule(false)));
		strategies.add(new BaseStrategy("Donchian", donchianEntry , new BooleanRule(false)));
		strategies.add(new BaseStrategy("Ichimoku", ichimokuEntry , new BooleanRule(false)));
		return strategies;
	}

	private Rule createDonchianEntry(TradeType tradeType) {
        Rule shortSignalsDontPrevail = priceActionRules.getShortSignalsPrevailRule(1).negation();
        Rule longSignalsDontPrevail = priceActionRules.getLongSignalsPrevailRule(1).negation();


		PreviousValueIndicator<Num> prevUpperD = new PreviousValueIndicator<>(donchianUpper);

		Indicator<Boolean> isUpperDRising = new DonchianIsRisingIndicator(donchianUpper);
		Indicator<Boolean> isUpperDFalling = new DonchianIsFallingIndicator(donchianUpper);
		Indicator<Boolean> isLowerDFalling = new DonchianIsFallingIndicator(donchianLower);
		Indicator<Boolean> isLowerDRising = new DonchianIsRisingIndicator(donchianLower);

		EMASmartIndicator smartTrendLine = new EMASmartIndicator(closePrice, 50);

		Indicator indForSL = tradeType == tradeType.BUY ? donchianLower : donchianUpper;

		Rule stopLossNotExceedingBounds = new IsEqualRule(
				new StopLossIndicator(indForSL, series, tradeType, 5, 2), DoubleNum.valueOf(0)).negation();

		Rule donchianEntry = null;
		if(tradeType == tradeType.BUY) {
			PreviousValueIndicator<Boolean> wasUpperDFalling = new PreviousValueIndicator<>(isUpperDFalling);
			DonchianFallingBarCountIndicator upperDFallingCount = new DonchianFallingBarCountIndicator(prevUpperD);
			Rule wasLowerDFallingInTheMeantime =  new OverIndicatorRule(new SatisfiedCountIndicator(isLowerDFalling, upperDFallingCount), 0);


			donchianEntry = new OverIndicatorRule(closePrice, smartTrendLine)
					.and(new BooleanIndicatorRule(wasUpperDFalling))
					.and(new BooleanIndicatorRule(isUpperDRising))
					.and(wasLowerDFallingInTheMeantime)
					.and(shortSignalsDontPrevail)
					.and(stopLossNotExceedingBounds);
		}
		else {
			PreviousValueIndicator<Boolean> wasLowerDRising = new PreviousValueIndicator<>(isLowerDRising);
			DonchianRisingBarCountIndicator lowerDRisingCount = new DonchianRisingBarCountIndicator(prevUpperD);
			Rule wasUpperDRisingInTheMeantime =  new OverIndicatorRule(new SatisfiedCountIndicator(isUpperDRising, lowerDRisingCount), 0);

			donchianEntry = new UnderIndicatorRule(closePrice, smartTrendLine)
					.and(new BooleanIndicatorRule(wasLowerDRising))
					.and(new BooleanIndicatorRule(isLowerDFalling))
					.and(wasUpperDRisingInTheMeantime).and(longSignalsDontPrevail)
					.and(stopLossNotExceedingBounds);
		}

        return donchianEntry;
    }



	//
	public static double assessStrategyStrength(TradeType tradeType, BaseBarSeries series, BaseBarSeries parentSeries) {

		PriceActionRules priceActionRules = new PriceActionRules(series, parentSeries);
		Rule strategyStrong = tradeType == TradeType.BUY ? priceActionRules.getLongSignalsPrevailRule(2) : priceActionRules.getShortSignalsPrevailRule(2);
		Rule strategyWeak = tradeType == TradeType.BUY ? priceActionRules.getShortSignalsPrevailRule(2) : priceActionRules.getLongSignalsPrevailRule(2);
		Double strategyStrength = 1.0;


		if(strategyStrong.isSatisfied(series.getEndIndex())) {
			strategyStrength = 1.5;
		}
		else if(strategyWeak.isSatisfied(series.getEndIndex())) {
			strategyStrength = 0.5;
		}

		System.out.println(new Date() + ":Calculated strategy strength: "+strategyStrength);

		if(!Configuration.considerStratetyStrength) {
			strategyStrength = 1.0;
		}

		return strategyStrength;
	}
}
