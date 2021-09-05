package pl.kordek.forex.bot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.ta4j.core.*;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.helpers.StopLossATRSmartIndicator;
import org.ta4j.core.indicators.helpers.StopLossIndicator;
import org.ta4j.core.indicators.helpers.StopLossSmartIndicator;
import org.ta4j.core.num.Num;

import pl.kordek.forex.bot.api.XTB;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.MarginLevelResponse;
import pro.xstore.api.message.response.MarginTradeResponse;

public class RobotUtilities {
	private XTB api;
	private SymbolRecord symbolRecord;


	public RobotUtilities(XTB api) {
		this.api = api;
		this.symbolRecord = api.getSr().getSymbol();
	}

	//------------------- SL AND TP CALCULATION ---------------
	public BigDecimal calculateStopLoss(OrderType orderType, BaseBarSeries series, String strategyWithEntrySignal){
		BigDecimal spread = BigDecimal.valueOf(symbolRecord.getSpreadRaw());

		Indicator<Num> donchianInd = orderType == OrderType.BUY ?
				new DonchianChannelLowerIndicator(series, 20) :
				new DonchianChannelUpperIndicator(series, 20);

		// stop loss with donchian channel + atr had better profit results than smart stop loss
		Indicator<Num> stopLossInd =
				 //new StopLossSmartIndicator(series, orderType, Configuration.stopLossBarCount, false)
				 new StopLossIndicator(donchianInd, series, orderType);
				;

        int precisionNumber = symbolRecord.getPrecision();

        BigDecimal stopLoss = BigDecimal.valueOf(stopLossInd.getValue(series.getEndIndex()).doubleValue()).setScale(precisionNumber, RoundingMode.HALF_UP);
        if(stopLoss.doubleValue() != 0 && orderType == OrderType.SELL) {
        	stopLoss = stopLoss.add(spread);
        }

        return stopLoss;
    }


	public BigDecimal calculateTakeProfit(OrderType orderType, BigDecimal stopLoss){
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
		return orderType == OrderType.BUY ? buyTP : sellTP;
    }

	public boolean shouldUpdateStopLoss(BigDecimal stopLossPrice, BigDecimal takeProfitPrice, OrderType orderType) {
		BigDecimal stopLossMinusBid = stopLossPrice.subtract(BigDecimal.valueOf(symbolRecord.getBid())).abs();
		BigDecimal takeProfitMinusBid = takeProfitPrice.subtract(BigDecimal.valueOf(symbolRecord.getBid())).abs();

		BigDecimal stopLossMinusAsk = stopLossPrice.subtract(BigDecimal.valueOf(symbolRecord.getAsk())).abs();
		BigDecimal takeProfitMinusAsk = takeProfitPrice.subtract(BigDecimal.valueOf(symbolRecord.getAsk())).abs();


		if(orderType == OrderType.SELL) {
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


	public String getStrategyWithEntrySignal(int endIndex, TradingRecord tradingRecord, List<Strategy> baseStrategies,
									  HashMap<String, HashMap<String, BigDecimal>> winningRatioMap, String symbol) {
		for(Strategy strategy : baseStrategies) {
			if(strategy.shouldEnter(endIndex, tradingRecord)
					&& winningRatioMap.get(strategy.getName()).get(symbol).compareTo(BigDecimal.valueOf(Configuration.minWinningRate)) > 0)
			{
				System.out.println(new Date() + ": "+strategy.getName()+" strategy signal for a symbol with winning ratio: "+winningRatioMap.get(strategy.getName()).get(symbol) +" - "+symbol);
				return strategy.getName();
			}
		}
		return "";
	}

	public String getStrategyWithExitSignal(int endIndex, TradingRecord tradingRecord, List<Strategy> baseStrategies) {
		for(Strategy strategy : baseStrategies) {
			if(strategy.shouldExit(endIndex, tradingRecord))
			{
				System.out.println(new Date() + ": "+strategy.getName()+" strategy exit signal. Current profit ratio of this strategy:");
				return strategy.getName();
			}
		}
		return "";
	}

	public Double getOptimalVolume(String symbol,Double strategyStrength){
		Double volume;
		try {
			volume = api.getOptimalVolumeXTB(symbol);
		}
		catch(Exception e){
			System.out.println(new Date() +": XTB Exception:" + e.getMessage() + " . Setting the vol to 0.05");
			volume = 0.05;
		}
		multiplyVolByStrategyStrength(volume, strategyStrength);
		return volume
	}

	private void multiplyVolByStrategyStrength(Double volume,Double strategyStrength) {
		volume = BigDecimal.valueOf(volume).multiply(BigDecimal.valueOf(strategyStrength)).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
	}



}
