package pl.kordek.forex.bot.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ichimoku.*;

public class IchimokuIndicators extends GeneralIndicators{
    IchimokuTenkanSenIndicator tenkanSen;
    IchimokuKijunSenIndicator kijunSen;
    IchimokuSenkouSpanAIndicator senkouSpanA;
    IchimokuSenkouSpanBIndicator senkouSpanB;
    IchimokuChikouSpanIndicator chikouSpan;


    public IchimokuIndicators(BarSeries series, BarSeries parentSeries) {
        super(series, parentSeries);
        tenkanSen = new IchimokuTenkanSenIndicator(series, 9);
        kijunSen = new IchimokuKijunSenIndicator(series, 26);
        senkouSpanA = new IchimokuSenkouSpanAIndicator(series);
        senkouSpanB = new IchimokuSenkouSpanBIndicator(series);
        chikouSpan = new IchimokuChikouSpanIndicator(series);
    }

    public IchimokuTenkanSenIndicator getTenkanSen() {
        return tenkanSen;
    }

    public IchimokuKijunSenIndicator getKijunSen() {
        return kijunSen;
    }

    public IchimokuSenkouSpanAIndicator getSenkouSpanA() {
        return senkouSpanA;
    }

    public IchimokuSenkouSpanBIndicator getSenkouSpanB() {
        return senkouSpanB;
    }

    public IchimokuChikouSpanIndicator getChikouSpan() {
        return chikouSpan;
    }
}
