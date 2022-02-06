package pl.kordek.forex.bot.utils;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.helpers.StopLossIndicator;
import org.ta4j.core.num.Num;
import pl.kordek.forex.bot.api.XTBSymbolOperations;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.records.TradeRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

public class VolumeAndSLOperations {
	private final int TYPE_OF_OPERATION_BUY = 0;
	private final int TYPE_OF_OPERATION_SELL = 1;

	private XTBSymbolOperations api;
	private SymbolRecord symbolRecord;


	public VolumeAndSLOperations(XTBSymbolOperations api) {
		this.api = api;
		this.symbolRecord = api.getSr().getSymbol();
	}

	//------------------- SL AND TP CALCULATION ---------------
	public BigDecimal calculateStopLoss(TradeType tradeType, Indicator stopLossStrategy, BaseBarSeries series){
		BigDecimal spread = BigDecimal.valueOf(symbolRecord.getSpreadRaw());
		Indicator<Num> stopLossInd =
				 new StopLossIndicator(stopLossStrategy, series, tradeType);

        int precisionNumber = symbolRecord.getPrecision();

        BigDecimal stopLoss = BigDecimal.valueOf(stopLossInd.getValue(series.getEndIndex()).doubleValue()).setScale(precisionNumber, RoundingMode.HALF_UP);
        if(stopLoss.doubleValue() != 0 && tradeType == TradeType.SELL) {
        	stopLoss = stopLoss.add(spread);
        }

        return stopLoss;
    }


	public BigDecimal calculateTakeProfit(TradeType tradeType, BigDecimal stopLoss){
		BigDecimal takeProfitVsStopLossCoeffBD = BigDecimal.valueOf(Configuration.takeProfitVsStopLossCoeff);

		Integer precisionNumber = symbolRecord.getPrecision();
		BigDecimal spread = BigDecimal.valueOf(symbolRecord.getSpreadRaw());

		//we add the spread for SELL because the imported prices are bid
		BigDecimal sellTP = BigDecimal.valueOf(symbolRecord.getBid())
				.subtract(stopLoss.subtract(BigDecimal.valueOf(symbolRecord.getBid())).multiply(takeProfitVsStopLossCoeffBD))
				.setScale(precisionNumber, RoundingMode.HALF_UP).add(spread);
        BigDecimal buyTP = BigDecimal.valueOf(symbolRecord.getAsk())
        		.add(BigDecimal.valueOf(symbolRecord.getAsk()).subtract(stopLoss).multiply(takeProfitVsStopLossCoeffBD))
        		.setScale(precisionNumber, RoundingMode.HALF_UP);
		return tradeType == TradeType.BUY ? buyTP : sellTP;
    }



	public boolean shouldUpdateStopLoss(TradeRecord xtbTR, TradeType tradeType){
		if(!Configuration.updateStopLoss) {
			return false;
		}
		if (xtbTR == null || isTrOperationInvalid(tradeType, xtbTR.getCmd()))
			return false;

		if(!shouldUpdateStopLoss(
				BigDecimal.valueOf(xtbTR.getSl()),
				BigDecimal.valueOf(xtbTR.getTp()),
				tradeType)){
			return false;
		}

		return true;
	}

	private boolean isTrOperationInvalid(TradeType tradeType, int existingOperationCode){
		return (existingOperationCode == TYPE_OF_OPERATION_SELL && tradeType  == TradeType.BUY)
				|| (existingOperationCode == TYPE_OF_OPERATION_BUY && tradeType == TradeType.SELL);
	}

	private boolean shouldUpdateStopLoss(BigDecimal stopLossPrice, BigDecimal takeProfitPrice, TradeType tradeType) {
		BigDecimal stopLossMinusBid = stopLossPrice.subtract(BigDecimal.valueOf(symbolRecord.getBid())).abs();
		BigDecimal takeProfitMinusBid = takeProfitPrice.subtract(BigDecimal.valueOf(symbolRecord.getBid())).abs();

		BigDecimal stopLossMinusAsk = stopLossPrice.subtract(BigDecimal.valueOf(symbolRecord.getAsk())).abs();
		BigDecimal takeProfitMinusAsk = takeProfitPrice.subtract(BigDecimal.valueOf(symbolRecord.getAsk())).abs();


		if(tradeType == TradeType.SELL) {
			return enoughProfitForSLChange(stopLossMinusAsk, takeProfitMinusAsk);
		}
		else {
			return enoughProfitForSLChange(stopLossMinusBid, takeProfitMinusBid);
		}
	}

	//calculates if we should update the stoploss to the opening price
		//if we have a ratio 1:1 between profit and stoploss distance then we update the stoploss
	private boolean enoughProfitForSLChange(BigDecimal stopLossMinusPrice, BigDecimal takeProfitMinusPrice) {
		BigDecimal ratio = BigDecimal.valueOf(2.0).
				divide(BigDecimal.valueOf(Configuration.takeProfitVsStopLossCoeff).subtract(BigDecimal.ONE));
		return stopLossMinusPrice.divide(takeProfitMinusPrice, 2, RoundingMode.HALF_UP)
				.compareTo(ratio) >= 0;
	}


	//------------------- MARGIN, VOLUME AND SL CHECKS ---------------
	public boolean volumeAndSlChecks(double volume, double stopLoss) throws XTBCommunicationException {
		if(stopLoss == 0.0) {
			System.out.println(new Date() + ": Couldnt calculate the stoploss that would be below the max allowed percentage. Skipping trade");
			return false;
		}

		if(!api.isEnoughMargin(volume)) {
			System.out.println(new Date() + ": Not enough margin");
			return false;
		}

		if(volume == 0.0) {
			System.out.println(new Date() + ": Calculated volume is below 0.01. Skipping the trade");
			return false;
		}

		return true;
	}

	public Double getOptimalVolume(String symbol,Double strategyStrength){
		Double volume;
		try {
			volume = api.getOptimalVolumeXTB();
		}
		catch(Exception e){
			System.out.println(new Date() +": XTB Exception:" + e.getMessage() + " . Setting the vol to 0.05");
			volume = 0.05;
		}
		multiplyVolByStrategyStrength(volume, strategyStrength);
		return volume;
	}

	private void multiplyVolByStrategyStrength(Double volume,Double strategyStrength) {
		volume = BigDecimal.valueOf(volume).multiply(BigDecimal.valueOf(strategyStrength)).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
	}



}
