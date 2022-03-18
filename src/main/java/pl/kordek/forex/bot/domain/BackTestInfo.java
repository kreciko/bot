package pl.kordek.forex.bot.domain;

import java.math.BigDecimal;

public class BackTestInfo {
    private BigDecimal winRate;
    private Double profit;
    private Double maxConsecutiveLoses;
    private Double maxConsecutiveWins;

    public BackTestInfo(BigDecimal winRate, Double profit, Double maxConsecutiveLoses, Double maxConsecutiveWins) {
        this.winRate = winRate;
        this.profit = profit;
        this.maxConsecutiveLoses = maxConsecutiveLoses;
        this.maxConsecutiveWins = maxConsecutiveWins;
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
}
