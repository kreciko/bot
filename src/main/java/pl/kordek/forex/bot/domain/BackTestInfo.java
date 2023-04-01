package pl.kordek.forex.bot.domain;

import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;

import java.math.BigDecimal;

public class BackTestInfo {
    private BigDecimal winRate;
    private Double profit;
    private Double maxConsecutiveLoses;
    private Double maxConsecutiveWins;
    private Trade.TradeType tradeType;

    public BackTestInfo(BigDecimal winRate, Double profit, Double maxConsecutiveLoses, Double maxConsecutiveWins, Trade.TradeType tradeType) {
        this.winRate = winRate;
        this.profit = profit;
        this.maxConsecutiveLoses = maxConsecutiveLoses;
        this.maxConsecutiveWins = maxConsecutiveWins;
        this.tradeType = tradeType;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public Double getProfit() {
        return profit;
    }

    public Double getMaxConsecutiveLoses() {
        return maxConsecutiveLoses;
    }

    public Double getMaxConsecutiveWins() {
        return maxConsecutiveWins;
    }

    public Trade.TradeType getTradeType() {
        return tradeType;
    }
}
