package pl.kordek.forex.bot.strategy;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import pl.kordek.forex.bot.constants.Configuration;

public class StrategyTester {
	private BaseBarSeries series = null;
	private BaseBarSeries parentSeries = null;

	public StrategyTester(BaseBarSeries series, BaseBarSeries parentSeries) {
		this.series = series;
		this.parentSeries = parentSeries;
	}

	public void strategyTest(int index, String symbol) {

		Strategy longStrategy = StrategyBuilder.buildLongStrategy(index, series, parentSeries);
		Strategy shortStrategy = StrategyBuilder.buildShortStrategy(index, series, parentSeries);

		OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		System.out.println("Should enter for: " + symbol + " index:" + index + " price:" + closePrice.getValue(index)
				+ " = " + shortStrategy.shouldEnter(index));

		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);

		Rule priceUnderCloud = new UnderIndicatorRule(closePrice, senkouSpanA)
				.and(new UnderIndicatorRule(closePrice, senkouSpanB));
		Rule priceOverCloud = new OverIndicatorRule(closePrice, senkouSpanA)
				.and(new OverIndicatorRule(closePrice, senkouSpanB));
		Rule priceCrossesKijunDownRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
		Rule chikouUnderPrice = new UnderIndicatorRule(new ConstantIndicator(series, chikouSpan.getValue(index - 26)),
				closePrice.getValue(index - 26));
		Rule chikouOverPrice = new OverIndicatorRule(new ConstantIndicator(series, chikouSpan.getValue(index - 26)),
				closePrice.getValue(index - 26));
		Rule signalA = priceCrossesKijunDownRule;
		Rule signalB = new CrossedDownIndicatorRule(closePrice, senkouSpanB);

		IchimokuTenkanSenIndicator tenkanSenParent = new IchimokuTenkanSenIndicator(parentSeries, 9);
		IchimokuKijunSenIndicator kijunSenParent = new IchimokuKijunSenIndicator(parentSeries, 26);
		IchimokuSenkouSpanAIndicator senkouSpanAParent = new IchimokuSenkouSpanAIndicator(parentSeries, tenkanSenParent, kijunSenParent);
		IchimokuSenkouSpanBIndicator senkouSpanBParent = new IchimokuSenkouSpanBIndicator(parentSeries, 52);
		Rule parentCloudBearish = new UnderIndicatorRule(senkouSpanAParent, senkouSpanBParent);
		Rule parentCloudBullish = new OverIndicatorRule(senkouSpanAParent, senkouSpanBParent);

		BullishEngulfingIndicator engulfingCandle = new BullishEngulfingIndicator(series);
		Rule engulfingCandleExist = new BooleanIndicatorRule(engulfingCandle);

		IchimokuRules ichimokuRules = new IchimokuRules(index, series, parentSeries);
		Rule tenkanCrossesKijunUp = ichimokuRules.getTenkanCrossesKijunUpRule();

		System.out.println("symbol: " + symbol);
		System.out.println("bullish engulfing: " + engulfingCandleExist.isSatisfied(index));
		System.out.println("price over cloud satisfied: " + priceOverCloud.isSatisfied(index));
		System.out.println("bullish trend confirmed by parent: " + parentCloudBullish.isSatisfied(parentSeries.getEndIndex()));
		System.out.println("Tenkan crosses kijun up: " + tenkanCrossesKijunUp.isSatisfied(index));
//		System.out.println("chikou under price: " + chikouUnderPrice.isSatisfied(index));
		System.out.println("chikou over price: " + chikouOverPrice.isSatisfied(index));
		System.out.println("price crosses down span b: "
				+ new CrossedDownIndicatorRule(closePrice, senkouSpanB).isSatisfied(index));
	}

	public void strategyTest2( OrderType orderType, int index) {
		TradingRecord testTradingRecord = new BaseTradingRecord(orderType);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		Rule signalOut = new TrailingStopLossRule(closePrice, DoubleNum.valueOf(0.5));

		boolean entered = testTradingRecord.enter(index, series.getBar(index).getClosePrice(),
				DoubleNum.valueOf(Configuration.volume));

		System.out.println("stop loss satisfied: " + signalOut.isSatisfied(series.getEndIndex(), testTradingRecord));

	}

	void stopLossCalcTest(int index) {

	}
}
