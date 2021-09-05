package pl.kordek.forex.bot;

import org.ta4j.core.*;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.analysis.criteria.AverageProfitableTradesCriterion;
import org.ta4j.core.num.Num;
import pl.kordek.forex.bot.api.XTB;
import pl.kordek.forex.bot.checker.PositionChecker;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.BlackListOperation;
import pl.kordek.forex.bot.domain.TradeInfo;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.indicator.DonchianIndicators;
import pl.kordek.forex.bot.strategy.*;
import pro.xstore.api.message.records.TradeRecord;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class Robot {

	private final int TYPE_OF_OPERATION_BUY = 0;
	private final int TYPE_OF_OPERATION_SELL = 1;
	private final double EXAMPLARY_VOLUME = 0.5;

	private BaseBarSeries series = null;
	private BaseBarSeries parentSeries = null;
	private TradingRecord longTradingRecord = null;
	private TradingRecord shortTradingRecord = null;

	//if null then the operation is not blacklisted
	private BlackListOperation blackListOperation = null;

	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	private List<TradeRecord> openedPositions = null;
	private HashMap<String, HashMap<String, BigDecimal>> winningRatioMap = null;
	private RobotUtilities utilities = null;
	private XTB api;
	private String currentSymbol;
	private HashMap<String, Double> liveWinningRatios;



	public Robot(XTB api, BaseBarSeries series, BaseBarSeries parentSeries, TradingRecord longTradingRecord, TradingRecord shortTradingRecord,
				 HashMap<String, HashMap<String, BigDecimal>> winningRatioMap, BlackListOperation bListOp,
				 List<TradeRecord> openedPositions)  {
		this.api = api;
		this.currentSymbol = api.getSr().getSymbol().getSymbol();

		this.series = series;
		this.parentSeries = parentSeries;
		this.openedPositions = openedPositions;
		this.longTradingRecord = longTradingRecord;
		this.shortTradingRecord = shortTradingRecord;
		this.blackListOperation = bListOp;

		this.winningRatioMap = winningRatioMap;
		this.utilities = new RobotUtilities(api);
	}

	public boolean runRobotIteration() throws XTBCommunicationException, InterruptedException {
			String symbol = api.getSr().getSymbol().getSymbol();
			int endIndex = series.getEndIndex();

			//StrategyBuilder stratBuilder = new StrategyBuilder(endIndex, series, parentSeries);

			DonchianIndicators donchianIndicators = new DonchianIndicators(series, parentSeries);
			Indicator stopLossLongStrategy = donchianIndicators.getDonchianLower();
			Indicator stopLossShortStrategy = donchianIndicators.getDonchianUpper();

			if(!Configuration.runTest) {
					boolean longPos = checkForPositions(longTradingRecord, new LongStrategyBuilder(series, parentSeries, stopLossLongStrategy));
					if(longPos)
						return true;

					boolean shortPos = checkForPositions(shortTradingRecord, new ShortStrategyBuilder(series, parentSeries, stopLossShortStrategy));
					if(shortPos)
						return true;
			}
			else {
				if(Configuration.runTestFX.equals(symbol)) {
					StrategyTester tester = new StrategyTester(series, parentSeries);
					tester.strategyTest(endIndex-Configuration.testedIndex, symbol);
				}
			}
			return false;
	}

	private boolean checkForPositions(TradingRecord tradingRecord, StrategyBuilder strategyBuilder) throws XTBCommunicationException {

		OrderType orderType = strategyBuilder.orderType;
		if(blackListOperation != null || blackListOperation.getTypeOfOperation() == orderType)
			return false;

		String strategyWithEntrySignal = utilities.getStrategyWithEntrySignal(series.getEndIndex(), tradingRecord,
				strategyBuilder.getStrategyList(), winningRatioMap, currentSymbol);
		String strategyWithExitSignal = utilities.getStrategyWithExitSignal(series.getEndIndex(), tradingRecord,
				strategyBuilder.getStrategyList());

		PositionChecker positionChecker = new PositionChecker(strategyBuilder, openedPositions);
		boolean positionOpenedAndValid = positionChecker.isPositionOpenedAndOperationValid(currentSymbol);

		if (!positionOpenedAndValid && !strategyWithEntrySignal.isEmpty()) {
			TradeInfo tradeInfo = getEntryTradeInfo(strategyBuilder.orderType, strategyBuilder.assessStrategyStrength(),
					strategyWithEntrySignal);
			return positionChecker.enterPosition(api, tradingRecord, tradeInfo);

		}
		if(positionOpenedAndValid) {
			TradeRecord currentSymbolTR = positionChecker.getSymbolTradeRecord(currentSymbol);
			if(shouldUpdateStopLoss(currentSymbolTR, orderType))
				api.updateStopLossXTB(currentSymbolTR, orderType);

			if (!strategyWithExitSignal.isEmpty()) {
				return positionChecker.exitPosition(api, tradingRecord, currentSymbolTR);
			}
		}

		return false;
	}

	private TradeInfo getEntryTradeInfo(OrderType orderType, Double strategyStrength, String strategyWithEntrySignal)
			throws XTBCommunicationException {
		BigDecimal stopLoss = utilities.calculateStopLoss(orderType, series, strategyWithEntrySignal);
		BigDecimal takeProfit = utilities.calculateTakeProfit(orderType, stopLoss);
		System.out.println(new Date() + ": "+orderType + " strategy should ENTER on " + currentSymbol
				+ ". Bar close price "+series.getLastBar().getClosePrice() + ". Stop Loss: "+stopLoss.doubleValue()+ " Take Profit: " + takeProfit.doubleValue());

		Double volume = utilities.getOptimalVolume(currentSymbol, strategyStrength);

		if(!utilities.volumeAndSlChecks(volume, stopLoss.doubleValue())) {
			return null;
		}

		return new TradeInfo(series, orderType, stopLoss, takeProfit, volume, strategyWithEntrySignal);
	}

	private boolean shouldUpdateStopLoss(TradeRecord xtbTR, OrderType orderType){
		if(!Configuration.updateStopLoss) {
			return false;
		}
		if (xtbTR == null || isTrOperationInvalid(orderType, xtbTR.getCmd()))
			return false;

		if(!utilities.shouldUpdateStopLoss(
				BigDecimal.valueOf(xtbTR.getSl()),
				BigDecimal.valueOf(xtbTR.getTp()),
				orderType)){
			return false;
		}

		return true;
	}


	private boolean isTrOperationInvalid(OrderType orderType, int existingOperationCode){
		return (existingOperationCode == TYPE_OF_OPERATION_SELL && orderType  == OrderType.BUY)
				|| (existingOperationCode == TYPE_OF_OPERATION_BUY && orderType == OrderType.SELL);
	}

	private void updateLiveWinningRatiosForStrategies(List<Strategy> strategies){
		TradingRecord tradingRecordForLongStrategy;
		TradingRecord tradingRecordForShortStrategy;
		Num profitableTradesLongRatio;
		Num profitableTradesShortRatio;

		for(Strategy strat : strategies) {
			List<Trade> filteredLongTrades = longTradingRecord.getTrades().stream()
					.filter(e -> e.getStrategyName().equals(strat.getName()))
					.collect(Collectors.toList());
			List<Trade> filteredShortTrades = shortTradingRecord.getTrades().stream()
					.filter(e -> e.getStrategyName().equals(strat.getName()))
					.collect(Collectors.toList());
			tradingRecordForLongStrategy = new BaseTradingRecord(filteredLongTrades.toArray(new Trade[0]));
			tradingRecordForShortStrategy = new BaseTradingRecord(filteredShortTrades.toArray(new Trade[0]));
			profitableTradesLongRatio = new AverageProfitableTradesCriterion().calculate(series, tradingRecordForLongStrategy);
			profitableTradesShortRatio = new AverageProfitableTradesCriterion().calculate(series, tradingRecordForShortStrategy);

		}
	}
}
