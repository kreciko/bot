package pl.kordek.forex.bot.domain;

public class SymbolResponseInfo {
    private Double spreadRaw;
    private Integer precision;
    private Double bid;
    private Double ask;
    private String symbol;

    public Double getSpreadRaw() {
        return spreadRaw;
    }

    public void setSpreadRaw(Double spreadRaw) {
        this.spreadRaw = spreadRaw;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }

    public Double getBid() {
        return bid;
    }

    public void setBid(Double bid) {
        this.bid = bid;
    }

    public Double getAsk() {
        return ask;
    }

    public void setAsk(Double ask) {
        this.ask = ask;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
