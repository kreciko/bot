package pl.kordek.forex.bot.api.xtb;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Trade;
import pl.kordek.forex.bot.api.BrokerAPI;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.AccountInfo;
import pl.kordek.forex.bot.domain.PositionInfo;
import pl.kordek.forex.bot.domain.SymbolResponseInfo;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.RateInfoRecord;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.records.TradeTransInfoRecord;
import pro.xstore.api.message.response.*;
import pro.xstore.api.sync.Credentials;
import pro.xstore.api.sync.SyncAPIConnector;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class XTBAPIImpl implements BrokerAPI {

    private SyncAPIConnector connector = null;
    private SymbolResponseInfo symbolInfo = null;
    private AccountInfo accountInfo = null;
    private HashMap<Duration, PERIOD_CODE> durationMap = null;
    private HashMap<Trade.TradeType, TRADE_OPERATION_CODE> tradeTypeMap = null;

    public XTBAPIImpl() throws APICommunicationException {
        buildDurationMap();
        buildTradeTypeMap();
        try {
            this.connector = new SyncAPIConnector(Configuration.server);
            login(Configuration.username, Configuration.password);
            this.accountInfo = retrieveAccountInfo();
        } catch (IOException e) {
            throw new APICommunicationException("Can't initialize connection to the XTB broker");
        }
    }

    @Override
    public void login(String login, String password) throws APICommunicationException {
        try {
            LoginResponse loginResponse = APICommandFactory.executeLoginCommand(connector,
                    new Credentials(login, password));
            if (loginResponse != null && !loginResponse.getStatus()) {
                throw new APICommunicationException("Failed to login");
            }
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException |
                APIErrorResponse | IOException e1) {
            throw new APICommunicationException("Failed to login");
        }
    }


    @Override
    public void enter(Trade.TradeType tradeType, double stopLoss, double takeProfit, double volume, String strategyWithEntrySignal) throws APICommunicationException {
        if(!symbolOperationsInitiated())
            throw new APICommunicationException("Can't enter the trade. API doesn't know the symbol.");
        TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(tradeTypeMap.get(tradeType),
                TRADE_TRANSACTION_TYPE.OPEN, symbolInfo.getBid(), stopLoss , takeProfit , symbolInfo.getSymbol(), volume, 0L, strategyWithEntrySignal, 0L);
        try {
            APICommandFactory.executeTradeTransactionCommand(connector,ttInfoRecord);
            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e1) {
            throw new APICommunicationException("Couldn't open the position in XTB due to communication problems: "+symbolInfo.getSymbol());
        }
    }

    @Override
    public void exit(Trade.TradeType tradeType, PositionInfo positionInfo) throws APICommunicationException {
        TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(tradeTypeMap.get(tradeType),
                TRADE_TRANSACTION_TYPE.CLOSE, positionInfo.getClosePrice(), positionInfo.getStopLoss(), positionInfo.getTakeProfit(),
                positionInfo.getSymbol(), positionInfo.getVolume(), positionInfo.getOrderNr(), positionInfo.getComment(),
                positionInfo.getExpiration());
        try {
            APICommandFactory.executeTradeTransactionCommand(connector, ttInfoRecord);
            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e1) {
            throw new APICommunicationException("Couldn't close the position in XTB due to communication problems: "+positionInfo.getSymbol());
        }
    }

    @Override
    public void update(Trade.TradeType tradeType, PositionInfo positionInfo) throws APICommunicationException{
        TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(tradeTypeMap.get(tradeType),
                TRADE_TRANSACTION_TYPE.MODIFY, positionInfo.getClosePrice(), positionInfo.getStopLoss(), positionInfo.getTakeProfit(),
                positionInfo.getSymbol(), positionInfo.getVolume(), positionInfo.getOrderNr(), positionInfo.getComment(),
                positionInfo.getExpiration());
        try {
            APICommandFactory.executeTradeTransactionCommand(connector, ttInfoRecord);
            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e1) {
            throw new APICommunicationException("Couldn't modify the position in XTB due to communication problems: "+positionInfo.getSymbol());
        }
    }

    @Override
    public double getOptimalVolume() throws APICommunicationException {
        MarginLevelResponse marginLevelResponse;
        BigDecimal optimalVolume = BigDecimal.valueOf(1);
        try {
            marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
            BigDecimal balance = BigDecimal.valueOf(marginLevelResponse.getBalance());
            BigDecimal balancePerTrade = balance.divide(BigDecimal.valueOf(Configuration.maxNumberOfPositionsOpen), 2, RoundingMode.HALF_UP);

            MarginTradeResponse marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, symbolInfo.getSymbol(), optimalVolume.doubleValue());
            BigDecimal marginRatio = balancePerTrade.divide(BigDecimal.valueOf(marginTradeResponse.getMargin()) , 2, RoundingMode.HALF_UP);

            optimalVolume = optimalVolume.multiply(marginRatio).setScale(2, RoundingMode.HALF_UP);
            Thread.sleep(250);

            if(optimalVolume.doubleValue() < 0.01) {
                return 0.0;
            }

            return optimalVolume.doubleValue();
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e) {
            throw new APICommunicationException("Couldn't get optimal volume");
        }
    }

    @Override
    public boolean isEnoughMargin(double volume) throws APICommunicationException {
        if(!accountInfoRetrieved()){
            throw new APICommunicationException("Can't return account info. API couldn't retrieve data.");
        }
        Double marginFree = accountInfo.getMarginFree();
        Double marginNeeded = accountInfo.getMargin();

        return marginFree!= null && marginNeeded!=null && marginFree > marginNeeded;
    }


    @Override
    public void initSymbolOperations(String symbol) throws APICommunicationException {
        try {
            SymbolResponse sr  = APICommandFactory.executeSymbolCommand(connector, symbol);
            symbolInfo = new SymbolResponseInfo();
            symbolInfo.setAsk(sr.getSymbol().getAsk());
            symbolInfo.setBid(sr.getSymbol().getBid());
            symbolInfo.setPrecision(sr.getSymbol().getPrecision());
            symbolInfo.setSpreadRaw(sr.getSymbol().getSpreadRaw());
            symbolInfo.setSymbol(sr.getSymbol().getSymbol());
        } catch (APICommandConstructionException | APIReplyParseException | APIErrorResponse | APICommunicationException e) {
            throw new APICommunicationException("Can't get symbol response from XTB broker");
        }
    }

    @Override
    public SymbolResponseInfo getSymbolResponseInfo() throws APICommunicationException {
        if(!symbolOperationsInitiated())
            throw new APICommunicationException("Can't return symbol info. API doesn't know the symbol.");
        return symbolInfo;
    }

    @Override
    public AccountInfo getAccountInfo() throws APICommunicationException {
        if(!accountInfoRetrieved())
            throw new APICommunicationException("Can't return account info. API couldn't retrieve data.");
        return accountInfo;
    }

    @Override
    public BaseBarSeries getCharts(Date sinceDate, Duration duration) throws APICommunicationException {
        if(!symbolOperationsInitiated())
            throw new APICommunicationException("Can't get charts. API doesn't know the symbol.");

        long millis = sinceDate.getTime();
        ChartResponse cr = null;
        try {
            cr = APICommandFactory.executeChartLastCommand(connector, symbolInfo.getSymbol(), durationMap.get(duration), millis);
            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException | APIErrorResponse | InterruptedException e) {
            throw new APICommunicationException("Couldn't get charts from XTB due to communication problems: "+symbolInfo.getSymbol());
        }

        return convertResultChartsToBarSeries(cr.getRateInfos(), symbolInfo.getSymbol(), symbolInfo.getPrecision(), duration);
    }

    private BaseBarSeries convertResultChartsToBarSeries(List<RateInfoRecord> rateInfoRecords, String symbol, int precisionNumber, Duration duration) {
        List<Bar> barList = new ArrayList<>();
        for (RateInfoRecord rateInfoRecord : rateInfoRecords) {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(rateInfoRecord.getCtm()),
                    ZoneId.systemDefault());
            BigDecimal open = BigDecimal.valueOf(rateInfoRecord.getOpen()).scaleByPowerOfTen(-precisionNumber);
            BigDecimal high = BigDecimal.valueOf(rateInfoRecord.getOpen())
                    .add(BigDecimal.valueOf(rateInfoRecord.getHigh())).scaleByPowerOfTen(-precisionNumber);
            BigDecimal low = BigDecimal.valueOf(rateInfoRecord.getOpen())
                    .add(BigDecimal.valueOf(rateInfoRecord.getLow())).scaleByPowerOfTen(-precisionNumber);
            BigDecimal close = BigDecimal.valueOf(rateInfoRecord.getOpen())
                    .add(BigDecimal.valueOf(rateInfoRecord.getClose())).scaleByPowerOfTen(-precisionNumber);

            Double openD = open.doubleValue();
            Double highD = high.doubleValue();
            Double lowD = low.doubleValue();
            Double closeD = close.doubleValue();

            BaseBar Bar = new BaseBar(duration, endTime, openD, highD, lowD, closeD,
                    rateInfoRecord.getVol());
            if(!(open.equals(close) && low.equals(high)))
                barList.add(Bar);
        }
        return new BaseBarSeries(symbol, barList);
    }

    @Override
    public List<PositionInfo> getOpenedPositions() throws APICommunicationException {
        List<TradeRecord> openedPositionsTR = null;
        try {
            TradesResponse tradeResponse = APICommandFactory.executeTradesCommand(connector, true);
            openedPositionsTR = tradeResponse.getTradeRecords();
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse e) {
            throw new APICommunicationException("Couldn't retrieve opened positions in XTB");
        }

        return convertOpenedPositions(openedPositionsTR);
    }

    private List<PositionInfo> convertOpenedPositions(List<TradeRecord> openedPositionsTR){
        List<PositionInfo> openedPositions = new ArrayList<>();
        for(TradeRecord openedPosTR : openedPositionsTR){
            PositionInfo posInfo = new PositionInfo();
            posInfo.setComment(openedPosTR.getCustomComment());
            posInfo.setClosePrice(openedPosTR.getClose_price());
            posInfo.setOpenPrice(openedPosTR.getOpen_price());
            posInfo.setExpiration(openedPosTR.getExpiration());
            posInfo.setStopLoss(openedPosTR.getSl());
            posInfo.setTakeProfit(openedPosTR.getTp());
            posInfo.setTradeType(openedPosTR.getCmd() == 0 ? Trade.TradeType.BUY : Trade.TradeType.SELL);
            posInfo.setSymbol(openedPosTR.getSymbol());
            posInfo.setVolume(openedPosTR.getVolume());
            posInfo.setOrderNr(openedPosTR.getOrder());

            openedPositions.add(posInfo);
        }

        return openedPositions;
    }

    private void buildDurationMap() {
        durationMap = new HashMap<>();
        durationMap.put(Duration.ofMinutes(1),PERIOD_CODE.PERIOD_M1);
        durationMap.put(Duration.ofMinutes(5), PERIOD_CODE.PERIOD_M5);
        durationMap.put(Duration.ofMinutes(15), PERIOD_CODE.PERIOD_M15);
        durationMap.put( Duration.ofMinutes(30), PERIOD_CODE.PERIOD_M30);
        durationMap.put(Duration.ofHours(1), PERIOD_CODE.PERIOD_H1);
        durationMap.put(Duration.ofHours(4), PERIOD_CODE.PERIOD_H4);
        durationMap.put(Duration.ofDays(1), PERIOD_CODE.PERIOD_D1);
    }
    private void buildTradeTypeMap() {
        tradeTypeMap = new HashMap<>();
        tradeTypeMap.put(Trade.TradeType.BUY, TRADE_OPERATION_CODE.BUY);
        tradeTypeMap.put(Trade.TradeType.SELL, TRADE_OPERATION_CODE.SELL);
    }

    private boolean symbolOperationsInitiated(){
        return symbolInfo!=null;
    }

    private AccountInfo retrieveAccountInfo() throws APICommunicationException {
        AccountInfo accountInfo = new AccountInfo();
        try {
            MarginLevelResponse marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
            accountInfo.setBalance(marginLevelResponse.getBalance());
            accountInfo.setCredit(marginLevelResponse.getCredit());
            accountInfo.setCurrency(marginLevelResponse.getCurrency());
            accountInfo.setEquity(marginLevelResponse.getEquity());
            accountInfo.setMargin(marginLevelResponse.getMargin());
            accountInfo.setMarginLevel(marginLevelResponse.getMargin_level());
            accountInfo.setMarginFree(marginLevelResponse.getMargin_free());

            Thread.sleep(250);
        } catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
                | APIErrorResponse | InterruptedException e) {
            throw new APICommunicationException("Couldn't retrieve account info from XTB");
        }
        return accountInfo;
    }

    private boolean accountInfoRetrieved() { return  accountInfo!=null; }

}
