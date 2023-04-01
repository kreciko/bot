package pl.kordek.forex.bot.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.StopLossIndicator;
import org.ta4j.core.num.Num;
import pl.kordek.forex.bot.api.BrokerAPI;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.PositionInfo;
import pl.kordek.forex.bot.domain.SymbolResponseInfo;
import pro.xstore.api.message.error.APICommunicationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

public class VolumeAndSLOperations {
	private static final Logger logger = LogManager.getLogger(VolumeAndSLOperations.class);

	private final int TYPE_OF_OPERATION_BUY = 0;
	private final int TYPE_OF_OPERATION_SELL = 1;

	private BrokerAPI api;
	private SymbolResponseInfo symbolInfo;


	public VolumeAndSLOperations(BrokerAPI api) throws APICommunicationException {
		this.api = api;
		this.symbolInfo = api.getSymbolResponseInfo();
	}

	//------------------- SL AND TP CALCULATION ---------------
	public BigDecimal calculateStopLoss(TradeType tradeType, Indicator stopLossStrategy, BaseBarSeries series){
		BigDecimal spread = BigDecimal.valueOf(symbolInfo.getSpreadRaw());
		Indicator<Num> stopLossInd =
				 new StopLossIndicator(stopLossStrategy, series, tradeType);

        int precisionNumber = symbolInfo.getPrecision();

        BigDecimal stopLoss = BigDecimal.valueOf(stopLossInd.getValue(series.getEndIndex()).doubleValue()).setScale(precisionNumber, RoundingMode.HALF_UP);
        if(stopLoss.doubleValue() != 0 && tradeType == TradeType.SELL) {
        	stopLoss = stopLoss.add(spread);
        }

        return stopLoss;
    }


	public BigDecimal calculateTakeProfit(TradeType tradeType, BigDecimal stopLoss){
		BigDecimal takeProfitVsStopLossCoeffBD = BigDecimal.valueOf(Configuration.takeProfitVsStopLossCoeff);

		Integer precisionNumber = symbolInfo.getPrecision();
		BigDecimal spread = BigDecimal.valueOf(symbolInfo.getSpreadRaw());

		//we add the spread for SELL because the imported prices are bid
		BigDecimal sellTP = BigDecimal.valueOf(symbolInfo.getBid())
				.subtract(stopLoss.subtract(BigDecimal.valueOf(symbolInfo.getBid())).multiply(takeProfitVsStopLossCoeffBD))
				.setScale(precisionNumber, RoundingMode.HALF_UP).add(spread);
        BigDecimal buyTP = BigDecimal.valueOf(symbolInfo.getAsk())
        		.add(BigDecimal.valueOf(symbolInfo.getAsk()).subtract(stopLoss).multiply(takeProfitVsStopLossCoeffBD))
        		.setScale(precisionNumber, RoundingMode.HALF_UP);
		return tradeType == TradeType.BUY ? buyTP : sellTP;
    }



	public boolean shouldUpdateStopLoss(PositionInfo positionInfo, TradeType tradeType){
		if(!Configuration.updateStopLoss) {
			return false;
		}
		if (positionInfo == null || !tradeType.equals(positionInfo.getTradeType()))
			return false;

		if(!shouldUpdateStopLoss(
				BigDecimal.valueOf(positionInfo.getStopLoss()),
				BigDecimal.valueOf(positionInfo.getTakeProfit()),
				tradeType)){
			return false;
		}

		return true;
	}

	private boolean shouldUpdateStopLoss(BigDecimal stopLossPrice, BigDecimal takeProfitPrice, TradeType tradeType) {
		BigDecimal stopLossMinusBid = stopLossPrice.subtract(BigDecimal.valueOf(symbolInfo.getBid())).abs();
		BigDecimal takeProfitMinusBid = takeProfitPrice.subtract(BigDecimal.valueOf(symbolInfo.getBid())).abs();

		BigDecimal stopLossMinusAsk = stopLossPrice.subtract(BigDecimal.valueOf(symbolInfo.getAsk())).abs();
		BigDecimal takeProfitMinusAsk = takeProfitPrice.subtract(BigDecimal.valueOf(symbolInfo.getAsk())).abs();


		if(tradeType == TradeType.SELL) {
			return enoughProfitForSLChange(stopLossMinusAsk, takeProfitMinusAsk);
		}
		else {
			return enoughProfitForSLChange(stopLossMinusBid, takeProfitMinusBid);
		}
	}

	private boolean enoughProfitForSLChange(BigDecimal stopLossMinusPrice, BigDecimal takeProfitMinusPrice) {
		BigDecimal ratio = BigDecimal.valueOf(2.0).
				divide(BigDecimal.valueOf(Configuration.takeProfitVsStopLossCoeff).subtract(BigDecimal.ONE));
		return stopLossMinusPrice.divide(takeProfitMinusPrice, 2, RoundingMode.HALF_UP)
				.compareTo(ratio) >= 0;
	}


	//------------------- MARGIN, VOLUME AND SL CHECKS ---------------
	public boolean volumeAndSlChecks(double volume, double stopLoss) throws APICommunicationException {
		if(stopLoss == 0.0) {
			logger.warn("Couldn't calculate the stoploss that would be below the max allowed percentage. Skipping trade");
			return false;
		}

		if(!api.isEnoughMargin(volume)) {
			logger.warn("Not enough margin");
			return false;
		}

		if(volume == 0.0) {
			logger.warn("Calculated volume is below 0.01. Skipping the trade");
			return false;
		}

		return true;
	}

	public Double getOptimalVolume(String symbol,Double strategyStrength, int openedPositionsCount){
		Double volume;
		try {
			volume = api.getOptimalVolume();

			while(!api.isEnoughMargin(volume) && Configuration.maxNumberOfPositionsOpen > openedPositionsCount) {
				volume = BigDecimal.valueOf(Configuration.volumeResizeFactor*volume).setScale(2, RoundingMode.HALF_UP).doubleValue();
				System.out.println(new Date() + ": Not enough margin, but less than max:"+Configuration.maxNumberOfPositionsOpen+" positions open. Resizing the volume to "+volume);
			}
		}
		catch(Exception e){
			logger.error("Unexpected exception when calculating optimal volume. Setting the vol to 0.05", e);
			volume = 0.05;
		}
		multiplyVolByStrategyStrength(volume, strategyStrength);
		return volume;
	}

	private void multiplyVolByStrategyStrength(Double volume,Double strategyStrength) {
		volume = BigDecimal.valueOf(volume).multiply(BigDecimal.valueOf(strategyStrength)).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
	}



}
