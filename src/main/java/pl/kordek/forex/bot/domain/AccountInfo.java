package pl.kordek.forex.bot.domain;

public class AccountInfo {
    private Double balance;
    private Double credit;
    private String currency;
    private Double equity;
    private Double margin;
    private Double marginFree;
    private Double marginLevel;

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public Double getCredit() {
        return credit;
    }

    public void setCredit(Double credit) {
        this.credit = credit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Double getEquity() {
        return equity;
    }

    public void setEquity(Double equity) {
        this.equity = equity;
    }

    public Double getMargin() {
        return margin;
    }

    public void setMargin(Double margin) {
        this.margin = margin;
    }

    public Double getMarginFree() {
        return marginFree;
    }

    public void setMarginFree(Double marginFree) {
        this.marginFree = marginFree;
    }

    public Double getMarginLevel() {
        return marginLevel;
    }

    public void setMarginLevel(Double marginLevel) {
        this.marginLevel = marginLevel;
    }
}
