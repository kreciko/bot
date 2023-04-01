package pl.kordek.forex.bot.strategy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.rules.BooleanRule;
import pl.kordek.forex.bot.checker.PositionChecker;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.indicator.GeneralIndicators;
import pl.kordek.forex.bot.rules.PriceActionRules;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public abstract class StrategyBuilder {
    private static final Logger logger = LogManager.getLogger(StrategyBuilder.class);

    public Trade.TradeType tradeType;
    public int typeOfOperation;
    public Indicator stopLossStrategy;

    protected BaseBarSeries series;
    protected BaseBarSeries parentSeries;
    protected PriceActionRules priceActionRules;
    protected GeneralIndicators generalIndicators;
    protected Rule exitRule;

    abstract public Strategy buildMACDStrategy();
    abstract public Strategy  buildIchimokuStrategy(int checkIndexForChikou);
    abstract public Strategy buildDonchianStrategy();
    abstract public Strategy buildPriceActionStrategy();
    abstract public Strategy buildBollingerBandsStrategy();
    abstract public double assessStrategyStrength();

    StrategyBuilder(BaseBarSeries series, BaseBarSeries parentSeries){
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        this.series = series;
        this.parentSeries = parentSeries;

        this.priceActionRules = new PriceActionRules(series, parentSeries);
        this.exitRule = new BooleanRule(false);
        this.generalIndicators = new GeneralIndicators(series,parentSeries);
    }

    public List<Strategy> getStrategyList(){
        ArrayList<Strategy> strategies = new ArrayList<>();
        strategies.add(buildMACDStrategy());
  //      strategies.add(buildIchimokuStrategy(series.getEndIndex()));
 //       strategies.add(buildPriceActionStrategy());
 //       strategies.add(buildDonchianStrategy());
 //       strategies.add(buildBollingerBandsStrategy());
        return strategies;
    }

    protected double assessStrategyStrength(Rule strategyStrong, Rule strategyWeak){
        Double strategyStrength = 1.0;
        if(Configuration.considerStratetyStrength) {
            if (strategyStrong.isSatisfied(series.getEndIndex())) {
                strategyStrength = 1.5;
            } else if (strategyWeak.isSatisfied(series.getEndIndex())) {
                strategyStrength = 0.5;
            }
            logger.info("Calculated strategy strength: {}", strategyStrength);
        }

        return strategyStrength;
    }

    public BaseBarSeries getSeries() {
        return series;
    }

    public BaseBarSeries getParentSeries() {
        return parentSeries;
    }

}
