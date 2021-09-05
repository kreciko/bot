package pl.kordek.forex.bot.checker;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.DoubleNum;
import pl.kordek.forex.bot.api.XTB;
import pl.kordek.forex.bot.domain.TradeInfo;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.strategy.StrategyBuilder;
import pro.xstore.api.message.records.TradeRecord;

import java.util.Date;
import java.util.List;

public class PositionChecker {
    private StrategyBuilder strategyBuilder;
    private List<TradeRecord> openedPositions = null;


    public PositionChecker(StrategyBuilder strategyBuilder,
                           List<TradeRecord> openedPositions) {
        this.strategyBuilder = strategyBuilder;
        this.openedPositions = openedPositions;
    }

    public double getStrategyStrength(){
        return strategyBuilder.assessStrategyStrength();
    }

    public boolean isPositionOpenedAndOperationValid(String symbol){
        boolean positionOpened =  openedPositions.stream()
                .anyMatch(e -> e.getSymbol().equals(symbol));
        boolean operationValid = openedPositions.stream()
                .anyMatch(e -> e.getSymbol().equals(symbol) && e.getCmd() == strategyBuilder.typeOfOperation);
        return positionOpened && operationValid;
    }

    public TradeRecord getSymbolTradeRecord(String symbol){
        return openedPositions.stream()
                .filter(e-> e.getSymbol().equals(symbol))
                .findAny()
                .orElse(null);
    }

    public boolean enterPosition(XTB api, TradingRecord tradingRecord, TradeInfo tradeInfo) throws XTBCommunicationException {
        if(tradeInfo == null)
            return false;

        double dummyVolume = 0.05;
        BaseBarSeries series = tradeInfo.getSeries();
        boolean entered = tradingRecord.enter(series.getEndIndex(), series.getLastBar().getClosePrice(),
                DoubleNum.valueOf(dummyVolume), tradeInfo.getStrategyName());
        if (entered) {
            api.enterXTB(tradeInfo.getOrderType(), tradeInfo.getStopLoss(), tradeInfo.getTakeProfit(),
                    tradeInfo.getVolume(), tradeInfo.getStrategyName());
            System.out.println(new Date() + ": Opened in XTB successfully");
            return true;
        } else {
            System.out.println(new Date() + ": Didn't enter "+tradeInfo.getOrderType()+" position for: " + series.getName());
        }

        return false;
    }

    public boolean exitPosition(XTB api, TradingRecord tradingRecord, TradeRecord xtbTradeRecord) throws XTBCommunicationException {
        double dummyVolume = 0.05;
        BaseBarSeries series = strategyBuilder.getSeries();

        boolean exited = tradingRecord.exit(series.getEndIndex(), series.getLastBar().getClosePrice(),
                DoubleNum.valueOf(dummyVolume));
        if (exited) {
            api.exitXTB(xtbTradeRecord, strategyBuilder.orderType);
            System.out.println(new Date() + ": Closed in XTB successfully");
            return true;
        } else {
            System.out.println(new Date() + ": Didn't exit "+ strategyBuilder.orderType+" position for: " + series.getName());
        }

        return false;
    }
}
