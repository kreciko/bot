package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.EMASmartIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ParentIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;

public class IndicatorProvider {
    BarSeries series;

    public IndicatorProvider(BarSeries series) {
        this.series = series;
    }

//    public GeneralIndicators getGeneralIndicators(){
//        Indicator closePrice = new ClosePriceIndicator(series);
//        return new GeneralIndicators(
//                closePrice,
//                new PreviousValueIndicator<>(closePrice),
//                new EMAIndicator(closePrice, 200)),
//                new EMASmartIndicator(closePrice, 50),
//                new EMASmartIndicator(closePrice, 200),
//                new ParentIndicator(new EMAIndicator(parentClosePrice, 50), 4);;
//    }
}
