package pl.kordek.forex.bot.utils;

import com.opencsv.CSVReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import pl.kordek.forex.bot.Robot;
import pl.kordek.forex.bot.checker.PositionChecker;
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
    private static final Logger logger = LogManager.getLogger(InitOperations.class);
    public RobotInfo deserializeFiles(String robotInfoFileLocation)
            throws SerializationFailedException {

        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(robotInfoFileLocation))) {

            RobotInfo info = (RobotInfo) inputStream
                    .readObject();
            if(info.getRobotIteration() > 1000)
            {
                logger.info("Reseting robot iteration nr to avoid large numbers");
                info.setRobotIteration(0);
            }
            return info;
        } catch (FileNotFoundException ex) {
            logger.info("robot-info.bin file not found. Will create new file");
            HashMap<String, BlackListOperation> blackList = new HashMap<>();
            HashMap<String, TradingRecord> longTradingRecordsMap = new HashMap<>();
            HashMap<String, TradingRecord> shortTradingRecordsMap = new HashMap<>();
            for (String symbol : Configuration.instrumentsFX) {
                longTradingRecordsMap.put(symbol, new BaseTradingRecord());
                shortTradingRecordsMap.put(symbol, new BaseTradingRecord(Trade.TradeType.SELL));
                logger.info("Init trading record for {}", symbol);
            }

            return new RobotInfo(longTradingRecordsMap, shortTradingRecordsMap, new HashMap<>(), blackList, 0);
        } catch (IOException e) {
            throw new SerializationFailedException("Deserialization failed. IO Exception");
        } catch (ClassNotFoundException e) {
            throw new SerializationFailedException("Derialization failed. Class not found exception");
        }

    }

    public HashMap<String, HashMap<String, BackTestInfo>> initWinningRatios() throws IOException {
        HashMap<String, HashMap<String, BackTestInfo>> winningRatioMap = new HashMap<>();
        InputStream stream = Robot.class.getClassLoader().getResourceAsStream("winning_ratios.csv");
        String strategyName = "";

        try (CSVReader csvReader = new CSVReader(new InputStreamReader(stream, Charset.forName("UTF-8")), ';', '"',0)) {
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                if(!line[0].equals(strategyName)){
                    winningRatioMap.put(line[0], new HashMap<>());
                    strategyName = line[0];
                }
                winningRatioMap.get(strategyName).put(line[1], new BackTestInfo(new BigDecimal(line[2]), Double.valueOf(line[4].replace(" profit:","")),
                        Double.valueOf(line[5].replace(" maxLost:","")), Double.valueOf(line[6].replace(" maxWon:","")),
                        Trade.TradeType.valueOf(line[7].replace(" tradeType:",""))));
            }

        } catch (IOException ioe) {
            logger.error("Unable to load winning ratios from CSV", ioe);
            throw ioe;
        }

        return winningRatioMap;
    }
}
