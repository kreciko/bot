package pl.kordek.forex.bot.strategy;

import org.ta4j.core.*;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.helpers.SatisfiedCountIndicator;
import org.ta4j.core.indicators.helpers.StopLossIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.trading.rules.*;
import pl.kordek.forex.bot.indicator.DonchianIndicators;
import pl.kordek.forex.bot.indicator.GeneralIndicators;
import pl.kordek.forex.bot.indicator.IchimokuIndicators;
import pl.kordek.forex.bot.indicator.MACDIndicators;
import pl.kordek.forex.bot.rules.IchimokuRules;

public class LongStrategyBuilder extends StrategyBuilder {

    private Rule stopLossNotExceedingBounds;
    private Rule shortSignalsDontPrevail;


    public LongStrategyBuilder(BaseBarSeries series, BaseBarSeries parentSeries, Indicator stopLossStrategy) {
        super(series,parentSeries);

        this.orderType = OrderType.BUY;
        this.typeOfOperation = 0;

        this.stopLossNotExceedingBounds = new IsEqualRule(
                new StopLossIndicator(stopLossStrategy, series, Order.OrderType.BUY, 5, 2), DoubleNum.valueOf(0)).negation();
        this.shortSignalsDontPrevail = priceActionRules.getShortSignalsPrevailRule(1).negation();
    }

    @Override
    public Strategy buildMACDStrategy() {
        MACDIndicators macdInd = new MACDIndicators(series, parentSeries);
        Rule macdEntry = new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getTrendLine200())
                .and(new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartParentTrendLine50()))
                .and(new CrossedUpIndicatorRule(macdInd.getMacd(), macdInd.getSignal()))
                .and(new UnderIndicatorRule(macdInd.getMacd(), DoubleNum.valueOf(0)))
                .and(shortSignalsDontPrevail)
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("MACD", macdEntry, new BooleanRule(false));
    }

    @Override
    public Strategy buildIchimokuStrategy(int checkIndexForChikou) {
        IchimokuIndicators ichimokuInd = new IchimokuIndicators(series, parentSeries);
        IchimokuRules ichimokuRules = new IchimokuRules(checkIndexForChikou, series, parentSeries);

        Rule priceOverCloud = ichimokuRules.getPriceOverCloud();
        Rule tenkanOverCloud = ichimokuRules.getTenkanSenOverCloud();
        Rule tenkanCrossesKijunUp = ichimokuRules.getTenkanCrossesKijunUpRule();
        Rule cloudBullish = ichimokuRules.getCloudBullish();
        Rule ichimokuEntry = cloudBullish
                .and(priceOverCloud)
                .and(tenkanOverCloud)
                .and(tenkanCrossesKijunUp)
                .and(new OverIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTenkanSen()));
        return new BaseStrategy("Ichimoku", ichimokuEntry, new BooleanRule(false));
    }

    @Override
    public Strategy buildDonchianStrategy() {
        DonchianIndicators donchianInd = new DonchianIndicators(series, parentSeries);
        Rule wasLowerDFallingInTheMeantime =  new OverIndicatorRule(
                new SatisfiedCountIndicator(donchianInd.getIsLowerDFalling(), donchianInd.getUpperDFallingCount()), 0);

        Rule donchianEntry = new OverIndicatorRule(donchianInd.getClosePrice(), donchianInd.getSmartTrendLine50())
                .and(new BooleanIndicatorRule(donchianInd.getWasUpperDFalling()))
                .and(new BooleanIndicatorRule(donchianInd.getIsUpperDRising()))
                .and(wasLowerDFallingInTheMeantime).and(shortSignalsDontPrevail)
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("Donchian", donchianEntry , new BooleanRule(false));
    }

    @Override
    public Strategy buildPriceActionStrategy() {
        GeneralIndicators genInd = new GeneralIndicators(series, parentSeries);
        Rule priceActionEntry = priceActionRules.getCustomPriceActionLongRule()
                .and(new OverIndicatorRule(genInd.getClosePrice(), genInd.getSmartTrendLine50()))
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("PriceAction", priceActionEntry , new BooleanRule(false));
    }


    public double assessStrategyStrength() {
        Rule strategyStrong = priceActionRules.getLongSignalsPrevailRule(2);
        Rule strategyWeak = priceActionRules.getShortSignalsPrevailRule(2);

        return assessStrategyStrength(strategyStrong, strategyWeak);
    }


}
