package pl.kordek.forex.bot.strategy;

import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.SatisfiedCountIndicator;
import org.ta4j.core.indicators.helpers.StopLossIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.rules.*;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.indicator.*;
import pl.kordek.forex.bot.rules.IchimokuRules;
import pl.kordek.forex.bot.rules.PriceActionRules;

public class ShortStrategyBuilder extends StrategyBuilder {
    private Rule stopLossNotExceedingBounds;
    private Rule shortSignalsPrevail;
    private final int rsiStrong = 30;

    public ShortStrategyBuilder(BaseBarSeries series, BaseBarSeries parentSeries, Indicator stopLossStrategy, Boolean shouldCloseOnStrongRSI) {
        super(series,parentSeries);

        this.tradeType = TradeType.SELL;
        this.typeOfOperation = 0;
        this.stopLossStrategy = stopLossStrategy;

        this.stopLossNotExceedingBounds = new IsEqualRule(
                new StopLossIndicator(stopLossStrategy, series, Trade.TradeType.SELL, Configuration.stopLossMaxATR, Configuration.stopLossMinATR), DoubleNum.valueOf(0)).negation();
        this.shortSignalsPrevail = priceActionRules.getShortSignalsPrevailRule(1);

        this.exitRule = new BooleanRule(shouldCloseOnStrongRSI).and(new UnderIndicatorRule(generalIndicators.getRsi(), 30));

    }

    @Override
    public Strategy buildMACDStrategy() {
        MACDIndicators macdInd = new MACDIndicators(series, parentSeries);
        Rule macdEntry = new UnderIndicatorRule(macdInd.getClosePrice(), macdInd.getSmartTrendLine200())
                .and(new UnderIndicatorRule(macdInd.getClosePriceParentInd(), macdInd.getSmartParentTrendLine50()))
                .and(new CrossedDownIndicatorRule(macdInd.getMacd(), macdInd.getSignal()))
                .and(new OverIndicatorRule(macdInd.getMacd(), DoubleNum.valueOf(0)))
                .and(priceActionRules.getShortSignalsPrevailRule(0))
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("MACD", macdEntry, exitRule);
    }

    @Override
    public Strategy buildIchimokuStrategy(int checkIndexForChikou) {
        IchimokuIndicators ichimokuInd = new IchimokuIndicators(series, parentSeries);
        IchimokuRules ichimokuRules = new IchimokuRules(checkIndexForChikou, series, parentSeries);

        Rule priceUnderCloud = ichimokuRules.getPriceUnderCloud();
        Rule tenkanUnderCloud = ichimokuRules.getTenkanSenUnderCloud();
        Rule tenkanCrossesKijunDown = ichimokuRules.getTenkanCrossesKijunDownRule();
        Rule cloudBearish = ichimokuRules.getCloudBearish();
        Rule priceUnderTenkan = new UnderIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTenkanSen());

        Rule ichimokuEntry = new UnderIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTrendLine200())
                .and(cloudBearish)
                .and(priceUnderCloud)
                .and(tenkanUnderCloud)
                .and(tenkanCrossesKijunDown)
                .and(priceUnderTenkan)
                .and(priceActionRules.getLongSignalsPrevailRule(1))
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("Ichimoku", ichimokuEntry, exitRule);
    }

    @Override
    public Strategy buildDonchianStrategy() {
        DonchianIndicators donchianInd = new DonchianIndicators(series, parentSeries);
        Rule wasUpperDRisingInTheMeantime =  new OverIndicatorRule(
                new SatisfiedCountIndicator(donchianInd.getIsUpperDRising(), donchianInd.getLowerDRisingCount()), 0);

        Rule donchianEntry = new UnderIndicatorRule(donchianInd.getClosePrice(), donchianInd.getSmartTrendLine200())
                .and(new UnderIndicatorRule(donchianInd.getClosePriceParentInd(), donchianInd.getSmartParentTrendLine50()))
                .and(new BooleanIndicatorRule(donchianInd.getWasLowerDRising()))
                .and(new BooleanIndicatorRule(donchianInd.getIsLowerDFalling()))
                .and(wasUpperDRisingInTheMeantime)
                .and(priceActionRules.getShortSignalsPrevailRule(0))
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("Donchian", donchianEntry , exitRule);
    }

    @Override
    public Strategy buildPriceActionStrategy() {
        GeneralIndicators genInd = new GeneralIndicators(series, parentSeries);
        Rule priceActionEntry = new UnderIndicatorRule(genInd.getClosePrice(), genInd.getSmartTrendLine200())
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(priceActionRules.getMarketNotChoppy())
                .and(priceActionRules.getShortSignalsPrevailRule(3))
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("PriceAction", priceActionEntry , exitRule);
    }

    @Override
    public Strategy buildBollingerBandsStrategy() {
        GeneralIndicators genInd = new GeneralIndicators(series, parentSeries);
        BollingerBandsIndicators bbandInd = new BollingerBandsIndicators(series, parentSeries);

        Rule bbEntry = new UnderIndicatorRule(genInd.getClosePrice(), genInd.getSmartTrendLine200())
                .and(new UnderIndicatorRule(genInd.getClosePriceParentInd(), genInd.getSmartParentTrendLine50()))
                .and(new CrossedUpIndicatorRule(genInd.getClosePrice(), bbandInd.getUpBBand()))
                .and(priceActionRules.getShortSignalsPrevailRule(0))
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(stopLossNotExceedingBounds);


        return new BaseStrategy("BollingerBands", bbEntry , exitRule);
    }

    public double assessStrategyStrength() {
        Rule strategyStrong = priceActionRules.getShortSignalsPrevailRule(2);
        Rule strategyWeak = priceActionRules.getLongSignalsPrevailRule(2);
        return assessStrategyStrength(strategyStrong, strategyWeak);
    }
}
