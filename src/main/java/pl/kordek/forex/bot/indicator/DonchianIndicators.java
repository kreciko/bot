package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.donchian.*;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

public class DonchianIndicators extends  GeneralIndicators{
    private DonchianChannelLowerIndicator donchianLower;
    private DonchianChannelUpperIndicator donchianUpper;
    private Indicator<Boolean> isUpperDRising;
    private Indicator<Boolean> isUpperDFalling;
    private Indicator<Boolean> isLowerDFalling;
    private Indicator<Boolean> isLowerDRising;
    private PreviousValueIndicator<Num> prevUpperD;

    private PreviousValueIndicator<Boolean> wasUpperDFalling;
    private DonchianFallingBarCountIndicator upperDFallingCount;
    private PreviousValueIndicator<Boolean> wasLowerDRising;
    private DonchianRisingBarCountIndicator lowerDRisingCount;


    public DonchianIndicators(BarSeries series, BarSeries parentSeries) {
        super(series, parentSeries);
        this.donchianLower = new DonchianChannelLowerIndicator(series, 20);
        this.donchianUpper = new DonchianChannelUpperIndicator(series, 20);
        this.isUpperDRising = new DonchianIsRisingIndicator(donchianUpper);
        this.isUpperDFalling = new DonchianIsFallingIndicator(donchianUpper);
        this.isLowerDFalling = new DonchianIsFallingIndicator(donchianLower);
        this.isLowerDRising = new DonchianIsRisingIndicator(donchianLower);
        this.prevUpperD =  new PreviousValueIndicator<>(donchianUpper);

        this.wasUpperDFalling = new PreviousValueIndicator<>(isUpperDFalling);
        this.upperDFallingCount = new DonchianFallingBarCountIndicator(prevUpperD);
        this.wasLowerDRising = new PreviousValueIndicator<>(isLowerDRising);
        this.lowerDRisingCount = new DonchianRisingBarCountIndicator(prevUpperD);
    }

    public DonchianChannelLowerIndicator getDonchianLower() {
        return donchianLower;
    }

    public DonchianChannelUpperIndicator getDonchianUpper() {
        return donchianUpper;
    }

    public Indicator<Boolean> getIsUpperDRising() {
        return isUpperDRising;
    }

    public Indicator<Boolean> getIsUpperDFalling() {
        return isUpperDFalling;
    }

    public Indicator<Boolean> getIsLowerDFalling() {
        return isLowerDFalling;
    }

    public Indicator<Boolean> getIsLowerDRising() {
        return isLowerDRising;
    }

    public PreviousValueIndicator<Num> getPrevUpperD() {
        return prevUpperD;
    }

    public PreviousValueIndicator<Boolean> getWasUpperDFalling() {
        return wasUpperDFalling;
    }

    public DonchianFallingBarCountIndicator getUpperDFallingCount() {
        return upperDFallingCount;
    }

    public PreviousValueIndicator<Boolean> getWasLowerDRising() {
        return wasLowerDRising;
    }

    public DonchianRisingBarCountIndicator getLowerDRisingCount() {
        return lowerDRisingCount;
    }
}
