package pl.kordek.forex.bot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import pl.kordek.forex.bot.api.BrokerAPI;
import pl.kordek.forex.bot.api.xtb.XTBAPIImpl;
import pl.kordek.forex.bot.checker.PositionChecker;
import pl.kordek.forex.bot.checker.TradeQualityChecker;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.*;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.indicator.GeneralIndicators;
import pl.kordek.forex.bot.strategy.LongStrategyBuilder;
import pl.kordek.forex.bot.strategy.ShortStrategyBuilder;
import pl.kordek.forex.bot.strategy.StrategyBuilder;
import pl.kordek.forex.bot.utils.InitOperations;
import pl.kordek.forex.bot.utils.VolumeAndSLOperations;
import pro.xstore.api.message.error.APICommunicationException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class Robot {
	private static final Logger logger = LogManager.getLogger(Robot.class);

	private final int TYPE_OF_OPERATION_BUY = 0;
	private final int TYPE_OF_OPERATION_SELL = 1;

	private BaseBarSeries series = null;
	private BaseBarSeries parentSeries = null;
	private TradingRecord longTradingRecord = null;
	private TradingRecord shortTradingRecord = null;

	//if null then the operation is not blacklisted
	private BlackListOperation blackListOperation = null;

	//TradeRecord is a class from xtb API. Don't mistake with TradingRecord from ta4j
	//private List<TradeRecord> openedPositions = null;
	private List<PositionInfo> openedPositions = null;
	private HashMap<String, HashMap<String, BackTestInfo>> winningRatioMap = null;
	//private XTBSymbolOperations api;
	private BrokerAPI api;
	private String currentSymbol;


	public Robot(BrokerAPI api, String symbol, BaseBarSeries series, BaseBarSeries parentSeries, RobotInfo robotInfo,
				 HashMap<String, HashMap<String, BackTestInfo>> winningRatioMap, List<PositionInfo> openedPositions) throws XTBCommunicationException, APICommunicationException {
		this.api = api;
		this.currentSymbol = symbol;
		this.openedPositions = openedPositions;
		this.series = series;
		this.parentSeries = parentSeries;
		this.longTradingRecord = robotInfo.getLongTradingRecordMap().get(currentSymbol);
		this.shortTradingRecord = robotInfo.getLongTradingRecordMap().get(currentSymbol);
		this.blackListOperation = robotInfo.getBlackList().get(currentSymbol);

		this.winningRatioMap = winningRatioMap;
	}

	public boolean runRobotIteration() throws APICommunicationException {
			PositionChecker positionChecker = new PositionChecker(openedPositions);

			ATRIndicator atr = new ATRIndicator(series,14);

			Indicator stopLossLongStrategy = new DifferenceIndicator(new ClosePriceIndicator(series), TransformIndicator.multiply(atr, 2));
			Indicator stopLossShortStrategy = new SumIndicator(new ClosePriceIndicator(series), TransformIndicator.multiply(atr, 2));

			Boolean shouldCloseOnStrongRSI = shouldCloseOnStrongRSI(positionChecker);


			boolean longPos = checkForPositions(longTradingRecord, positionChecker, new LongStrategyBuilder(series, parentSeries, stopLossLongStrategy, false, shouldCloseOnStrongRSI));
			if(longPos)
				return true;

			boolean shortPos = checkForPositions(shortTradingRecord, positionChecker, new ShortStrategyBuilder(series, parentSeries, stopLossShortStrategy, false, shouldCloseOnStrongRSI));
			if(shortPos)
				return true;

			return false;
	}

	private boolean checkForPositions(TradingRecord tradingRecord, PositionChecker positionChecker, StrategyBuilder strategyBuilder) throws APICommunicationException {
		TradeType tradeType = strategyBuilder.tradeType;
		VolumeAndSLOperations volumeAndSLOperations = new VolumeAndSLOperations(api);
		TradeQualityChecker qualityChecker = new TradeQualityChecker(api);

		if(blackListOperation != null && blackListOperation.getTypeOfOperation() == tradeType)
			return false;

		boolean positionOpenedAndTradeTypeValid = positionChecker.isPositionOpenedAndOperationValid(currentSymbol, tradeType);

		if(positionOpenedAndTradeTypeValid) {
			PositionInfo currentPositionInfo = positionChecker.getOpenedPosition(currentSymbol);
			if(volumeAndSLOperations.shouldUpdateStopLoss(currentPositionInfo, tradeType)) {
				currentPositionInfo.setStopLoss(currentPositionInfo.getOpenPrice());
				api.update(tradeType, currentPositionInfo);
			}

			Optional<Strategy> exitStrategy = strategyBuilder.getStrategyList().stream()
					.filter(e-> e.getName().equals(currentPositionInfo.getComment()))
					.findAny();
			if(exitStrategy.isPresent()) {
				Boolean exitSignalExists = exitStrategy.get().shouldExit(series.getEndIndex(), tradingRecord);


				if (exitSignalExists) {
					return positionChecker.exitPosition(api, series, tradingRecord, currentPositionInfo);
				}
			}
		}
		else {
			int endIndex = series.getEndIndex();
			String strategyWithEntrySignal = getStrategyWithEntrySignal(endIndex, tradingRecord,
					strategyBuilder.getStrategyList(), winningRatioMap, currentSymbol);
			if (!strategyWithEntrySignal.isEmpty()) {
				TradeInfo tradeInfo = getEntryTradeInfo(strategyBuilder, strategyWithEntrySignal, volumeAndSLOperations);
				if (qualityChecker.checkNoOpenCorrelations(currentSymbol, openedPositions) &&
						qualityChecker.checkTradeNotTooRisky(tradeInfo) &&
						qualityChecker.checkEntryBarNotTooBig(series) &&
						qualityChecker.notBlackListed(blackListOperation, tradeType) &&
						qualityChecker.rsiNotTooStrong(series, tradeType))
					return positionChecker.enterPosition(api, tradingRecord, tradeInfo);
			}
		}

		return false;
	}

	private TradeInfo getEntryTradeInfo(StrategyBuilder strategyBuilder, String strategyWithEntrySignal, VolumeAndSLOperations volumeAndSLOperations)
			throws APICommunicationException {
		TradeType tradeType = strategyBuilder.tradeType;
		BigDecimal stopLoss = volumeAndSLOperations.calculateStopLoss(tradeType, strategyBuilder.stopLossStrategy, series);
		BigDecimal takeProfit = volumeAndSLOperations.calculateTakeProfit(tradeType, stopLoss);
		BigDecimal closePrice = BigDecimal.valueOf(series.getLastBar().getClosePrice().doubleValue());
		Double volume = volumeAndSLOperations.getOptimalVolume(currentSymbol, strategyBuilder.assessStrategyStrength(), openedPositions.size());

		if(!volumeAndSLOperations.volumeAndSlChecks(volume, stopLoss.doubleValue())) {
			return null;
		}

		return new TradeInfo(series, tradeType, closePrice, stopLoss, BigDecimal.ZERO, takeProfit, volume, strategyWithEntrySignal);
	}



	private String getStrategyWithEntrySignal(int endIndex, TradingRecord tradingRecord, List<Strategy> baseStrategies,
											 HashMap<String, HashMap<String, BackTestInfo>> winningRatioMap, String symbol) {
		for(Strategy strategy : baseStrategies) {
			if(strategy.shouldEnter(endIndex, tradingRecord))
			{
				if(!winningRatioMap.get(strategy.getName()).containsKey(symbol)){
					logger.info("Planned to enter {} with strategy {}. But strategy not in the winning ratio file", symbol,strategy.getName());
					continue;
				}
				logger.info("{} strategy signal for a symbol - {}", strategy.getName(),symbol);
				return strategy.getName();
			}
		}
		return "";
	}


	private Boolean shouldCloseOnStrongRSI(PositionChecker positionChecker){
		PositionInfo currentPositionInfo = positionChecker.getOpenedPosition(currentSymbol);

		//if stop loss is changed to the same price as open price then we should close on high rsi
		if(currentPositionInfo!=null && currentPositionInfo.getOpenPrice().equals(currentPositionInfo.getStopLoss())){
			return true;
		}
		return false;
	}
}
