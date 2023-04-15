package pl.kordek.forex.bot.strategy;

import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.SatisfiedCountIndicator;
import org.ta4j.core.indicators.helpers.StopLossIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.rules.*;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.indicator.*;
import pl.kordek.forex.bot.rules.IchimokuRules;
import pl.kordek.forex.bot.rules.PriceActionRules;

public class LongStrategyBuilder extends StrategyBuilder {
    private Rule stopLossNotExceedingBounds;
    private Rule longSignalsPrevail;
    private final int rsiStrong = 70;



    public LongStrategyBuilder(BaseBarSeries series, BaseBarSeries parentSeries, Indicator stopLossStrategy, Boolean shouldCloseOnMaxPriceCrossingTP, Boolean shouldCloseOnStrongRSI) {
        super(series,parentSeries);

        this.tradeType = TradeType.BUY;
        this.typeOfOperation = 0;
        this.stopLossStrategy = stopLossStrategy;

        Indicator stoplossIndicator = new StopLossIndicator(stopLossStrategy, series, Trade.TradeType.BUY, Configuration.stopLossMaxATR, Configuration.stopLossMinATR);
        Rule stopGainBySL = new StopGainBySLIndRule(series, stoplossIndicator, Configuration.takeProfitVsStopLossCoeff);

        this.stopLossNotExceedingBounds = new IsEqualRule( stoplossIndicator, DoubleNum.valueOf(0)).negation();
        this.longSignalsPrevail = priceActionRules.getLongSignalsPrevailRule(1);

        Rule exitOnStrongRSIRule = new BooleanRule(shouldCloseOnStrongRSI).and(new OverIndicatorRule(generalIndicators.getRsi(), 75));

        //this is in case the high price crosses TP but the trade is not closed (cause spread)
        Rule exitOnPriceCrossingTP = new BooleanRule(shouldCloseOnMaxPriceCrossingTP).and(stopGainBySL);
        this.exitRule = exitOnStrongRSIRule.or(exitOnPriceCrossingTP);
    }

    @Override
    public Strategy buildMACDStrategy() {
        MACDIndicators macdInd = new MACDIndicators(series, parentSeries);
        Rule macdEntry = new OverIndicatorRule(macdInd.getClosePrice(), macdInd.getTrendLine200())
                .and(new CrossedUpIndicatorRule(macdInd.getMacd(), macdInd.getSignal()))
                .and(new UnderIndicatorRule(macdInd.getMacd(), DoubleNum.valueOf(0)))
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("MACD", macdEntry, exitRule);
    }

    @Override
    public Strategy buildIchimokuStrategy(int checkIndexForChikou) {
        IchimokuIndicators ichimokuInd = new IchimokuIndicators(series, parentSeries);
        IchimokuRules ichimokuRules = new IchimokuRules(checkIndexForChikou, series, parentSeries);

        Rule priceOverCloud = ichimokuRules.getPriceOverCloud();
        Rule tenkanOverCloud = ichimokuRules.getTenkanSenOverCloud();
        Rule tenkanCrossesKijunUp = ichimokuRules.getTenkanCrossesKijunUpRule();
        Rule cloudBullish = ichimokuRules.getCloudBullish();
        Rule priceOverTenkan = new OverIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTenkanSen());

        Rule ichimokuEntry = new OverIndicatorRule(ichimokuInd.getClosePrice(), ichimokuInd.getTrendLine200())
                .and(cloudBullish)
                .and(priceOverCloud)
                .and(tenkanOverCloud)
                .and(tenkanCrossesKijunUp)
                .and(priceOverTenkan)
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("Ichimoku", ichimokuEntry, exitRule);
    }

    @Override
    public Strategy buildDonchianStrategy() {
        DonchianIndicators donchianInd = new DonchianIndicators(series, parentSeries);
        Rule wasLowerDFallingInTheMeantime =  new OverIndicatorRule(
                new SatisfiedCountIndicator(donchianInd.getIsLowerDFalling(), donchianInd.getUpperDFallingCount()), 0);

        Rule donchianEntry = new OverIndicatorRule(donchianInd.getClosePrice(), donchianInd.getSmartTrendLine200())
                .and(new OverIndicatorRule(donchianInd.getClosePriceParentInd(), donchianInd.getSmartParentTrendLine50()))
                .and(new BooleanIndicatorRule(donchianInd.getWasUpperDFalling()))
                .and(new BooleanIndicatorRule(donchianInd.getIsUpperDRising()))
                .and(wasLowerDFallingInTheMeantime)
                .and(priceActionRules.getLongSignalsPrevailRule(0))
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("Donchian", donchianEntry , exitRule);
    }

    @Override
    public Strategy buildPriceActionStrategy() {
        GeneralIndicators genInd = new GeneralIndicators(series, parentSeries);
        Rule priceActionEntry = new OverIndicatorRule(genInd.getClosePrice(), genInd.getSmartTrendLine200())
                .and(priceActionRules.getPriceActionNotTooDynamic())
                .and(priceActionRules.getMarketNotChoppy())
                .and(priceActionRules.getLongSignalsPrevailRule(3))
                .and(stopLossNotExceedingBounds);
        return new BaseStrategy("PriceAction", priceActionEntry , exitRule);
    }

    @Override
    public Strategy buildBollingerBandsStrategy() {
        GeneralIndicators genInd = new GeneralIndicators(series, parentSeries);
        BollingerBandsIndicators bbandInd = new BollingerBandsIndicators(series, parentSeries);

        Rule bbEntry = new OverIndicatorRule(genInd.getClosePrice(), genInd.getTrendLine200())
                .and(new CrossedDownIndicatorRule(genInd.getClosePrice(), bbandInd.getLowBBand()))
                .and(stopLossNotExceedingBounds);


        return new BaseStrategy("BollingerBands", bbEntry , exitRule);
    }


    public double assessStrategyStrength() {
        Rule strategyStrong = priceActionRules.getLongSignalsPrevailRule(2);
        Rule strategyWeak = priceActionRules.getShortSignalsPrevailRule(2);

        return assessStrategyStrength(strategyStrong, strategyWeak);
    }


}
