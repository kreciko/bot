package pl.kordek.forex.bot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Order;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.DoubleNum;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.BlackListOperation;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.strategy.StopLossHelper;
import pl.kordek.forex.bot.strategy.StrategyBuilder;
import pl.kordek.forex.bot.strategy.StrategyTester;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.records.TradeTransInfoRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.MarginLevelResponse;
import pro.xstore.api.message.response.MarginTradeResponse;
import pro.xstore.api.message.response.SymbolResponse;
import pro.xstore.api.message.response.TradeTransactionResponse;
import pro.xstore.api.sync.SyncAPIConnector;

public class Robot {

	private final int TYPE_OF_OPERATION_BUY = 0;
	private final int TYPE_OF_OPERATION_SELL = 1;
	private final double EXAMPLARY_VOLUME = 0.5;

	private BaseBarSeries series = null;
	private BaseBarSeries helperSeries = null;
	private TradingRecord longTradingRecord = null;
	private TradingRecord shortTradingRecord = null;
	private SymbolResponse symbolResponse = null;
	private double strategyStrength = 0.0;

	//if null then the operation is not blacklisted
	private BlackListOperation blackListOperation = null;

	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	private List<TradeRecord> openedPositions = null;

	private SyncAPIConnector connector = null;

	private Boolean tradeSuccessful = false;



	public Robot(BaseBarSeries series, BaseBarSeries helperSeries, TradingRecord longTradingRecord, TradingRecord shortTradingRecord, BlackListOperation bListOp,
			List<TradeRecord> openedPositions, SymbolResponse symbolResponse, SyncAPIConnector connector) {
		this.series = series;
		this.helperSeries = helperSeries;
		this.openedPositions = openedPositions;
		this.symbolResponse = symbolResponse;
		this.connector = connector;
		this.longTradingRecord = longTradingRecord;
		this.shortTradingRecord = shortTradingRecord;
		this.blackListOperation = bListOp;
	}

	public void runRobotIteration() throws XTBCommunicationException, InterruptedException {
			//we want to have max 1 trade in each robot run
			if(tradeSuccessful) {
				return;
			}

			String symbol = symbolResponse.getSymbol().getSymbol();
			int endIndex = series.getEndIndex();

			StrategyBuilder stratBuilder = new StrategyBuilder(endIndex, series, helperSeries);

			List<Strategy> longStrategies = stratBuilder.buildLongStrategies();
			List<Strategy> shortStrategies = stratBuilder.buildShortStrategies();

			if(!Configuration.runTest) {
				//check for long. take blacklist into consideration
				if(blackListOperation == null || blackListOperation.getTypeOfOperation() == TYPE_OF_OPERATION_SELL){
					tradeSuccessful = checkForPositions(endIndex, symbol, longStrategies, shortStrategies, TRADE_OPERATION_CODE.BUY);
				}
				//check for short.  take blacklist into consideration
				if(blackListOperation == null || blackListOperation.getTypeOfOperation() == TYPE_OF_OPERATION_BUY) {
					tradeSuccessful = checkForPositions(endIndex, symbol, shortStrategies, longStrategies, TRADE_OPERATION_CODE.SELL);
				}
			}
			else {
				if(Configuration.runTestFX.equals(symbol)) {
					StrategyTester tester = new StrategyTester(series, helperSeries);
					tester.strategyTest(endIndex-Configuration.testedIndex, symbol);
				}
			}
	}

