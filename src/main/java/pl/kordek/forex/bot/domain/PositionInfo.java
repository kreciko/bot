package pl.kordek.forex.bot.domain;

import org.ta4j.core.Trade;

public class PositionInfo {
    private Double openPrice;
    private Double closePrice;
    private Double stopLoss;
    private Double takeProfit;
    private Double volume;
    private String comment;
    private String symbol;
    private Long expiration;
    private Long orderNr;
    private Trade.TradeType tradeType;

    public Double getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(Double openPrice) {
        this.openPrice = openPrice;
    }

    public void setClosePrice(Double closePrice) {
        this.closePrice = closePrice;
    }

    public void setStopLoss(Double stopLoss) {
        this.stopLoss = stopLoss;
    }

    public void setTakeProfit(Double takeProfit) {
        this.takeProfit = takeProfit;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }

    public void setTradeType(Trade.TradeType tradeType) {
        this.tradeType = tradeType;
    }

    public Double getClosePrice() {
        return closePrice;
    }

    public Double getStopLoss() {
        return stopLoss;
    }

    public Double getTakeProfit() {
        return takeProfit;
    }

    public Double getVolume() {
        return volume;
    }

    public String getComment() {
        return comment;
    }

    public String getSymbol() {
        return symbol;
    }

    public Long getExpiration() {
        return expiration;
    }

    public Trade.TradeType getTradeType() {
        return tradeType;
    }

    public Long getOrderNr() {
        return orderNr;
    }

    public void setOrderNr(Long orderNr) {
        this.orderNr = orderNr;
    }
}
