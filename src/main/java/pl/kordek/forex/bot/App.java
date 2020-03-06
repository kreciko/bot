package pl.kordek.forex.bot;

import static java.util.stream.Collectors.toList;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.TradingRecord;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.SerializationFailedException;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.RateInfoRecord;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.ChartResponse;
import pro.xstore.api.message.response.LoginResponse;
import pro.xstore.api.message.response.MarginLevelResponse;
import pro.xstore.api.message.response.MarginTradeResponse;
import pro.xstore.api.message.response.SymbolResponse;
import pro.xstore.api.message.response.TradesResponse;
import pro.xstore.api.sync.Credentials;
import pro.xstore.api.sync.SyncAPIConnector;

/**
 * Hello world!
 *
 */
public class App {
	private static final int LONG_INDEX=0; 
	private static final int SHORT_INDEX=1;
	
	private static HashMap<PERIOD_CODE, Duration> durationMap = new HashMap<>();
	private static HashMap<String, BaseBarSeries> baseBarSeriesMap = new HashMap<>();
	private static HashMap<String, TradingRecord> longTradingRecordsMap = null;
	private static HashMap<String, TradingRecord> shortTradingRecordsMap = null;
	private static List<TradeRecord> openedPositions = new ArrayList<>();

	public static void main(String[] args) {
		SyncAPIConnector connector = null;
		buildDurationMap();
		String tradingRecordsFileLocation = args.length == 0 ? "trading-record.bin" : args[0];

		try {
			Thread.sleep(5000);

			deserializeTradingRecords(tradingRecordsFileLocation);

			connector = new SyncAPIConnector(Configuration.server);
			LoginResponse loginResponse = APICommandFactory.executeLoginCommand(connector,
					new Credentials(Configuration.username, Configuration.password));

			if (loginResponse != null && !loginResponse.getStatus()) {
				throw new XTBCommunicationException("Failed to login");
			}

			populateBaseBarSeriesMap(connector);

			getOpenedPositions(connector);
			
			Double optimalVolume = getOptimalVolume(connector);
			Robot.setVolume(optimalVolume);

			boolean longTRecordUpdateNeeded = false;
			boolean shortTRecordUpdateNeeded = false;
			int endIndex = baseBarSeriesMap.get(Configuration.oneFX[0]).getEndIndex();
			for (String symbol : Configuration.instrumentsFX) {
				SymbolResponse sr = APICommandFactory.executeSymbolCommand(connector, symbol);
						
				longTRecordUpdateNeeded = updateTradingRecord(endIndex, symbol, longTradingRecordsMap.get(symbol));
				shortTRecordUpdateNeeded = updateTradingRecord(endIndex, symbol, shortTradingRecordsMap.get(symbol));
				
				//something happened in xtb (like stop loss). trading record is updated but we skip this symbol
				if(longTRecordUpdateNeeded || shortTRecordUpdateNeeded) {
					continue;
				}
				
				Robot robot = new Robot(baseBarSeriesMap.get(symbol), longTradingRecordsMap.get(symbol),
						shortTradingRecordsMap.get(symbol), openedPositions, sr, connector);
				robot.runRobotIteration();

				Thread.sleep(500);
			}

			serializeTradingRecords(tradingRecordsFileLocation);

			getOpenedPositions(connector);
			System.out.println(new Date() + ": Last robot iteration. Positions opened: "
					+ openedPositions.stream().map(e -> e.getSymbol()).collect(toList()));
			System.out.println();

		} catch (XTBCommunicationException xtbEx) {
			System.out.println(new Date() +": XTB Exception:" + xtbEx.getMessage());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println(new Date() +": Other Exception:" + e.getMessage());
			return;
		}

	}

