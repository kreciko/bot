package pl.kordek.forex.bot.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.TradingRecord;
import pl.kordek.forex.bot.Robot;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.BlackListOperation;
import pl.kordek.forex.bot.domain.PositionInfo;
import pl.kordek.forex.bot.domain.RobotInfo;
import pl.kordek.forex.bot.exceptions.SerializationFailedException;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.TradesHistoryResponse;
import pro.xstore.api.sync.SyncAPIConnector;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.*;

import static java.util.stream.Collectors.toList;

public class FinishOperations {
    private static final Logger logger = LogManager.getLogger(FinishOperations.class);

    private int robotIteration;
    private HashMap<String,BlackListOperation> blackList;
    private HashMap<String, TradingRecord> longTradingRecordsMap;
    private HashMap<String, TradingRecord> shortTradingRecordsMap;
    private HashMap<String, BaseBarSeries> baseBarSeriesMap;


    public FinishOperations(RobotInfo robotInfo) {
        this.robotIteration = robotInfo.getRobotIteration();
        this.blackList = robotInfo.getBlackList();
        this.longTradingRecordsMap = robotInfo.getLongTradingRecordMap();
        this.shortTradingRecordsMap = robotInfo.getShortTradingRecordMap();
        this.baseBarSeriesMap = robotInfo.getBaseBarSeriesMap();
    }

    public void updateBlackList(SyncAPIConnector connector, Duration durationPeriod)
            throws XTBCommunicationException {
        //Duration durationPeriod = durationMap.get(PERIOD_CODE.PERIOD_H1);
        if(robotIteration == 0)
            return;

        long durationMilis = durationPeriod.toMillis();
        long dateStart = new Date().getTime() - durationMilis;
        long breakInTrading = Configuration.breakInTradingForSymbol;

        TradesHistoryResponse tHistoryResponse;
        try {
            Thread.sleep(250);
            tHistoryResponse = APICommandFactory.executeTradesHistoryCommand(connector, dateStart, 0L);


            Duration timePassedDuration = null;
            for (TradeRecord t : tHistoryResponse.getTradeRecords()) {
                Long timePassed = new Date().getTime() - t.getClose_time();
                timePassedDuration = Duration.ofMillis(timePassed);
                if ( timePassedDuration.toHours() < breakInTrading && !blackList.containsKey(t.getSymbol())) {
                    blackList.put(t.getSymbol(), new BlackListOperation(t.getSymbol(), t.getCmd(), t.getClose_time()));
                    logger.info("Putting symbol {} to the black list. Because trade was finished within the last {} h",t.getSymbol(), breakInTrading);
                }
            }

            Set<Map.Entry<String, BlackListOperation>> blackListEntrySet = new HashSet<>();
            blackListEntrySet.addAll(blackList.entrySet());
            for (Map.Entry<String, BlackListOperation> pair : blackListEntrySet) {
                BlackListOperation t = pair.getValue();
                Long timePassed = new Date().getTime() - t.getCloseTime();
                Duration d = Duration.ofMillis(timePassed);
                if (d.toHours() > breakInTrading) {
                    blackList.remove(pair.getKey());
                    logger.info("Removing symbol " + pair.getKey() + " from the black list");
                }
            }

        } catch (APICommandConstructionException | APICommunicationException | APIReplyParseException | APIErrorResponse | InterruptedException e1) {
            throw new XTBCommunicationException("Couldn't get trade history from xtb");
        }
    }

    public void serializeFiles(String robotInfoFileLocation) throws IOException
    {
        List<HashMap<String, TradingRecord>> tradingRecordsMaps = new ArrayList<>();
        tradingRecordsMaps.add(longTradingRecordsMap);
        tradingRecordsMaps.add(shortTradingRecordsMap);
        RobotInfo info = new RobotInfo(longTradingRecordsMap, shortTradingRecordsMap, baseBarSeriesMap, blackList, robotIteration+1);

        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(robotInfoFileLocation))) {
            outputStream.writeObject(info);
        } catch (IOException e) {
            throw e;
        }
    }

    public void printIterationInfos(List<PositionInfo> openedPositionsList , List<String> spreadTooLargeSymbols){
        logger.info("{} robot iteration. Positions opened: {}", robotIteration, openedPositionsList.stream().map(e -> e.getSymbol()).collect(toList()));
        logger.info("Black list: "+blackList.values().stream().map(e -> e.getInstrument() + " " + e.getTypeOfOperation()).collect(toList()));
        logger.info("Spread too wide for following: {}",spreadTooLargeSymbols);
    }


}
