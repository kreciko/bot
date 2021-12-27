package pl.kordek.forex.bot.strategy;

import org.ta4j.core.*;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.rules.PriceActionRules;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public abstract class StrategyBuilder {
    public Order.OrderType orderType;
    public int typeOfOperation;
    public Indicator stopLossStrategy;

    protected BaseBarSeries series;
    protected BaseBarSeries parentSeries;
    protected PriceActionRules priceActionRules;

    abstract public Strategy buildMACDStrategy();
    abstract public Strategy  buildIchimokuStrategy(int checkIndexForChikou);
    abstract public Strategy buildDonchianStrategy();
    abstract public Strategy buildPriceActionStrategy();
    abstract public double assessStrategyStrength();

    StrategyBuilder(BaseBarSeries series, BaseBarSeries parentSeries){
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        this.series = series;
        this.parentSeries = parentSeries;

        this.priceActionRules = new PriceActionRules(series, parentSeries);
    }

    public List<Strategy> getStrategyList(){
        ArrayList<Strategy> strategies = new ArrayList<>();
        strategies.add(buildMACDStrategy());
//        strategies.add(buildIchimokuStrategy(series.getEndIndex()));
        strategies.add(buildPriceActionStrategy());
//        strategies.add(buildDonchianStrategy());
        return strategies;
    }

    protected double assessStrategyStrength(Rule strategyStrong, Rule strategyWeak){
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

    public BaseBarSeries getSeries() {
        return series;
    }

    public BaseBarSeries getParentSeries() {
        return parentSeries;
    }

}
