package pl.kordek.forex.bot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.helpers.StopLossATRSmartIndicator;
import org.ta4j.core.num.Num;

import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.MarginLevelResponse;
import pro.xstore.api.message.response.MarginTradeResponse;
import pro.xstore.api.message.response.SymbolResponse;
import pro.xstore.api.sync.SyncAPIConnector;

public class RobotUtilities {

	private SymbolResponse symbolResponse;
	private SyncAPIConnector connector;


	public RobotUtilities(SymbolResponse symbolResponse, SyncAPIConnector connector) {
		this.symbolResponse = symbolResponse;
		this.connector = connector;
	}

	//------------------- SL AND TP CALCULATION ---------------
	BigDecimal calculateStopLoss(TRADE_OPERATION_CODE operationCode, BaseBarSeries series){
		OrderType orderType = operationCode == TRADE_OPERATION_CODE.BUY ? OrderType.BUY : OrderType.SELL;

		BigDecimal spread = BigDecimal.valueOf(symbolResponse.getSymbol().getSpreadRaw());

		Indicator<Num> stopLossInd = new StopLossATRSmartIndicator(series, orderType, Configuration.stopLossBarCount, false);

        int precisionNumber = symbolResponse.getSymbol().getPrecision();

        BigDecimal stopLoss = BigDecimal.valueOf(stopLossInd.getValue(series.getEndIndex()).doubleValue()).setScale(precisionNumber, RoundingMode.HALF_UP);
        if(stopLoss.doubleValue() != 0 && operationCode == TRADE_OPERATION_CODE.SELL) {
        	stopLoss = stopLoss.add(spread);
        }

        return stopLoss;
    }

	BigDecimal calculateTakeProfit(TRADE_OPERATION_CODE operationCode, BigDecimal stopLoss){
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

	boolean shouldUpdateStopLoss(BigDecimal stopLossPrice, BigDecimal takeProfitPrice, TRADE_OPERATION_CODE operationCode) {
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
	private boolean enoughProfitForSLChange(BigDecimal stopLossMinusPrice, BigDecimal takeProfitMinusPrice) {
		BigDecimal ratio = BigDecimal.valueOf(2.0).
				divide(BigDecimal.valueOf(Configuration.takeProfitVsStopLossCoeff).subtract(BigDecimal.ONE));
		return stopLossMinusPrice.divide(takeProfitMinusPrice, 2, RoundingMode.HALF_UP)
				.compareTo(ratio) >= 0;
	}


	//------------------- MARGIN, VOLUME AND SL CHECKS ---------------
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


	//------------------- OPTIMAL VOLUME CALCULATION ---------------
	double getOptimalVolume(String symbol) throws XTBCommunicationException {
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



	Boolean checkShouldEnter(int endIndex, TradingRecord tradingRecord, List<Strategy> baseStrategies,
			HashMap<String, HashMap<String, BigDecimal>> winningRatioMap, String symbol) {
		for(Strategy strategy : baseStrategies) {
			if(strategy.shouldEnter(endIndex, tradingRecord)
					&& winningRatioMap.get(strategy.getName()).get(symbol).compareTo(BigDecimal.valueOf(Configuration.minWinningRate)) > 0)
			{
				System.out.println(new Date() + ": "+strategy.getName()+" strategy signal for a symbol with winning ratio: "+winningRatioMap.get(strategy.getName()).get(symbol));
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


}
