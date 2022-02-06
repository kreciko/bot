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

public class LongStrategyBuilder extends StrategyBuilder {

    private Rule stopLossNotExceedingBounds;
    private Rule longSignalsPrevail;


    public LongStrategyBuilder(BaseBarSeries series, BaseBarSeries parentSeries, Indicator stopLossStrategy) {
        super(series,parentSeries);

        this.tradeType = TradeType.BUY;
        this.typeOfOperation = 0;
        this.stopLossStrategy = stopLossStrategy;

        this.stopLossNotExceedingBounds = new IsEqualRule(
                new StopLossIndicator(stopLossStrategy, series, Trade.TradeType.BUY, Configuration.stopLossMaxATR, Configuration.stopLossMinATR), DoubleNum.valueOf(0)).negation();
        this.longSignalsPrevail = priceActionRules.getLongSignalsPrevailRule(1);
    }

    @Override
    public Strategy buildMACDStrategy() {
        MACDIndicators macdInd = new MACDIndicators(series, parentSeries);
        Rule macdEntry = new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartTrendLine200())
                .and(new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartParentTrendLine50()))
                .and(new CrossedUpIndicatorRule(macdInd.getMacd(), macdInd.getSignal()))
                .and(new UnderIndicatorRule(macdInd.getMacd(), DoubleNum.valueOf(0)))
                .and(priceActionRules.getPriceActionNotTooDynamic())
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
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(new OverIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTrendLine200()))
                .and(new OverIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTenkanSen()));
        return new BaseStrategy("Ichimoku", ichimokuEntry, new BooleanRule(false));
    }

    @Override
    public Strategy buildDonchianStrategy() {
        DonchianIndicators donchianInd = new DonchianIndicators(series, parentSeries);
        Rule wasLowerDFallingInTheMeantime =  new OverIndicatorRule(
                new SatisfiedCountIndicator(donchianInd.getIsLowerDFalling(), donchianInd.getUpperDFallingCount()), 0);

        Rule donchianEntry = new OverIndicatorRule(donchianInd.getClosePrice(), donchianInd.getSmartTrendLine200())
                .and(new OverIndicatorRule(donchianInd.getClosePrice(), donchianInd.getSmartParentTrendLine200()))
                .and(new BooleanIndicatorRule(donchianInd.getWasUpperDFalling()))
                .and(new BooleanIndicatorRule(donchianInd.getIsUpperDRising()))
                .and(wasLowerDFallingInTheMeantime)
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("Donchian", donchianEntry , new BooleanRule(false));
    }

    @Override
    public Strategy buildPriceActionStrategy() {
        GeneralIndicators genInd = new GeneralIndicators(series, parentSeries);
        Rule priceActionEntry = new OverIndicatorRule(genInd.getClosePrice(), genInd.getSmartTrendLine200())
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(priceActionRules.getMarketNotChoppy())
                .and(priceActionRules.getLongSignalsPrevailRule(3))
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("PriceAction", priceActionEntry , new BooleanRule(false));
    }


    public double assessStrategyStrength() {
        Rule strategyStrong = priceActionRules.getLongSignalsPrevailRule(2);
        Rule strategyWeak = priceActionRules.getShortSignalsPrevailRule(2);

        return assessStrategyStrength(strategyStrong, strategyWeak);
    }


}
