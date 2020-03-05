package pl.kordek.forex.bot;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.TrailingStopLossRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import pl.kordek.forex.bot.constants.Configuration;

public class StrategyTester {
	private BaseBarSeries series = null;

	public StrategyTester(BaseBarSeries series) {
		this.series = series;
	}

	void strategyTest(int index, String symbol) {

		Strategy longStrategy = StrategyBuilder.buildLongStrategy(index, series);
		Strategy shortStrategy = StrategyBuilder.buildShortStrategy(index, series);

		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		System.out.println("Should enter for: " + symbol + " index:" + index + " price:" + closePrice.getValue(index)
				+ " = " + longStrategy.shouldEnter(index));

		IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
		IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 26);
		IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
		IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 52);
		IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 26);

		Rule priceUnderCloud = new UnderIndicatorRule(closePrice, senkouSpanA)
				.and(new UnderIndicatorRule(closePrice, senkouSpanB));
		Rule priceCrossesKijunDownRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
		Rule chikouUnderPrice = new UnderIndicatorRule(new ConstantIndicator(series, chikouSpan.getValue(index - 26)),
				closePrice.getValue(index - 26));
		Rule chikouOverPrice = new OverIndicatorRule(new ConstantIndicator(series, chikouSpan.getValue(index - 26)),
				closePrice.getValue(index - 26));
		Rule signalA = priceCrossesKijunDownRule;
		Rule signalB = new CrossedDownIndicatorRule(closePrice, senkouSpanB);

		System.out.println("price under cloud satisfied: " + priceUnderCloud.isSatisfied(index));
		System.out.println("price crosses down kijun: " + priceCrossesKijunDownRule.isSatisfied(index));
		System.out.println("chikou under price: " + chikouUnderPrice.isSatisfied(index));
		System.out.println("chikou over price: " + chikouOverPrice.isSatisfied(index));
		System.out.println("price crosses down span b: "
				+ new CrossedDownIndicatorRule(closePrice, senkouSpanB).isSatisfied(index));
	}

	void strategyTest2( OrderType orderType, int index) {
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
