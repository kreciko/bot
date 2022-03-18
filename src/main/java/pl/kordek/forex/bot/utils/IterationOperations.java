package pl.kordek.forex.bot.utils;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import pl.kordek.forex.bot.api.XTBSymbolOperations;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.records.RateInfoRecord;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class IterationOperations {

    public BaseBarSeries populateBaseBarSeries(XTBSymbolOperations api, Duration duration, PERIOD_CODE candlePeriod, Date sinceDate)
            throws XTBCommunicationException {

        List<RateInfoRecord> rateInfos = api.getCharts(sinceDate, candlePeriod);

        int precisionNumber = api.getSr().getSymbol().getPrecision();
        String symbol = api.getSr().getSymbol().getSymbol();

        BaseBarSeries series = convertRateInfoToBarSeries(rateInfos.subList(0, rateInfos.size() - 1), symbol, precisionNumber, duration);

        return series;
    }

    private BaseBarSeries convertRateInfoToBarSeries(List<RateInfoRecord> rateInfoRecords, String symbol, int precisionNumber, Duration duration) {
        List<Bar> barList = new ArrayList<>();
        for (RateInfoRecord rateInfoRecord : rateInfoRecords) {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(rateInfoRecord.getCtm()),
                    ZoneId.systemDefault());
            BigDecimal open = BigDecimal.valueOf(rateInfoRecord.getOpen()).scaleByPowerOfTen(-precisionNumber);
            BigDecimal high = BigDecimal.valueOf(rateInfoRecord.getOpen())
                    .add(BigDecimal.valueOf(rateInfoRecord.getHigh())).scaleByPowerOfTen(-precisionNumber);
            BigDecimal low = BigDecimal.valueOf(rateInfoRecord.getOpen())
                    .add(BigDecimal.valueOf(rateInfoRecord.getLow())).scaleByPowerOfTen(-precisionNumber);
            BigDecimal close = BigDecimal.valueOf(rateInfoRecord.getOpen())
                    .add(BigDecimal.valueOf(rateInfoRecord.getClose())).scaleByPowerOfTen(-precisionNumber);

            Double openD = open.doubleValue();
            Double highD = high.doubleValue();
            Double lowD = low.doubleValue();
            Double closeD = close.doubleValue();

            BaseBar Bar = new BaseBar(duration, endTime, openD, highD, lowD, closeD,
                    rateInfoRecord.getVol());
            if(!(open.equals(close) && low.equals(high)))
                barList.add(Bar);
        }
        return new BaseBarSeries(symbol, barList);
    }
}
