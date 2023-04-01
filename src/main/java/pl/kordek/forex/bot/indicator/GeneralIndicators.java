package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.EMASmartIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ParentIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.StopLossIndicator;
import org.ta4j.core.num.Num;
import pl.kordek.forex.bot.constants.Configuration;

public class GeneralIndicators {
    protected BarSeries series;
    protected ClosePriceIndicator closePrice;
    protected ParentIndicator closePriceParentInd;
    protected PreviousValueIndicator<Num> prevClosePrice;
    protected EMAIndicator trendLine200;
    protected EMASmartIndicator smartTrendLine200;
    protected ParentIndicator parentTrendLine200;
    protected ParentIndicator smartParentTrendLine200;
    protected ParentIndicator smartParentTrendLine50;
    protected RSIIndicator rsi;
    //private StopLossIndicator slIndicator;

    public GeneralIndicators(BarSeries series, BarSeries parentSeries) {
        Long parentScaleL = (Configuration.parentCandlePeriod.getCode()/Configuration.candlePeriod.getCode());

        this.closePrice = new ClosePriceIndicator(series);
        this.prevClosePrice = new PreviousValueIndicator<>(closePrice);
        this.trendLine200 = new EMAIndicator(closePrice, 200);
        this.smartTrendLine200 = new EMASmartIndicator(closePrice, 200);

        ClosePriceIndicator parentClosePrice = new ClosePriceIndicator(parentSeries);
        this.closePriceParentInd = new ParentIndicator(parentClosePrice,parentScaleL.intValue());

        this.parentTrendLine200 = new ParentIndicator(new EMAIndicator(parentClosePrice, 200), parentScaleL.intValue());
        this.smartParentTrendLine200 = new ParentIndicator(new EMASmartIndicator(parentClosePrice, 200), parentScaleL.intValue());
        this.smartParentTrendLine50 = new ParentIndicator(new EMASmartIndicator(parentClosePrice, 50), parentScaleL.intValue());
        this.rsi = new RSIIndicator(closePrice, 14);
    }

    public ClosePriceIndicator getClosePrice() {
        return closePrice;
    }



    public PreviousValueIndicator<Num> getPrevClosePrice() {
        return prevClosePrice;
    }

    public EMAIndicator getTrendLine200() {
        return trendLine200;
    }


    public EMASmartIndicator getSmartTrendLine200() {
        return smartTrendLine200;
    }

    public ParentIndicator getSmartParentTrendLine200() {
        return smartParentTrendLine200;
    }

    public ParentIndicator getSmartParentTrendLine50() {
        return smartParentTrendLine50;
    }

    public ParentIndicator getParentTrendLine200() {
        return parentTrendLine200;
    }

    public ParentIndicator getClosePriceParentInd() {
        return closePriceParentInd;
    }

    public void setClosePriceParentInd(ParentIndicator closePriceParentInd) {
        this.closePriceParentInd = closePriceParentInd;
    }

    public RSIIndicator getRsi() { return rsi; }
}
