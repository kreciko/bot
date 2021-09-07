package pl.kordek.forex.bot.api;

import org.ta4j.core.Order.OrderType;
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

public class XTBSymbolOperations extends XTBOperations{
    private SymbolResponse sr;

    public XTBSymbolOperations(SyncAPIConnector connector, String symbol) throws APIErrorResponse, APIReplyParseException, APICommunicationException, APICommandConstructionException {
        super(connector);
        this.sr = APICommandFactory.executeSymbolCommand(connector, symbol);
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



    //------------------- OPTIMAL VOLUME CALCULATION ---------------
    public double getOptimalVolumeXTB() throws XTBCommunicationException {
        MarginLevelResponse marginLevelResponse;
        BigDecimal optimalVolume = BigDecimal.valueOf(1);
        try {
            marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
            BigDecimal balance = BigDecimal.valueOf(marginLevelResponse.getBalance());
            BigDecimal balancePerTrade = balance.divide(BigDecimal.valueOf(5L), 2, RoundingMode.HALF_UP);

            MarginTradeResponse marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, sr.getSymbol().getSymbol(), optimalVolume.doubleValue());
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
