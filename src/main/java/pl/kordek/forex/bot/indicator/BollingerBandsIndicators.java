package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

public class BollingerBandsIndicators extends GeneralIndicators {
    private BollingerBandsMiddleIndicator middleBBand;
    private BollingerBandsLowerIndicator lowBBand;
    private BollingerBandsUpperIndicator upBBand;


    public BollingerBandsIndicators(BarSeries series, BarSeries parentSeries) {
        super(series, parentSeries);
        EMAIndicator avg14 = new EMAIndicator(closePrice, 14);
        StandardDeviationIndicator sd14 = new StandardDeviationIndicator(closePrice, 14);

        this.middleBBand = new BollingerBandsMiddleIndicator(avg14);
        this.lowBBand = new BollingerBandsLowerIndicator(middleBBand, sd14);
        this.upBBand = new BollingerBandsUpperIndicator(middleBBand, sd14);
    }

    public BollingerBandsMiddleIndicator getMiddleBBand() {
        return middleBBand;
    }

    public BollingerBandsLowerIndicator getLowBBand() {
        return lowBBand;
    }

    public BollingerBandsUpperIndicator getUpBBand() {
        return upBBand;
    }
}
