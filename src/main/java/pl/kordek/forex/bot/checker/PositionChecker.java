package pl.kordek.forex.bot.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.DoubleNum;
import pl.kordek.forex.bot.App;
import pl.kordek.forex.bot.api.BrokerAPI;
import pl.kordek.forex.bot.domain.PositionInfo;
import pl.kordek.forex.bot.domain.TradeInfo;
import pro.xstore.api.message.error.APICommunicationException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PositionChecker {
    private static final Logger logger = LogManager.getLogger(PositionChecker.class);

    private List<PositionInfo> openedPositions = null;

    public PositionChecker(List<PositionInfo> openedPositions) {
        this.openedPositions = openedPositions;
    }
    public PositionChecker() {
        this.openedPositions = new ArrayList<>();
    }

    public boolean isPositionOpenedAndOperationValid(String symbol, TradeType tradeType){
        boolean positionOpened =  openedPositions.stream()
                .anyMatch(e -> e.getSymbol().equals(symbol));
        boolean operationValid = openedPositions.stream()
                .anyMatch(e -> e.getSymbol().equals(symbol) && e.getTradeType().equals(tradeType));
        return positionOpened && operationValid;
    }

    public PositionInfo getOpenedPosition(String symbol){
        return openedPositions.stream()
                .filter(e-> e.getSymbol().equals(symbol))
                .findAny()
                .orElse(null);
    }

    public boolean enterPosition(BrokerAPI api, TradingRecord tradingRecord, TradeInfo tradeInfo) throws APICommunicationException {
        if(tradeInfo == null)
            return false;

        double dummyVolume = 0.05;
        BaseBarSeries series = tradeInfo.getSeries();
        logger.info("{} strategy should ENTER on {}. Bar close price {}. Stop Loss: {} Take Profit: {}",
                tradeInfo.getTradeType(), api.getSymbolResponseInfo().getSymbol(),series.getLastBar().getClosePrice(),
                tradeInfo.getStopLoss().doubleValue(),tradeInfo.getTakeProfit().doubleValue());
        boolean entered = tradingRecord.enter(series.getEndIndex(), series.getLastBar().getClosePrice(),
                DoubleNum.valueOf(dummyVolume), tradeInfo.getStrategyName());
        if (entered) {
            api.enter(tradeInfo.getTradeType(), tradeInfo.getStopLoss().doubleValue(), tradeInfo.getTakeProfit().doubleValue(),
                    tradeInfo.getVolume(), tradeInfo.getStrategyName());
            logger.info("Opened in XTB successfully. Expected stoploss value {}", tradeInfo.getStopLossValue());
            return true;
        } else {
            logger.info("Didn't enter {} position for: {}",tradeInfo.getTradeType(),series.getName());
        }

        return false;
    }

    public boolean exitPosition(BrokerAPI api, BaseBarSeries series, TradingRecord tradingRecord,
                                PositionInfo positionInfo) throws APICommunicationException {
        double dummyVolume = 0.05;
        TradeType tradeType = tradingRecord.getStartingType();

        boolean exited = tradingRecord.exit(series.getEndIndex(), series.getLastBar().getClosePrice(),
                DoubleNum.valueOf(dummyVolume));
        if (exited) {
            api.exit(tradeType, positionInfo);
            logger.info("Closed in XTB successfully");
            return true;
        } else {
            logger.info("Didn't enter {} position for: {}",tradeType, positionInfo.getSymbol());
        }

        return false;
    }
}
