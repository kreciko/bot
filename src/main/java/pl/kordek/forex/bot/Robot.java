package pl.kordek.forex.bot;

import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;
import pl.kordek.forex.bot.api.XTBSymbolOperations;
import pl.kordek.forex.bot.checker.PositionChecker;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.BlackListOperation;
import pl.kordek.forex.bot.domain.RobotInfo;
import pl.kordek.forex.bot.domain.TradeInfo;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.strategy.LongStrategyBuilder;
import pl.kordek.forex.bot.strategy.ShortStrategyBuilder;
import pl.kordek.forex.bot.strategy.StrategyBuilder;
import pl.kordek.forex.bot.utils.VolumeAndSLOperations;
import pro.xstore.api.message.records.TradeRecord;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class Robot {

	private final int TYPE_OF_OPERATION_BUY = 0;
	private final int TYPE_OF_OPERATION_SELL = 1;

	private BaseBarSeries series = null;
	private BaseBarSeries parentSeries = null;
	private TradingRecord longTradingRecord = null;
	private TradingRecord shortTradingRecord = null;

	//if null then the operation is not blacklisted
	private BlackListOperation blackListOperation = null;

	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	private List<TradeRecord> openedPositions = null;
	private HashMap<String, HashMap<String, BigDecimal>> winningRatioMap = null;
	private VolumeAndSLOperations volAndSLOperations = null;
	private XTBSymbolOperations api;
	private String currentSymbol;



	public Robot(XTBSymbolOperations api, BaseBarSeries series, BaseBarSeries parentSeries, RobotInfo robotInfo,
				 HashMap<String, HashMap<String, BigDecimal>> winningRatioMap) throws XTBCommunicationException {
		this.api = api;
		this.currentSymbol = api.getSr().getSymbol().getSymbol();

		this.series = series;
		this.parentSeries = parentSeries;
		this.openedPositions = api.getOpenedPositions();
		this.longTradingRecord = robotInfo.getLongTradingRecordMap().get(currentSymbol);
		this.shortTradingRecord = robotInfo.getLongTradingRecordMap().get(currentSymbol);
		this.blackListOperation = robotInfo.getBlackList().get(currentSymbol);

		this.winningRatioMap = winningRatioMap;
		this.volAndSLOperations = new VolumeAndSLOperations(api);
	}

	public boolean runRobotIteration() throws XTBCommunicationException, InterruptedException {
			int endIndex = series.getEndIndex();

			ATRIndicator atr = new ATRIndicator(series,14);

			Indicator stopLossLongStrategy = new DifferenceIndicator(new ClosePriceIndicator(series), TransformIndicator.multiply(atr, 2));
			Indicator stopLossShortStrategy = new SumIndicator(new ClosePriceIndicator(series), TransformIndicator.multiply(atr, 2));

			boolean longPos = checkForPositions(longTradingRecord, new LongStrategyBuilder(series, parentSeries, stopLossLongStrategy));
			if(longPos)
				return true;

			boolean shortPos = checkForPositions(shortTradingRecord, new ShortStrategyBuilder(series, parentSeries, stopLossShortStrategy));
			if(shortPos)
				return true;

			return false;
	}

	private boolean checkForPositions(TradingRecord tradingRecord, StrategyBuilder strategyBuilder) throws XTBCommunicationException {
		TradeType tradeType = strategyBuilder.tradeType;
		if(blackListOperation != null && blackListOperation.getTypeOfOperation() == tradeType)
			return false;
		PositionChecker positionChecker = new PositionChecker(openedPositions);
		boolean positionOpenedAndValid = positionChecker.isPositionOpenedAndOperationValid(currentSymbol, tradeType);

		String strategyWithEntrySignal = getStrategyWithEntrySignal(series.getEndIndex(), tradingRecord,
				strategyBuilder.getStrategyList(), winningRatioMap, currentSymbol);
		String strategyWithExitSignal = getStrategyWithExitSignal(series.getEndIndex(), tradingRecord,
				strategyBuilder.getStrategyList());



		if (!positionOpenedAndValid && !strategyWithEntrySignal.isEmpty()) {
			TradeInfo tradeInfo = getEntryTradeInfo(strategyBuilder,
					strategyWithEntrySignal);
			return positionChecker.enterPosition(api, tradingRecord, tradeInfo);

		}
		if(positionOpenedAndValid) {
			TradeRecord currentSymbolTR = positionChecker.getOpenedPosition(currentSymbol);
			if(volAndSLOperations.shouldUpdateStopLoss(currentSymbolTR, tradeType))
				api.updateStopLossXTB(currentSymbolTR, tradeType);

			if (!strategyWithExitSignal.isEmpty()) {
				return positionChecker.exitPosition(api, series, tradingRecord, currentSymbolTR);
			}
		}

		return false;
	}

	private TradeInfo getEntryTradeInfo(StrategyBuilder strategyBuilder, String strategyWithEntrySignal)
			throws XTBCommunicationException {
		TradeType tradeType = strategyBuilder.tradeType.complementType();
		BigDecimal stopLoss = volAndSLOperations.calculateStopLoss(tradeType, strategyBuilder.stopLossStrategy, series);
		BigDecimal takeProfit = volAndSLOperations.calculateTakeProfit(tradeType, stopLoss);
		System.out.println(new Date() + ": "+tradeType + " strategy should ENTER on " + currentSymbol
				+ ". Bar close price "+series.getLastBar().getClosePrice() + ". Stop Loss: "+stopLoss.doubleValue()+ " Take Profit: " + takeProfit.doubleValue());

		Double volume = volAndSLOperations.getOptimalVolume(currentSymbol, strategyBuilder.assessStrategyStrength());

		if(!volAndSLOperations.volumeAndSlChecks(volume, stopLoss.doubleValue())) {
			return null;
		}

		return new TradeInfo(series, tradeType, stopLoss, takeProfit, volume, strategyWithEntrySignal);
	}



	private String getStrategyWithEntrySignal(int endIndex, TradingRecord tradingRecord, List<Strategy> baseStrategies,
											 HashMap<String, HashMap<String, BigDecimal>> winningRatioMap, String symbol) {
		for(Strategy strategy : baseStrategies) {
			HashMap<String, BigDecimal> winRatioStrategyMap = winningRatioMap.get(strategy.getName());
			if(strategy.shouldEnter(endIndex, tradingRecord)
					&& winRatioStrategyMap.containsKey(symbol)
					&& winRatioStrategyMap.get(symbol).compareTo(BigDecimal.valueOf(Configuration.minWinningRate)) > 0)
			{
				System.out.println(new Date() + ": "+strategy.getName()+" strategy signal for a symbol with winning ratio: "+winningRatioMap.get(strategy.getName()).get(symbol) +" - "+symbol);
				return strategy.getName();
			}
		}
		return "";
	}

	private String getStrategyWithExitSignal(int endIndex, TradingRecord tradingRecord, List<Strategy> baseStrategies) {
		for(Strategy strategy : baseStrategies) {
			if(strategy.shouldExit(endIndex, tradingRecord))
			{
				System.out.println(new Date() + ": "+strategy.getName()+" strategy exit signal. Current profit ratio of this strategy:");
				return strategy.getName();
			}
		}
		return "";
	}

	private void updateLiveWinningRatiosForStrategies(List<Strategy> strategies){
		TradingRecord tradingRecordForLongStrategy;
		TradingRecord tradingRecordForShortStrategy;
		Num profitableTradesLongRatio;
		Num profitableTradesShortRatio;

		for(Strategy strat : strategies) {
			List<Position> filteredLongTrades = longTradingRecord.getPositions().stream()
					.filter(e -> e.getStrategyName().equals(strat.getName()))
					.collect(Collectors.toList());
			List<Position> filteredShortTrades = shortTradingRecord.getPositions().stream()
					.filter(e -> e.getStrategyName().equals(strat.getName()))
					.collect(Collectors.toList());
			tradingRecordForLongStrategy = new BaseTradingRecord(filteredLongTrades.toArray(new Trade[0]));
			tradingRecordForShortStrategy = new BaseTradingRecord(filteredShortTrades.toArray(new Trade[0]));
//			profitableTradesLongRatio = new AverageProfitableTradesCriterion().calculate(series, tradingRecordForLongStrategy);
//			profitableTradesShortRatio = new AverageProfitableTradesCriterion().calculate(series, tradingRecordForShortStrategy);

		}
	}
}
