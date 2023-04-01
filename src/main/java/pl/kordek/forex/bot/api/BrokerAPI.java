package pl.kordek.forex.bot.api;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Trade;
import pl.kordek.forex.bot.domain.AccountInfo;
import pl.kordek.forex.bot.domain.PositionInfo;
import pl.kordek.forex.bot.domain.SymbolResponseInfo;
import pro.xstore.api.message.error.APICommunicationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.List;

public interface BrokerAPI {
    void login(String login, String password) throws APICommunicationException;

    void enter(Trade.TradeType tradeType, double stopLoss, double takeProfit, double volume, String strategyWithEntrySignal) throws APICommunicationException;
    void exit(Trade.TradeType tradeType, PositionInfo positionInfo) throws APICommunicationException;
    void update(Trade.TradeType tradeType, PositionInfo positionInfo) throws APICommunicationException;
    double getOptimalVolume() throws APICommunicationException;
    boolean isEnoughMargin(double volume) throws APICommunicationException;
    void initSymbolOperations(String symbol) throws APICommunicationException;
    SymbolResponseInfo getSymbolResponseInfo() throws APICommunicationException;
    AccountInfo getAccountInfo() throws APICommunicationException;
    BaseBarSeries getCharts(Date sinceDate, Duration duration) throws APICommunicationException;
    List<PositionInfo> getOpenedPositions() throws APICommunicationException;
}
