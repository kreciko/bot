package pl.kordek.forex.bot.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.candles.BearishPinBarIndicator;
import org.ta4j.core.indicators.candles.BullishPinBarIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import pl.kordek.forex.bot.constants.Configuration;

public class StopLossHelper {
	private static BigDecimal stopLossPrc = BigDecimal.valueOf(0.0);
	private static BigDecimal stopLossByMax = BigDecimal.valueOf(0.0);
	private static BigDecimal prevStopLossByMax = BigDecimal.valueOf(0.0);



	public static BigDecimal getNewStopLoss(BaseBarSeries series, OrderType orderType, int barCount) {
		return getNewStopLoss(series.getEndIndex(), series, orderType, barCount);
	}

	public static BigDecimal getNewStopLoss(int endIndex, BaseBarSeries series, OrderType orderType, int barCount) {
		if (series == null) {
			throw new IllegalArgumentException("Series cannot be null");
		}
		if(!Configuration.stopLossSetToSupport) {
			return BigDecimal.ZERO;
		}


		BigDecimal zero = BigDecimal.valueOf(0.0);

		int index = 0;
		stopLossPrc = calculateStopLossPrc(orderType, series.getLastBar().getClosePrice().doubleValue());

		do {
			stopLossByMax = getMaxValue(endIndex, series, orderType, barCount-index);
			index++;
		}
		while((stopLossByMax.equals(zero) || isStopLossExceedingMax(orderType)) && barCount > index);

		return stopLossByMax;
	}

	private static boolean isStopLossExceedingMax(OrderType orderType) {
		if(stopLossByMax.doubleValue() != prevStopLossByMax.doubleValue()) {
			System.out.println(new Date() + ": Calculated sl: "+stopLossByMax+ ". Max sl: "+stopLossPrc);
		}
		prevStopLossByMax = BigDecimal.valueOf(stopLossByMax.doubleValue());
		if((orderType == OrderType.BUY && stopLossPrc.compareTo(stopLossByMax) > 0) ||
				(orderType == OrderType.SELL && stopLossByMax.compareTo(stopLossPrc) > 0)	) {
			stopLossByMax = BigDecimal.valueOf(0.0);
			return true;
		}
		return false;
	}

	public static double getNewTrailingStopLoss(BigDecimal closePrice, BigDecimal trail, BigDecimal originalStopLoss) {
		BigDecimal resultSL = closePrice.add(trail);
		if(resultSL.compareTo(originalStopLoss) > 0) {
			return resultSL.doubleValue();
		}
		return 0.0;
	}

	public static BigDecimal getMaxValue(int endIndex, BaseBarSeries series, OrderType orderType, int barCount) {
		BullishPinBarIndicator bullishPinBar = new BullishPinBarIndicator(series, 0.6);
		BearishPinBarIndicator bearishPinBar = new BearishPinBarIndicator(series, 0.6);

		LowPriceIndicator lowPrice = new LowPriceIndicator(series);
		LowestValueIndicator lowest = new LowestValueIndicator(lowPrice, barCount);

		HighPriceIndicator highPrice = new HighPriceIndicator(series);
		HighestValueIndicator highest = new HighestValueIndicator(highPrice, barCount);

		ATRIndicator atr = new ATRIndicator(series, 14);
		Num atrVal = atr.getValue(endIndex);


		Num result;
		if(orderType == OrderType.BUY)
		{
			result = lowest.getValue(endIndex);
			int lowestIndex = lowest.getLowestIndex(endIndex);

			//dont return stoploss if the lowest value is the newest value.
			//And if the lowest value is one of the rising candles (unless it's a pinbar)
			if( !series.getBar(lowestIndex).isBearish()
							&& !series.getBar(lowestIndex-1).isBearish()
							&& !bullishPinBar.getValue(lowestIndex)) {
				return BigDecimal.valueOf(0.0);
			}

			result = result.minus(atr.getValue(endIndex));
		}
		else {
			result = highest.getValue(endIndex);
			int highestIndex = highest.getHighestIndex(endIndex);

			if(!series.getBar(highestIndex).isBullish()
							&& !series.getBar(highestIndex-1).isBullish()
							&& !bearishPinBar.getValue(highestIndex)) {
				return BigDecimal.valueOf(0.0);
			}

			result = result.plus(atr.getValue(endIndex));
		}

		BigDecimal resultBD = BigDecimal.valueOf(result.doubleValue()).setScale(0, RoundingMode.HALF_UP);

		return resultBD;
	}

	public static BigDecimal calculateStopLossPrc(OrderType orderType, Double closePrice){
        BigDecimal stopLossRatioPrc = orderType == OrderType.BUY
        		? BigDecimal.valueOf(100L).subtract(BigDecimal.valueOf(Configuration.stopLossPrc))
        		: BigDecimal.valueOf(100L).add(BigDecimal.valueOf(Configuration.stopLossPrc));
		BigDecimal stopLossRatio = stopLossRatioPrc.divide(BigDecimal.valueOf(100L));
        BigDecimal stopLossPrice = BigDecimal.valueOf(closePrice).multiply(stopLossRatio);

        return stopLossPrice;
    }
}
