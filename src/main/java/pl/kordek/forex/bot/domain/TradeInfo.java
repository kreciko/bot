package pl.kordek.forex.bot.domain;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Trade;

import java.math.BigDecimal;

public class TradeInfo {
    String strategyName;
    BaseBarSeries series;
    Trade.TradeType tradeType;
    BigDecimal stopLoss;
    BigDecimal takeProfit;
    Double volume;

    public TradeInfo(BaseBarSeries series, Trade.TradeType tradeType, BigDecimal stopLoss,
                     BigDecimal takeProfit, Double volume, String strategyName) {
        this.strategyName = strategyName;
        this.series = series;
        this.tradeType = tradeType;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.volume = volume;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public BaseBarSeries getSeries() {
        return series;
    }

    public void setSeries(BaseBarSeries series) {
        this.series = series;
    }

    public Trade.TradeType getTradeType() {
        return tradeType;
    }

    public void setTradeType(Trade.TradeType tradeType) {
        this.tradeType = tradeType;
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }

    public BigDecimal getTakeProfit() {
        return takeProfit;
    }

    public void setTakeProfit(BigDecimal takeProfit) {
        this.takeProfit = takeProfit;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }
}