	private static boolean updateTradingRecord(int endIndex, String symbol, TradingRecord tradingRecord) {
		if(null == tradingRecord) {
			System.out.println(new Date() +": There shouldn't be null values in trading record map!");
			return true;
		}
		//check if trading record has a trade that is opened (not new), but it's not existing in XTB 
		if(!tradingRecord.getCurrentTrade().isNew() && !openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol))) {
			System.out.println(new Date() + ": Trade record was outdated for symbol "+symbol+". Updating the trade record");
			boolean exited = tradingRecord.exit(endIndex);
			if(!exited) {
				System.out.println(new Date() + ": Exit not successful");
			}
			return true;
		}
		
		return false;
	}

	private static BaseBarSeries convertRateInfoToBarSeries(List<RateInfoRecord> rateInfoRecords, String symbol) {
		List<Bar> barList = new ArrayList<>();

		for (RateInfoRecord rateInfoRecord : rateInfoRecords) {
			ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(rateInfoRecord.getCtm()),
					ZoneId.systemDefault());
			BigDecimal open = BigDecimal.valueOf(rateInfoRecord.getOpen()).setScale(5, RoundingMode.HALF_UP);
			BigDecimal high = BigDecimal.valueOf(rateInfoRecord.getOpen())
					.add(BigDecimal.valueOf(rateInfoRecord.getHigh())).setScale(5, RoundingMode.HALF_UP);
			BigDecimal low = BigDecimal.valueOf(rateInfoRecord.getOpen())
					.add(BigDecimal.valueOf(rateInfoRecord.getLow())).setScale(5, RoundingMode.HALF_UP);
			BigDecimal close = BigDecimal.valueOf(rateInfoRecord.getOpen())
					.add(BigDecimal.valueOf(rateInfoRecord.getClose())).setScale(5, RoundingMode.HALF_UP);

			Double openD = open.doubleValue();
			Double highD = high.doubleValue();
			Double lowD = low.doubleValue();
			Double closeD = close.doubleValue();

			BaseBar Bar = new BaseBar(durationMap.get(Configuration.candlePeriod), endTime, openD, highD, lowD, closeD,
					rateInfoRecord.getVol());
			barList.add(Bar);
		}
		return new BaseBarSeries(symbol, barList);
	}

	private static void populateBaseBarSeriesMap(SyncAPIConnector connector) throws APICommandConstructionException,
			APICommunicationException, APIReplyParseException, APIErrorResponse, ParseException {
		ChartResponse cr = null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = sdf.parse(Configuration.sinceDate);
		long millis = date.getTime();
		for (String symbol : Configuration.instrumentsFX) {
			cr = APICommandFactory.executeChartLastCommand(connector, symbol, Configuration.candlePeriod, millis);

			List<RateInfoRecord> rateInfos = cr.getRateInfos();

			// rateInfos.size()-1 - get last full candle. New one is still changing
			BaseBarSeries baseBarInfos = convertRateInfoToBarSeries(rateInfos.subList(0, rateInfos.size() - 1), symbol);

			baseBarSeriesMap.put(symbol, baseBarInfos);
		}
	}

	private static void buildDurationMap() {
		durationMap.put(PERIOD_CODE.PERIOD_M1, Duration.ofMinutes(1));
		durationMap.put(PERIOD_CODE.PERIOD_M5, Duration.ofMinutes(5));
		durationMap.put(PERIOD_CODE.PERIOD_M15, Duration.ofMinutes(15));
		durationMap.put(PERIOD_CODE.PERIOD_M30, Duration.ofMinutes(30));
		durationMap.put(PERIOD_CODE.PERIOD_H1, Duration.ofHours(1));
		durationMap.put(PERIOD_CODE.PERIOD_H4, Duration.ofHours(4));
		durationMap.put(PERIOD_CODE.PERIOD_D1, Duration.ofDays(1));
	}

	private static void getOpenedPositions(SyncAPIConnector connector) throws APICommandConstructionException,
			APIReplyParseException, APICommunicationException, APIErrorResponse {
		TradesResponse tradeResponse = APICommandFactory.executeTradesCommand(connector, true);
		openedPositions = tradeResponse.getTradeRecords();
	}
	
	private static double getOptimalVolume(SyncAPIConnector connector) throws XTBCommunicationException {
		MarginLevelResponse marginLevelResponse;
        try {
			marginLevelResponse = APICommandFactory.executeMarginLevelCommand(connector);
			BigDecimal balance = BigDecimal.valueOf(marginLevelResponse.getMargin_free()+marginLevelResponse.getMargin());
			BigDecimal balancePerTrade = balance.divide(BigDecimal.valueOf(7L), 2, RoundingMode.HALF_UP);
			
			BigDecimal optimalVolume = BigDecimal.valueOf(1);
			
			MarginTradeResponse marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, Configuration.oneFX[0], optimalVolume.doubleValue());
			BigDecimal marginRatio = balancePerTrade.divide(BigDecimal.valueOf(marginTradeResponse.getMargin()) , 2, RoundingMode.HALF_UP);
			
			optimalVolume = optimalVolume.multiply(marginRatio).setScale(2, RoundingMode.HALF_UP);
			return optimalVolume.doubleValue();
			
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse e) {
			throw new XTBCommunicationException("Couldn't get optimal volume");
		}

	}

	private static void serializeTradingRecords(String tradingRecordsFileLocation) throws SerializationFailedException
	{
		List<HashMap<String, TradingRecord>> tradingRecordsMaps = new ArrayList<>();
		tradingRecordsMaps.add(longTradingRecordsMap);
		tradingRecordsMaps.add(shortTradingRecordsMap);
		try (ObjectOutputStream outputStream = new ObjectOutputStream(
				new FileOutputStream(tradingRecordsFileLocation))) {
			outputStream.writeObject(tradingRecordsMaps);
		} catch (IOException e) {
			throw new SerializationFailedException("Serialization failed");
		}
		
	}

	@SuppressWarnings({ "unchecked", "unused" })
	private static void deserializeTradingRecords(String tradingRecordsFileLocation) throws SerializationFailedException
			 {
		try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(tradingRecordsFileLocation))) {
			List<HashMap<String, TradingRecord>> tradingRecordsMaps = (List<HashMap<String, TradingRecord>>) inputStream.readObject();
			longTradingRecordsMap = tradingRecordsMaps.get(LONG_INDEX);
			shortTradingRecordsMap = tradingRecordsMaps.get(SHORT_INDEX);
		} catch (FileNotFoundException ex) {
			System.out.println("trading-record.bin file not found. Will create new file");
			longTradingRecordsMap = new HashMap<String, TradingRecord>();
			shortTradingRecordsMap = new HashMap<String, TradingRecord>();
			for (String symbol : Configuration.instrumentsFX) {
				longTradingRecordsMap.put(symbol, new BaseTradingRecord());
				shortTradingRecordsMap.put(symbol, new BaseTradingRecord(OrderType.SELL));
				System.out.println("Init trading record for " + symbol);
			}
		} catch (IOException e) {
			throw new SerializationFailedException("Deserialization failed. IO Exception");
		} catch (ClassNotFoundException e) {
			throw new SerializationFailedException("Derialization failed. Class not found exception");
		}
	}
}
