package pl.kordek.forex.bot.api;

import org.ta4j.core.Order;
import org.ta4j.core.Order.OrderType;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.RateInfoRecord;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.records.TradeTransInfoRecord;
import pro.xstore.api.message.response.*;
import pro.xstore.api.sync.SyncAPIConnector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class XTB {
    private SyncAPIConnector connector = null;
    private SymbolResponse sr;
    private HashMap<OrderType, TRADE_OPERATION_CODE> orderTypeXTBMap;

    public XTB(SyncAPIConnector connector, String symbol) throws APIErrorResponse, APIReplyParseException, APICommunicationException, APICommandConstructionException {
        this.connector = connector;
        this.sr = APICommandFactory.executeSymbolCommand(connector, symbol);
        this.orderTypeXTBMap = new HashMap<>();
        orderTypeXTBMap.put(OrderType.BUY, TRADE_OPERATION_CODE.BUY);
        orderTypeXTBMap.put(OrderType.SELL, TRADE_OPERATION_CODE.SELL);
    }

    public void enterXTB(OrderType orderType, BigDecimal stopLoss, BigDecimal takeProfit,
                         double volume, String strategyWithEntrySignal) throws XTBCommunicationException {
        SymbolRecord symbolRecord = sr.getSymbol();
        TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(orderTypeXTBMap.get(orderType),
                TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getBid(), stopLoss.doubleValue() , takeProfit.doubleValue() , symbolRecord.getSymbol(), volume, 0L, strategyWithEntrySignal, 0L);

        try {
            APICommandFactory.executeTradeTransactionCommand(connector,ttInfoRecord);
            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e1) {
            System.out.println(new Date() + ": Failed to open in XTB" + symbolRecord.getSymbol());
            throw new XTBCommunicationException("Couldn't open the position in XTB due to communication problems: "+symbolRecord.getSymbol());
        }
    }

    public void exitXTB(TradeRecord tr, OrderType orderType) throws XTBCommunicationException {
        if(tr==null)
            return;
        TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(orderTypeXTBMap.get(orderType),
                TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());
        try {
            APICommandFactory.executeTradeTransactionCommand(connector, ttInfoRecord);
            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e1) {
            System.out.println(new Date() + ": Failed to close in XTB" + tr.getSymbol());
            throw new XTBCommunicationException("Couldn't close the position in XTB due to communication problems: "+tr.getSymbol());
        }

    }

    //------------------- OPTIMAL VOLUME CALCULATION ---------------
    public double getOptimalVolumeXTB(String symbol) throws XTBCommunicationException {
        MarginLevelResponse marginLevelResponse;
        BigDecimal optimalVolume = BigDecimal.valueOf(1);
        try {
            marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
            BigDecimal balance = BigDecimal.valueOf(marginLevelResponse.getBalance());
            BigDecimal balancePerTrade = balance.divide(BigDecimal.valueOf(5L), 2, RoundingMode.HALF_UP);

            MarginTradeResponse marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, symbol, optimalVolume.doubleValue());
            BigDecimal marginRatio = balancePerTrade.divide(BigDecimal.valueOf(marginTradeResponse.getMargin()) , 2, RoundingMode.HALF_UP);

            optimalVolume = optimalVolume.multiply(marginRatio).setScale(2, RoundingMode.HALF_UP);
            Thread.sleep(250);

            if(optimalVolume.doubleValue() < 0.01) {
                return 0.0;
            }

            return optimalVolume.doubleValue();
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e) {
            throw new XTBCommunicationException("Couldn't get optimal volume");
        }
    }

    public void updateStopLossXTB(TradeRecord tr, OrderType orderType) throws XTBCommunicationException {
        double stopLoss = tr.getOpen_price();

        TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(orderTypeXTBMap.get(orderType),
                TRADE_TRANSACTION_TYPE.MODIFY, tr.getClose_price(), stopLoss, tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());
        try {
            APICommandFactory.executeTradeTransactionCommand(connector, ttInfoRecord);
            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e1) {
            throw new XTBCommunicationException("Couldn't update the stopLoss in XTB due to communication problems: "+tr.getSymbol());
        }
    }

    private Double getMarginFree() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
        MarginLevelResponse marginLevelResponse;
        marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
        return marginLevelResponse.getMargin_free();
    }

    private Double getMarginNeeded(Double volume) throws XTBCommunicationException {
        MarginTradeResponse marginTradeResponse;
        try{
            marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, sr.getSymbol().getSymbol(), volume);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException | APIErrorResponse e) {
            throw new XTBCommunicationException("Couldn't retrieve margins in XTB due to communication problems: "+sr.getSymbol().getSymbol());
        }
        return marginTradeResponse.getMargin();
    }

    public List<RateInfoRecord> getCharts(Date sinceDate, PERIOD_CODE period) throws XTBCommunicationException {
        long millis = sinceDate.getTime();
        ChartResponse cr = null;
        try {
            cr = APICommandFactory.executeChartLastCommand(connector, sr.getSymbol().getSymbol(), period, millis);
            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException | APIErrorResponse | InterruptedException e) {
            throw new XTBCommunicationException("Couldn't get charts from XTB due to communication problems: "+sr.getSymbol().getSymbol());
        }


        return cr.getRateInfos();
    }

    public boolean isEnoughMargin(Double volume) throws XTBCommunicationException {
        Double marginFree = 0.0;
        Double marginNeeded = 0.0;
        try {
            marginFree = getMarginFree();
            marginNeeded = getMarginNeeded(volume);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse e) {
            throw new XTBCommunicationException("Couldn't execute is enough margin check");
        }

        return marginFree > marginNeeded;
    }

    /// getters


    public SyncAPIConnector getConnector() {
        return connector;
    }

    public SymbolResponse getSr() {
        return sr;
    }
}
