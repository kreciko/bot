package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ChoppyMarketIndicator;
import org.ta4j.core.indicators.candles.*;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.DoubleNum;

public class PriceActionIndicators extends GeneralIndicators{
    private final double pinBarBodyFactor = 0.6;
    private final int scannedBarCount = 14;
    private final int shrinkingCandlesBarCount = 3;

    protected BearishEngulfingIndicator bearishEngulfingInd;
    protected BullishEngulfingIndicator bullishEngulfingInd;

    protected PreviousValueIndicator<Boolean> bearishEngulfingIndPrev;
    protected PreviousValueIndicator<Boolean> bullishEngulfingIndPrev;

    protected ThreeBlackCrowsIndicator threeBlackCrowsInd;
    protected ThreeWhiteSoldiersIndicator threeWhiteSoldiersInd;

    protected BearishHaramiIndicator bearishHaramiIndicator;
    protected BullishHaramiIndicator bullishHaramiIndicator;

    protected BearishPinBarIndicator bearishPinBarIndicator;
    protected BullishPinBarIndicator bullishPinBarIndicator;

    protected PreviousValueIndicator<Boolean> prevBullishPinBarIndicator;

    protected PreviousValueIndicator<Boolean> prevBearishPinBarIndicator;

    protected BullishShrinkingCandlesIndicator bullishShrinkingCandlesIndicator;
    protected BearishShrinkingCandlesIndicator bearishShrinkingCandlesIndicator;

    protected ChoppyMarketIndicator choppyMarketIndicator;

    public PriceActionIndicators(BarSeries series, BarSeries parentSeries) {
        super(series, parentSeries);

        bearishEngulfingInd = new BearishEngulfingIndicator(series);
        bullishEngulfingInd = new BullishEngulfingIndicator(series);
        bearishEngulfingIndPrev = new PreviousValueIndicator<>(bearishEngulfingInd);
        bullishEngulfingIndPrev = new PreviousValueIndicator<>(bullishEngulfingInd);

        threeBlackCrowsInd = new ThreeBlackCrowsIndicator(series, scannedBarCount, 1.0);
        threeWhiteSoldiersInd = new ThreeWhiteSoldiersIndicator(series, scannedBarCount, DoubleNum.valueOf(1));

        bearishHaramiIndicator = new BearishHaramiIndicator(series);
        bullishHaramiIndicator = new BullishHaramiIndicator(series);

        bearishPinBarIndicator = new BearishPinBarIndicator(series, pinBarBodyFactor);
        bullishPinBarIndicator = new BullishPinBarIndicator(series, pinBarBodyFactor);

        prevBullishPinBarIndicator = new PreviousValueIndicator<>(bullishPinBarIndicator);

        prevBearishPinBarIndicator = new PreviousValueIndicator<>(bearishPinBarIndicator);

        bullishShrinkingCandlesIndicator = new BullishShrinkingCandlesIndicator(series, shrinkingCandlesBarCount, true);
        bearishShrinkingCandlesIndicator = new BearishShrinkingCandlesIndicator(series, shrinkingCandlesBarCount, true);

        choppyMarketIndicator = new ChoppyMarketIndicator(series);
    }

    public BearishEngulfingIndicator getBearishEngulfingInd() {
        return bearishEngulfingInd;
    }

    public BullishEngulfingIndicator getBullishEngulfingInd() {
        return bullishEngulfingInd;
    }

    public PreviousValueIndicator<Boolean> getBearishEngulfingIndPrev() {
        return bearishEngulfingIndPrev;
    }

    public PreviousValueIndicator<Boolean> getBullishEngulfingIndPrev() {
        return bullishEngulfingIndPrev;
    }

    public ThreeBlackCrowsIndicator getThreeBlackCrowsInd() {
        return threeBlackCrowsInd;
    }

    public ThreeWhiteSoldiersIndicator getThreeWhiteSoldiersInd() {
        return threeWhiteSoldiersInd;
    }

    public BearishHaramiIndicator getBearishHaramiIndicator() {
        return bearishHaramiIndicator;
    }

    public BullishHaramiIndicator getBullishHaramiIndicator() {
        return bullishHaramiIndicator;
    }

    public BearishPinBarIndicator getBearishPinBarIndicator() {
        return bearishPinBarIndicator;
    }

    public BullishPinBarIndicator getBullishPinBarIndicator() {
        return bullishPinBarIndicator;
    }

    public PreviousValueIndicator<Boolean> getPrevBullishPinBarIndicator() {
        return prevBullishPinBarIndicator;
    }

    public PreviousValueIndicator<Boolean> getPrevBearishPinBarIndicator() {
        return prevBearishPinBarIndicator;
    }

    public BullishShrinkingCandlesIndicator getBullishShrinkingCandlesIndicator() {
        return bullishShrinkingCandlesIndicator;
    }

    public BearishShrinkingCandlesIndicator getBearishShrinkingCandlesIndicator() {
        return bearishShrinkingCandlesIndicator;
    }

    public ChoppyMarketIndicator getChoppyMarketIndicator() {
        return choppyMarketIndicator;
    }
}
