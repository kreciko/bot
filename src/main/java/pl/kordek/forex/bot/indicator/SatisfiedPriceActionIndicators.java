package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.SatisfiedCountIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;

public class SatisfiedPriceActionIndicators extends PriceActionIndicators{
    private SatisfiedCountIndicator satisfiedBullishPinbars;
    private SatisfiedCountIndicator satisfiedBearishPinbars;

    private SatisfiedCountIndicator satisfiedBullishEngulfing;
    private SatisfiedCountIndicator satisfiedBearishEngulfing;

    private SatisfiedCountIndicator satisfiedBullishHarami;
    private SatisfiedCountIndicator satisfiedBearishHarami;

    private SatisfiedCountIndicator satisfiedBullishShrinkingCandles;
    private SatisfiedCountIndicator satisfiedBearishShrinkingCandles;

    private SumIndicator longEntrySignals;
    private SumIndicator shortEntrySignals;
    private DifferenceIndicator longMinusShortSignals;
    private DifferenceIndicator shortMinusLongSignals;




    public SatisfiedPriceActionIndicators(BarSeries series, BarSeries parentSeries, int scannedBarCount) {
        super(series, parentSeries);

        satisfiedBullishPinbars = new SatisfiedCountIndicator(bullishPinBarIndicator, scannedBarCount);
        satisfiedBearishPinbars = new SatisfiedCountIndicator(bearishPinBarIndicator, scannedBarCount);

        satisfiedBullishEngulfing = new SatisfiedCountIndicator(bullishEngulfingInd, scannedBarCount);
        satisfiedBearishEngulfing = new SatisfiedCountIndicator(bearishEngulfingInd, scannedBarCount);

        satisfiedBullishHarami = new SatisfiedCountIndicator(bullishHaramiIndicator, scannedBarCount);
        satisfiedBearishHarami = new SatisfiedCountIndicator(bearishHaramiIndicator, scannedBarCount);

        satisfiedBullishShrinkingCandles = new SatisfiedCountIndicator(bullishShrinkingCandlesIndicator , scannedBarCount);
        satisfiedBearishShrinkingCandles = new SatisfiedCountIndicator(bearishShrinkingCandlesIndicator, scannedBarCount);

        longEntrySignals = new SumIndicator(satisfiedBullishEngulfing, satisfiedBullishHarami, satisfiedBearishShrinkingCandles);
        shortEntrySignals = new SumIndicator(satisfiedBearishEngulfing, satisfiedBearishHarami, satisfiedBullishShrinkingCandles);

        longMinusShortSignals = new DifferenceIndicator(longEntrySignals, shortEntrySignals);
        shortMinusLongSignals = new DifferenceIndicator(shortEntrySignals, longEntrySignals);

    }

    public SatisfiedCountIndicator getSatisfiedBullishPinbars() {
        return satisfiedBullishPinbars;
    }

    public SatisfiedCountIndicator getSatisfiedBearishPinbars() {
        return satisfiedBearishPinbars;
    }

    public SatisfiedCountIndicator getSatisfiedBullishEngulfing() {
        return satisfiedBullishEngulfing;
    }

    public SatisfiedCountIndicator getSatisfiedBearishEngulfing() {
        return satisfiedBearishEngulfing;
    }

    public SatisfiedCountIndicator getSatisfiedBullishHarami() {
        return satisfiedBullishHarami;
    }

    public SatisfiedCountIndicator getSatisfiedBearishHarami() {
        return satisfiedBearishHarami;
    }

    public SatisfiedCountIndicator getSatisfiedBullishShrinkingCandles() {
        return satisfiedBullishShrinkingCandles;
    }

    public SatisfiedCountIndicator getSatisfiedBearishShrinkingCandles() {
        return satisfiedBearishShrinkingCandles;
    }

    public SumIndicator getLongEntrySignals() {
        return longEntrySignals;
    }

    public SumIndicator getShortEntrySignals() {
        return shortEntrySignals;
    }

    public DifferenceIndicator getLongMinusShortSignals() {
        return longMinusShortSignals;
    }
    public DifferenceIndicator getShortMinusLongSignals() {
        return shortMinusLongSignals;
    }

}
