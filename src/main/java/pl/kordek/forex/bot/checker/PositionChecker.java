package pl.kordek.forex.bot.checker;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Order;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.DoubleNum;
import pl.kordek.forex.bot.api.XTBSymbolOperations;
import pl.kordek.forex.bot.domain.TradeInfo;
import pl.kordek.forex.bot.exceptions.XTBCommunicationException;
import pl.kordek.forex.bot.strategy.StrategyBuilder;
import pro.xstore.api.message.records.TradeRecord;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PositionChecker {
    private List<TradeRecord> openedPositions = null;


    public PositionChecker(List<TradeRecord> openedPositions) {
        this.openedPositions = openedPositions;
    }
    public PositionChecker() {
        this.openedPositions = new ArrayList<>();
    }

    public boolean isPositionOpenedAndOperationValid(String symbol, OrderType orderType){
        int typeOfOperation = orderType == orderType.BUY ? 0 : 1;
        boolean positionOpened =  openedPositions.stream()
                .anyMatch(e -> e.getSymbol().equals(symbol));
        boolean operationValid = openedPositions.stream()
                .anyMatch(e -> e.getSymbol().equals(symbol) && e.getCmd() == typeOfOperation);
        return positionOpened && operationValid;
    }

    public TradeRecord getOpenedPosition(String symbol){
        return openedPositions.stream()
                .filter(e-> e.getSymbol().equals(symbol))
                .findAny()
                .orElse(null);
    }


    public boolean enterPosition(XTBSymbolOperations api, TradingRecord tradingRecord, TradeInfo tradeInfo) throws XTBCommunicationException {
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

    public boolean exitPosition(XTBSymbolOperations api, BaseBarSeries series, TradingRecord tradingRecord,
                                TradeRecord xtbTradeRecord) throws XTBCommunicationException {
        double dummyVolume = 0.05;
        OrderType orderType = tradingRecord.getStartingType();

        boolean exited = tradingRecord.exit(series.getEndIndex(), series.getLastBar().getClosePrice(),
                DoubleNum.valueOf(dummyVolume));
        if (exited) {
            api.exitXTB(xtbTradeRecord, orderType);
            System.out.println(new Date() + ": Closed in XTB successfully");
            return true;
        } else {
            System.out.println(new Date() + ": Didn't exit "+ orderType+" position for: " + xtbTradeRecord.getSymbol());
        }

        return false;
    }
}
