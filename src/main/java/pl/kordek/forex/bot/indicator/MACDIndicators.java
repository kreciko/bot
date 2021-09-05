package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;

public class MACDIndicators extends GeneralIndicators{
    private MACDIndicator macd;
    private EMAIndicator signal;


    public MACDIndicators(BarSeries series, BarSeries parentSeries) {
        super(series, parentSeries);
        macd = new MACDIndicator(closePrice, 12 , 26);
        signal = new EMAIndicator(macd, 9);
    }

    public MACDIndicator getMacd() {
        return macd;
    }

    public EMAIndicator getSignal() {
        return signal;
    }
}
