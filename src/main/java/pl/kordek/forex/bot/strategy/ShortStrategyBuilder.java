package pl.kordek.forex.bot.strategy;

import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.SatisfiedCountIndicator;
import org.ta4j.core.indicators.helpers.StopLossIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.rules.*;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.indicator.DonchianIndicators;
import pl.kordek.forex.bot.indicator.GeneralIndicators;
import pl.kordek.forex.bot.indicator.IchimokuIndicators;
import pl.kordek.forex.bot.indicator.MACDIndicators;
import pl.kordek.forex.bot.rules.IchimokuRules;

public class ShortStrategyBuilder extends StrategyBuilder {
    private Rule stopLossNotExceedingBounds;
    private Rule shortSignalsPrevail;


    public ShortStrategyBuilder(BaseBarSeries series, BaseBarSeries parentSeries, Indicator stopLossStrategy) {
        super(series,parentSeries);

        this.tradeType = TradeType.SELL;
        this.typeOfOperation = 0;
        this.stopLossStrategy = stopLossStrategy;

        this.stopLossNotExceedingBounds = new IsEqualRule(
                new StopLossIndicator(stopLossStrategy, series, Trade.TradeType.SELL, Configuration.stopLossMaxATR, Configuration.stopLossMinATR), DoubleNum.valueOf(0)).negation();
        this.shortSignalsPrevail = priceActionRules.getShortSignalsPrevailRule(1);
    }

    @Override
    public Strategy buildMACDStrategy() {
        MACDIndicators macdInd = new MACDIndicators(series, parentSeries);
        Rule macdEntry = new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartTrendLine200())
                .and(new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartParentTrendLine50()))
                .and(new CrossedDownIndicatorRule(macdInd.getMacd(), macdInd.getSignal()))
                .and(new OverIndicatorRule(macdInd.getMacd(), DoubleNum.valueOf(0)))
                .and(priceActionRules.getPriceActionNotTooDynamic())
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
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(new UnderIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTrendLine200()))
                .and(new UnderIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTenkanSen()));

        return new BaseStrategy("Ichimoku", ichimokuEntry, new BooleanRule(false));
    }

    @Override
    public Strategy buildDonchianStrategy() {
        DonchianIndicators donchianInd = new DonchianIndicators(series, parentSeries);
        Rule wasUpperDRisingInTheMeantime =  new OverIndicatorRule(
                new SatisfiedCountIndicator(donchianInd.getIsUpperDRising(), donchianInd.getLowerDRisingCount()), 0);

        Rule donchianEntry = new UnderIndicatorRule(donchianInd.getClosePrice(), donchianInd.getSmartTrendLine200())
                .and(new UnderIndicatorRule(donchianInd.getClosePrice(), donchianInd.getSmartParentTrendLine200()))
                .and(new BooleanIndicatorRule(donchianInd.getWasLowerDRising()))
                .and(new BooleanIndicatorRule(donchianInd.getIsLowerDFalling()))
                .and(wasUpperDRisingInTheMeantime)
                .and(stopLossNotExceedingBounds);

        return new BaseStrategy("Donchian", donchianEntry , new BooleanRule(false));
    }

    @Override
    public Strategy buildPriceActionStrategy() {
        GeneralIndicators genInd = new GeneralIndicators(series, parentSeries);
        Rule priceActionEntry = new UnderIndicatorRule(genInd.getClosePrice(), genInd.getSmartTrendLine200())
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(priceActionRules.getMarketNotChoppy())
                .and(priceActionRules.getShortSignalsPrevailRule(3))
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("PriceAction", priceActionEntry , new BooleanRule(false));
    }

    public double assessStrategyStrength() {
        Rule strategyStrong = priceActionRules.getShortSignalsPrevailRule(2);
        Rule strategyWeak = priceActionRules.getLongSignalsPrevailRule(2);
        return assessStrategyStrength(strategyStrong, strategyWeak);
    }
}