	private boolean checkForPositions(int endIndex, String symbol, List<Strategy> baseStrategy, List<Strategy> oppositeStrategy,
			TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException, InterruptedException {
		TradingRecord tradingRecord = operationCode == TRADE_OPERATION_CODE.BUY ? longTradingRecord : shortTradingRecord;
		String strategyType = operationCode == TRADE_OPERATION_CODE.BUY ? "LONG" : "SHORT";
		int typeOfOperation = operationCode == TRADE_OPERATION_CODE.BUY ? 0 : 1;

		boolean shouldEnter = checkShouldEnter(endIndex, tradingRecord, baseStrategy);
		boolean shouldExit = checkShouldExit(endIndex, tradingRecord, baseStrategy);
		boolean shouldOppositeEnter = checkShouldEnter(endIndex, tradingRecord, oppositeStrategy);
		boolean isSymbolOpenedXTB = openedPositions.stream().map(e -> e.getSymbol()).anyMatch(e -> e.equals(symbol));
		boolean openedSymbolOperationValid = openedPositions.stream().filter(e -> e.getSymbol().equals(symbol)).anyMatch(e -> e.getCmd() == typeOfOperation);

		if (!isSymbolOpenedXTB && shouldEnter) {
			BigDecimal stopLoss = calculateStopLoss(operationCode, series);
			BigDecimal takeProfit = calculateTakeProfit(operationCode, stopLoss);
			System.out.println(new Date() + ": "+strategyType + " strategy should ENTER on " + symbol
					+ ". Bar close price "+series.getLastBar().getClosePrice() + ". Stop Loss: "+stopLoss.doubleValue()+ " Take Profit: " + takeProfit.doubleValue());

			this.strategyStrength = StrategyBuilder.assessStrategyStrength(operationCode, series);
			System.out.println(new Date() + ": Strategy strength: "+strategyStrength);
			if(!Configuration.considerStratetyStrength)
				strategyStrength = 1.0;

			Double volume = getOptimalVolume(symbol);
			multiplyVolByStrategyStrength(volume);


			if(!volumeAndSlChecks(volume, stopLoss.doubleValue())) {
				return false;
			}


			boolean entered = tradingRecord.enter(endIndex, series.getLastBar().getClosePrice(),
					DoubleNum.valueOf(EXAMPLARY_VOLUME));
			if (entered) {
				enterXTB(operationCode, stopLoss, takeProfit, volume);
				System.out.println(new Date() + ": Opened in XTB successfully");
				return true;
			} else {
				System.out.println(new Date() + ": Didn't enter "+strategyType+" position for: " + symbol);
			}
		}
		if(isSymbolOpenedXTB && openedSymbolOperationValid) {
			updateStopLossXTB(operationCode);
			if (shouldExit) {
				System.out.println(new Date() + ": "+strategyType + " strategy should EXIT on " + symbol + ". Exit strategy valid:" + shouldExit);
				boolean exited = tradingRecord.exit(endIndex, series.getLastBar().getClosePrice(),
						DoubleNum.valueOf(EXAMPLARY_VOLUME));
				if (exited) {
					exitXTB(operationCode);
					System.out.println(new Date() + ": Closed in XTB successfully");
					return true;
				} else {
					System.out.println(new Date() + ": Didn't exit "+strategyType+" position for: " + symbol);
				}
			}
		}

		return false;
	}

	Boolean checkShouldEnter(int endIndex, TradingRecord tradingRecord, List<Strategy> baseStrategies) {
		for(Strategy strategy : baseStrategies) {
			if(strategy.shouldEnter(endIndex, tradingRecord))
			{
				System.out.println(new Date() + ": "+strategy.getName()+" strategy signal. Current profit ratio of this strategy:");
				return true;
			}
		}
		return false;
	}

	Boolean checkShouldExit(int endIndex, TradingRecord tradingRecord, List<Strategy> baseStrategies) {
		for(Strategy strategy : baseStrategies) {
			if(strategy.shouldExit(endIndex, tradingRecord))
			{
				System.out.println(new Date() + ": "+strategy.getName()+" strategy exit signal. Current profit ratio of this strategy:");
				return true;
			}
		}
		return false;
	}


	private void enterXTB(TRADE_OPERATION_CODE operationCode, BigDecimal stopLoss, BigDecimal takeProfit, double volume) throws XTBCommunicationException {
		SymbolRecord symbolRecord = symbolResponse.getSymbol();
		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
				TRADE_TRANSACTION_TYPE.OPEN, symbolRecord.getBid(), stopLoss.doubleValue() , takeProfit.doubleValue() , symbolRecord.getSymbol(), volume, 0L, "" , 0L);

		try {
			APICommandFactory.executeTradeTransactionCommand(connector,ttInfoRecord);
			Thread.sleep(250);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse | InterruptedException e1) {
			System.out.println(new Date() + ": Failed to open in XTB" + symbolRecord.getSymbol());
			throw new XTBCommunicationException("Couldn't open the position in XTB due to communication problems: "+symbolRecord.getSymbol());
		}
	}

