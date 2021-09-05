package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.candles.*;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.SatisfiedCountIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;

public class SatisfiedPriceActionIndicators extends PriceActionIndicators{
    private final int scannedBarCount = 7;

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



    public SatisfiedPriceActionIndicators(BarSeries series, BarSeries parentSeries) {
        super(series, parentSeries);


        satisfiedBullishPinbars = new SatisfiedCountIndicator(bullishPinBarIndicator, scannedBarCount);
        satisfiedBearishPinbars = new SatisfiedCountIndicator(bearishPinBarIndicator, scannedBarCount);

        satisfiedBullishEngulfing = new SatisfiedCountIndicator(bullishEngulfingInd, scannedBarCount);
        satisfiedBearishEngulfing = new SatisfiedCountIndicator(bearishEngulfingInd, scannedBarCount);

        satisfiedBullishHarami = new SatisfiedCountIndicator(bullishHaramiIndicator, scannedBarCount);
        satisfiedBearishHarami = new SatisfiedCountIndicator(bearishHaramiIndicator, scannedBarCount);

        satisfiedBullishShrinkingCandles = new SatisfiedCountIndicator(bullishShrinkingCandlesIndicator , scannedBarCount);
        satisfiedBearishShrinkingCandles = new SatisfiedCountIndicator(bearishShrinkingCandlesIndicator, scannedBarCount);

        longEntrySignals = new SumIndicator(satisfiedBullishPinbars, satisfiedBullishEngulfing, satisfiedBullishHarami, satisfiedBearishShrinkingCandles);
        shortEntrySignals = new SumIndicator(satisfiedBearishPinbars, satisfiedBearishEngulfing, satisfiedBearishHarami, satisfiedBullishShrinkingCandles);

        longMinusShortSignals = new DifferenceIndicator(longEntrySignals, shortEntrySignals);

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
}
