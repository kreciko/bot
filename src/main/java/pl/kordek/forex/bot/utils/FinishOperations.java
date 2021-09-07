package pl.kordek.forex.bot.utils;

import org.ta4j.core.TradingRecord;
import pl.kordek.forex.bot.domain.BlackListOperation;
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
    private int robotIteration;
    private HashMap<String,BlackListOperation> blackList;
    private HashMap<String, TradingRecord> longTradingRecordsMap;
    private HashMap<String, TradingRecord> shortTradingRecordsMap;


    public FinishOperations(RobotInfo robotInfo) {
        this.robotIteration = robotInfo.getRobotIteration();
        this.blackList = robotInfo.getBlackList();
        this.longTradingRecordsMap = robotInfo.getLongTradingRecordMap();
        this.shortTradingRecordsMap = robotInfo.getShortTradingRecordMap();
    }

    public void updateBlackList(SyncAPIConnector connector, Duration durationPeriod)
            throws XTBCommunicationException {
        //Duration durationPeriod = durationMap.get(PERIOD_CODE.PERIOD_H1);
        if(robotIteration == 0)
            return;

        long durationMilis = durationPeriod.toMillis();
        long dateStart = new Date().getTime() - durationMilis;

        TradesHistoryResponse tHistoryResponse;
        try {
            tHistoryResponse = APICommandFactory.executeTradesHistoryCommand(connector, dateStart, 0L);

            for (TradeRecord t : tHistoryResponse.getTradeRecords()) {
                if (t.getProfit() < 0 && !blackList.containsKey(t.getSymbol())) {
                    blackList.put(t.getSymbol(), new BlackListOperation(t.getSymbol(), t.getCmd(), t.getClose_time()));
                    System.out.println(new Date() + ": Putting symbol " + t.getSymbol() + " to the black list");
                }
            }

            Set<Map.Entry<String, BlackListOperation>> blackListEntrySet = new HashSet<>();
            blackListEntrySet.addAll(blackList.entrySet());
            for (Map.Entry<String, BlackListOperation> pair : blackListEntrySet) {
                BlackListOperation t = pair.getValue();
                Long timePassed = new Date().getTime() - t.getCloseTime();
                Duration d = Duration.ofMillis(timePassed);
                if (d.toHours() > 5L) {
                    blackList.remove(pair.getKey());
                    System.out.println(new Date() + ": Removing symbol " + pair.getKey() + " from the black list");
                }
            }

        } catch (APICommandConstructionException | APICommunicationException | APIReplyParseException
                | APIErrorResponse e1) {
            throw new XTBCommunicationException("Couldn't get trade history from xtb");
        }
    }

    public void serializeFiles(String robotInfoFileLocation) throws SerializationFailedException
    {
        List<HashMap<String, TradingRecord>> tradingRecordsMaps = new ArrayList<>();
        tradingRecordsMaps.add(longTradingRecordsMap);
        tradingRecordsMaps.add(shortTradingRecordsMap);
        RobotInfo info = new RobotInfo(longTradingRecordsMap, shortTradingRecordsMap, blackList, robotIteration+1);

        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(robotInfoFileLocation))) {
            outputStream.writeObject(info);
        } catch (IOException e) {
            throw new SerializationFailedException("Serialization failed");
        }
    }

    public void printIterationInfos(List<TradeRecord> openedPositionsList ,List<String> spreadTooLargeSymbols){
        System.out.print(new Date() + ": "+robotIteration+" robot iteration. Positions opened: "
                + openedPositionsList.stream().map(e -> e.getSymbol()).collect(toList()));
        System.out.println(" Black list: "+blackList.values().stream().map(e -> e.getInstrument() + " " + e.getTypeOfOperation()).collect(toList()));
        System.out.println(new Date() + ": Spread too wide for following:"+spreadTooLargeSymbols);
        System.out.println();
    }


}