	private void exitXTB(TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);
		try {
			if(tr==null) return;
			TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
					TRADE_TRANSACTION_TYPE.CLOSE, tr.getClose_price(), tr.getSl(), tr.getTp(), tr.getSymbol(), tr.getVolume(), tr.getOrder(), tr.getComment(), tr.getExpiration());
			APICommandFactory.executeTradeTransactionCommand(connector, ttInfoRecord);
			Thread.sleep(250);
		} catch (APICommandConstructionException | APIReplyParseException | APICommunicationException
				| APIErrorResponse | InterruptedException e1) {
			System.out.println(new Date() + ": Failed to close in XTB" + tr.getSymbol());
			throw new XTBCommunicationException("Couldn't close the position in XTB due to communication problems: "+tr.getSymbol());
		}

	}

	private void updateStopLossXTB(TRADE_OPERATION_CODE operationCode) throws XTBCommunicationException {
		TradeRecord tr = openedPositions.stream().filter(e-> e.getSymbol().equals(symbolResponse.getSymbol().getSymbol())).findAny()
                .orElse(null);

		if (tr == null || (tr.getCmd() == TYPE_OF_OPERATION_SELL && operationCode == TRADE_OPERATION_CODE.BUY)
				|| tr.getCmd() == TYPE_OF_OPERATION_BUY && operationCode == TRADE_OPERATION_CODE.SELL)
			return;

		if(!Configuration.updateStopLoss) {
			return;
		}

		//checks if profit is in proportion 1:1 with stop loss. If yes, setting sl to open price value to avoid risk
		if(!shouldUpdateStopLoss(BigDecimal.valueOf(tr.getSl()), BigDecimal.valueOf(tr.getTp()), operationCode))
			return;

		double stopLoss = tr.getOpen_price();

		TradeTransInfoRecord ttInfoRecord = new TradeTransInfoRecord(operationCode,
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

	private Double getMarginNeeded(Double volume) throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
		MarginTradeResponse marginTradeResponse;
		marginTradeResponse = APICommandFactory.executeMarginTradeCommand(connector, symbolResponse.getSymbol().getSymbol(), volume);
        return marginTradeResponse.getMargin();
	}

	private boolean isEnoughMargin(Double volume) throws XTBCommunicationException {
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



	private BigDecimal calculateStopLoss(TRADE_OPERATION_CODE operationCode, BaseBarSeries series){
		OrderType orderType = operationCode == TRADE_OPERATION_CODE.BUY ? OrderType.BUY : OrderType.SELL;
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
		BigDecimal spread = BigDecimal.valueOf(symbolResponse.getSymbol().getSpreadRaw());
        BigDecimal stopLoss = StopLossHelper.getNewStopLoss(series, orderType, Configuration.stopLossBarCount).scaleByPowerOfTen(-precisionNumber);

        if(stopLoss.doubleValue() != 0 && operationCode == TRADE_OPERATION_CODE.SELL) {
        	stopLoss = stopLoss.add(spread);
        }

        return stopLoss;
    }

	private Boolean shouldUpdateStopLoss(BigDecimal stopLossPrice, BigDecimal takeProfitPrice, TRADE_OPERATION_CODE operationCode) {
		BigDecimal stopLossMinusBid = stopLossPrice.subtract(BigDecimal.valueOf(symbolResponse.getSymbol().getBid())).abs();
		BigDecimal takeProfitMinusBid = takeProfitPrice.subtract(BigDecimal.valueOf(symbolResponse.getSymbol().getBid())).abs();

		BigDecimal stopLossMinusAsk = stopLossPrice.subtract(BigDecimal.valueOf(symbolResponse.getSymbol().getAsk())).abs();
		BigDecimal takeProfitMinusAsk = takeProfitPrice.subtract(BigDecimal.valueOf(symbolResponse.getSymbol().getAsk())).abs();


		if(operationCode == TRADE_OPERATION_CODE.SELL) {
			return enoughProfitForSLChange(stopLossMinusAsk, takeProfitMinusAsk);
		}
		else {
			return enoughProfitForSLChange(stopLossMinusBid, takeProfitMinusBid);
		}
	}

	//calculates if we should update the stoploss to the opening price
	//if we have a ratio 1:1 between profit and stoploss distance then we update the stoploss
	private Boolean enoughProfitForSLChange(BigDecimal stopLossMinusPrice, BigDecimal takeProfitMinusPrice) {
		BigDecimal ratio = BigDecimal.valueOf(2.0).
				divide(BigDecimal.valueOf(Configuration.takeProfitVsStopLossCoeff).subtract(BigDecimal.ONE));
		return stopLossMinusPrice.divide(takeProfitMinusPrice, 2, RoundingMode.HALF_UP)
				.compareTo(ratio) >= 0;
	}

	private BigDecimal calculateTrailingStopLoss(TradeRecord tr, BaseBarSeries series){
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
		BigDecimal closePrice = BigDecimal.valueOf(series.getLastBar().getClosePrice().doubleValue()).scaleByPowerOfTen(-precisionNumber);
		BigDecimal trail = BigDecimal.valueOf(tr.getSl()).subtract(new BigDecimal(tr.getClose_price()));

        return BigDecimal.valueOf(StopLossHelper.getNewTrailingStopLoss(closePrice, trail, new BigDecimal(tr.getSl())));
    }


	private BigDecimal calculateTakeProfit(TRADE_OPERATION_CODE operationCode, BigDecimal stopLoss){
		OrderType orderType = operationCode == TRADE_OPERATION_CODE.BUY ? OrderType.BUY : OrderType.SELL;
		BigDecimal takeProfitVsStopLossCoeffBD = BigDecimal.valueOf(Configuration.takeProfitVsStopLossCoeff);
		Integer precisionNumber = symbolResponse.getSymbol().getPrecision();
		BigDecimal spread = BigDecimal.valueOf(symbolResponse.getSymbol().getSpreadRaw());

		//we add the spread for SELL because the imported prices are bid
		BigDecimal sellTP = BigDecimal.valueOf(symbolResponse.getSymbol().getBid())
				.subtract(stopLoss.subtract(BigDecimal.valueOf(symbolResponse.getSymbol().getBid())).multiply(takeProfitVsStopLossCoeffBD))
				.setScale(precisionNumber, RoundingMode.HALF_UP).add(spread);
        BigDecimal buyTP = BigDecimal.valueOf(symbolResponse.getSymbol().getAsk())
        		.add(BigDecimal.valueOf(symbolResponse.getSymbol().getAsk()).subtract(stopLoss).multiply(takeProfitVsStopLossCoeffBD))
        		.setScale(precisionNumber, RoundingMode.HALF_UP);
		return orderType == OrderType.BUY ? buyTP : sellTP;
    }


	private double getOptimalVolume(String symbol) throws XTBCommunicationException {
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

	void multiplyVolByStrategyStrength(Double volume) {
		volume = BigDecimal.valueOf(volume).multiply(BigDecimal.valueOf(strategyStrength)).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
	}


	boolean volumeAndSlChecks(double volume, double stopLoss) throws XTBCommunicationException {
		if(stopLoss == 0.0) {
			System.out.println(new Date() + ": Couldnt calculate the stoploss that would be below the max allowed percentage. Skipping trade");
			return false;
		}

		if(!isEnoughMargin(volume)) {
			System.out.println(new Date() + ": Not enough margin");
			return false;
		}

		if(volume == 0.0) {
			System.out.println(new Date() + ": Calculated volume is below 0.01. Skipping the trade");
			return false;
		}

		return true;
	}

}
