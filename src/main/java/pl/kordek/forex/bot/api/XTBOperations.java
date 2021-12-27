package pl.kordek.forex.bot.api;

import org.ta4j.core.Order;
import org.ta4j.core.Order.OrderType;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.records.TradeTransInfoRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.LoginResponse;
import pro.xstore.api.message.response.MarginLevelResponse;
import pro.xstore.api.message.response.TradesResponse;
import pro.xstore.api.sync.Credentials;
import pro.xstore.api.sync.SyncAPIConnector;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class XTBOperations {
    protected SyncAPIConnector connector = null;
    protected HashMap<OrderType, TRADE_OPERATION_CODE> orderTypeXTBMap;

    public XTBOperations(SyncAPIConnector connector) {
        this.connector = connector;
        this.orderTypeXTBMap = new HashMap<>();
        orderTypeXTBMap.put(OrderType.BUY, TRADE_OPERATION_CODE.BUY);
        orderTypeXTBMap.put(OrderType.SELL, TRADE_OPERATION_CODE.SELL);
    }

    public List<TradeRecord> getOpenedPositions() throws XTBCommunicationException {
        List<TradeRecord> openedPositionList = null;
        try {
            TradesResponse tradeResponse = APICommandFactory.executeTradesCommand(connector, true);
            openedPositionList = tradeResponse.getTradeRecords();
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse e) {
            throw new XTBCommunicationException("Couldn't execute is enough margin check");
        }

        return openedPositionList;
    }

    public Double getMarginFree() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
        MarginLevelResponse marginLevelResponse;
        marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
        return marginLevelResponse.getMargin_free();
    }

    public void exitXTB(TradeRecord tr, Order.OrderType orderType) throws XTBCommunicationException {
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

    public void loginToXTB() throws XTBCommunicationException {
        try {
            LoginResponse loginResponse = APICommandFactory.executeLoginCommand(connector,
                    new Credentials(Configuration.username, Configuration.password));
            if (loginResponse != null && !loginResponse.getStatus()) {
                throw new XTBCommunicationException("Failed to login");
            }
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException |
                APIErrorResponse | IOException e1) {
            throw new XTBCommunicationException("Couldn't log in to the XTB");
        }
    }


}
