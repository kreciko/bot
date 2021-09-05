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

public class ShortStrategyBuilder extends StrategyBuilder {
    private Rule stopLossNotExceedingBounds;
    private Rule longSignalsDontPrevail;

    public ShortStrategyBuilder(BaseBarSeries series, BaseBarSeries parentSeries, Indicator stopLossStrategy) {
        super(series,parentSeries);

        this.orderType = OrderType.SELL;
        this.typeOfOperation = 0;

        this.stopLossNotExceedingBounds = new IsEqualRule(
                new StopLossIndicator(stopLossStrategy, series, Order.OrderType.SELL, 5, 2), DoubleNum.valueOf(0)).negation();
        this.longSignalsDontPrevail = priceActionRules.getLongSignalsPrevailRule(1).negation();
    }

    @Override
    public Strategy buildMACDStrategy() {
        MACDIndicators macdInd = new MACDIndicators(series, parentSeries);
        Rule macdEntry = new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartTrendLine200())
                .and(new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartParentTrendLine50()))
                .and(new CrossedDownIndicatorRule(macdInd.getMacd(), macdInd.getSignal()))
                .and(new OverIndicatorRule(macdInd.getMacd(), DoubleNum.valueOf(0)))
                .and(longSignalsDontPrevail)
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("MACD", macdEntry, new BooleanRule(false));
    }

    @Override
    public Strategy buildIchimokuStrategy(int checkIndexForChikou) {
        IchimokuIndicators ichimokuInd = new IchimokuIndicators(series, parentSeries);
        IchimokuRules ichimokuRules = new IchimokuRules(checkIndexForChikou, series, parentSeries);

        Rule priceUnderCloud = ichimokuRules.getPriceUnderCloud();
        Rule tenkanUnderCloud = ichimokuRules.getTenkanSenUnderCloud();
        Rule tenkanCrossesKijunDown = ichimokuRules.getTenkanCrossesKijunDownRule();
        Rule cloudBearish = ichimokuRules.getCloudBearish();
        Rule ichimokuEntry = cloudBearish
                .and(priceUnderCloud)
                .and(tenkanUnderCloud)
                .and(tenkanCrossesKijunDown)
                .and(new UnderIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTenkanSen()));

        return new BaseStrategy("Ichimoku", ichimokuEntry, new BooleanRule(false));
    }

    @Override
    public Strategy buildDonchianStrategy() {
        DonchianIndicators donchianInd = new DonchianIndicators(series, parentSeries);
        Rule wasUpperDRisingInTheMeantime =  new OverIndicatorRule(
                new SatisfiedCountIndicator(donchianInd.getIsUpperDRising(), donchianInd.getLowerDRisingCount()), 0);

        Rule donchianEntry = new UnderIndicatorRule(donchianInd.getClosePrice(), donchianInd.getSmartTrendLine50())
                .and(new BooleanIndicatorRule(donchianInd.getWasLowerDRising()))
                .and(new BooleanIndicatorRule(donchianInd.getIsLowerDFalling()))
                .and(wasUpperDRisingInTheMeantime).and(longSignalsDontPrevail)
                .and(stopLossNotExceedingBounds);

        return new BaseStrategy("Donchian", donchianEntry , new BooleanRule(false));
    }

    @Override
    public Strategy buildPriceActionStrategy() {
        GeneralIndicators genInd = new GeneralIndicators(series, parentSeries);

        Rule priceActionEntry = priceActionRules.getCustomPriceActionShortRule()
                .and(new UnderIndicatorRule(genInd.getClosePrice(), genInd.getSmartTrendLine50()))
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("PriceAction", priceActionEntry , new BooleanRule(false));
    }

    public double assessStrategyStrength() {
        Rule strategyStrong = priceActionRules.getShortSignalsPrevailRule(2);
        Rule strategyWeak = priceActionRules.getLongSignalsPrevailRule(2);
        return assessStrategyStrength(strategyStrong, strategyWeak);
    }
}
