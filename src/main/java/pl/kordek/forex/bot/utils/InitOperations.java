package pl.kordek.forex.bot.utils;

import com.opencsv.CSVReader;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import pl.kordek.forex.bot.Robot;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.BackTestInfo;
import pl.kordek.forex.bot.domain.BlackListOperation;
import pl.kordek.forex.bot.domain.RobotInfo;
import pl.kordek.forex.bot.exceptions.SerializationFailedException;
import pro.xstore.api.message.codes.PERIOD_CODE;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.HashMap;

public class InitOperations {

    private static final int LONG_INDEX=0;
    private static final int SHORT_INDEX=1;

    public RobotInfo deserializeFiles(String robotInfoFileLocation)
            throws SerializationFailedException {

        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(robotInfoFileLocation))) {

            RobotInfo info = (RobotInfo) inputStream
                    .readObject();
            if(info.getRobotIteration() > 1000)
            {
                System.out.println("Reseting robot iteration nr to avoid large numbers");
                info.setRobotIteration(0);
            }
            return info;
        } catch (FileNotFoundException ex) {
            System.out.println("robot-info.bin file not found. Will create new file");
            HashMap<String, BlackListOperation> blackList = new HashMap<>();
            HashMap<String, TradingRecord> longTradingRecordsMap = new HashMap<>();
            HashMap<String, TradingRecord> shortTradingRecordsMap = new HashMap<>();
            for (String symbol : Configuration.instrumentsFX) {
                longTradingRecordsMap.put(symbol, new BaseTradingRecord());
                shortTradingRecordsMap.put(symbol, new BaseTradingRecord(Trade.TradeType.SELL));
                System.out.println("Init trading record for " + symbol);
            }

            return new RobotInfo(longTradingRecordsMap, shortTradingRecordsMap, blackList, 0);
        } catch (IOException e) {
            throw new SerializationFailedException("Deserialization failed. IO Exception");
        } catch (ClassNotFoundException e) {
            throw new SerializationFailedException("Derialization failed. Class not found exception");
        }

    }

    public HashMap<PERIOD_CODE, Duration> buildDurationMap() {
        HashMap<PERIOD_CODE, Duration> durationMap = new HashMap<>();
        durationMap.put(PERIOD_CODE.PERIOD_M1, Duration.ofMinutes(1));
        durationMap.put(PERIOD_CODE.PERIOD_M5, Duration.ofMinutes(5));
        durationMap.put(PERIOD_CODE.PERIOD_M15, Duration.ofMinutes(15));
        durationMap.put(PERIOD_CODE.PERIOD_M30, Duration.ofMinutes(30));
        durationMap.put(PERIOD_CODE.PERIOD_H1, Duration.ofHours(1));
        durationMap.put(PERIOD_CODE.PERIOD_H4, Duration.ofHours(4));
        durationMap.put(PERIOD_CODE.PERIOD_D1, Duration.ofDays(1));
        return durationMap;
    }

    public HashMap<String, HashMap<String, BackTestInfo>> initWinningRatios() throws IOException {
        HashMap<String, HashMap<String, BackTestInfo>> winningRatioMap = new HashMap<>();
        InputStream stream = Robot.class.getClassLoader().getResourceAsStream("winning_ratios.csv");
        String strategyName = "";

        try (CSVReader csvReader = new CSVReader(new InputStreamReader(stream, Charset.forName("UTF-8")), ';', '"',1)) {
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                if(!line[0].equals(strategyName)){
                    winningRatioMap.put(line[0], new HashMap<>());
                    strategyName = line[0];
                }
                winningRatioMap.get(strategyName).put(line[1], new BackTestInfo(new BigDecimal(line[2]), Double.valueOf(line[4].replace(" profit:","")),
                        Double.valueOf(line[5].replace(" maxLost:","")), Double.valueOf(line[6].replace(" maxWon:",""))));
            }

        } catch (IOException ioe) {
            System.out.println("Unable to load winning ratios from CSV");
            throw ioe;
        }

        return winningRatioMap;
    }
}
