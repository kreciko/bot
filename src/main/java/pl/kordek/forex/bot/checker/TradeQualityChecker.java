package pl.kordek.forex.bot.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.UnderIndicatorRule;
import pl.kordek.forex.bot.Robot;
import pl.kordek.forex.bot.api.BrokerAPI;
import pl.kordek.forex.bot.constants.Configuration;
import pl.kordek.forex.bot.domain.*;
import pro.xstore.api.message.error.APICommunicationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class TradeQualityChecker {
    private static final Logger logger = LogManager.getLogger(TradeQualityChecker.class);
    private BrokerAPI api;

    public TradeQualityChecker(BrokerAPI api) {
        this.api = api;
    }


    public boolean notBlackListed(BlackListOperation blackListInfo, Trade.TradeType tradeType){
            if(blackListInfo!= null && blackListInfo.getTypeOfOperation() == tradeType){
                logger.info("Symbol {} is currently blacklisted for tradeType: {}", blackListInfo.getInstrument(), tradeType);
                return false;
            }
            return true;
    }

    public boolean rsiNotTooStrong(BaseBarSeries series, Trade.TradeType tradeType){
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), 14);
        Num rsiVal = rsi.getValue(series.getEndIndex());

        if(tradeType == Trade.TradeType.BUY && rsiVal.isGreaterThan(DoubleNum.valueOf(65))) {
            logger.info("RSI too strong to enter: {} ", rsiVal.doubleValue());
            return false;
        }
        if(tradeType == Trade.TradeType.SELL && rsiVal.isLessThan(DoubleNum.valueOf(35))) {
            logger.info("RSI too strong to enter: {} ", rsiVal.doubleValue());
            return false;
        }

        return true;
    }

    public boolean checkEntryBarNotTooBig(BaseBarSeries series) {
        ATRIndicator atr = new ATRIndicator(series,14);
        PreviousValueIndicator<Num> prevAtr = new PreviousValueIndicator(atr);
        TRIndicator tr = new TRIndicator(series);
        Rule entryBarNotTooBigRule = new UnderIndicatorRule(tr, TransformIndicator.multiply(prevAtr, 5.0));
        int endIndex = series.getEndIndex();

        Double trVal = tr.getValue(endIndex).doubleValue();
        Double prevATRVal = prevAtr.getValue(endIndex).doubleValue();

        BigDecimal div = BigDecimal.valueOf(trVal).
                divide(BigDecimal.valueOf(prevATRVal),2, RoundingMode.HALF_UP);

        boolean entryBarNotTooBig = entryBarNotTooBigRule.isSatisfied(endIndex);
        if(!entryBarNotTooBig){
            logger.info("Entry bar too big. Skipping the trade. Candle size is {} times bigger than average", div);
        }
        return entryBarNotTooBig;
    }

    public boolean checkProfitableBacktestData(HashMap<String, BackTestInfo> winRatioStrategyMap, String symbol){
        boolean winRatioContainsSymbol = winRatioStrategyMap.containsKey(symbol);
        if(!winRatioContainsSymbol)
            logger.debug("Backtest data doesn't contain value for symbol: {}. Skipping the trade", symbol);
        return winRatioContainsSymbol;
    }

    public boolean checkNoOpenCorrelations(String symbol, List<PositionInfo> openedPositions){
        if(symbol.length() != 6){
            return true;
        }
        List correlationExceptionList = List.of(Configuration.correlationException);
        String firstCurr = symbol.substring(0,3);
        String secCurr = symbol.substring(3,6);
        List<PositionInfo> listOfCorrelatedPositions = openedPositions.stream().filter(e ->
                (e.getSymbol().contains(firstCurr) ||
                        e.getSymbol().contains(secCurr)) && !correlationExceptionList.contains(e.getSymbol())).collect(Collectors.toList());
        if(!listOfCorrelatedPositions.isEmpty()){
            logger.info("Correlated position already opened: {}", listOfCorrelatedPositions.get(0).getSymbol());
        }
        return listOfCorrelatedPositions.isEmpty();
    }

    public boolean checkTradeNotTooRisky(TradeInfo tradeInfo) throws APICommunicationException {
        BigDecimal closePrice = tradeInfo.getClosePrice().setScale(5, RoundingMode.HALF_UP);
        BigDecimal stopLoss = tradeInfo.getStopLoss().setScale(5, RoundingMode.HALF_UP);
        BigDecimal diff = closePrice.subtract(stopLoss).abs();
        String symbol = tradeInfo.getSeries().getName();

        String baseCurrency = symbol.substring(3, 6);

        AccountInfo accountInfo = api.getAccountInfo();

        BigDecimal contractCurrency = BigDecimal.ONE;
        SymbolResponseInfo symbolResponse = null;
        if (!accountInfo.getCurrency().equals(baseCurrency)) {
            api.initSymbolOperations(accountInfo.getCurrency() + baseCurrency);
            symbolResponse = api.getSymbolResponseInfo();
            contractCurrency = new BigDecimal(symbolResponse.getBid()).setScale(5, RoundingMode.HALF_UP);
        }

        api.initSymbolOperations(tradeInfo.getSeries().getName());
        SymbolResponseInfo ourTradeSymbolResponse = api.getSymbolResponseInfo();

        BigDecimal contractValue = BigDecimal.ONE.divide(contractCurrency, 5, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(tradeInfo.getVolume()))
                .multiply(BigDecimal.valueOf(ourTradeSymbolResponse.getContractSizeForOneLot()));

        BigDecimal diffValue = contractValue.multiply(diff);

        tradeInfo.setStopLossValue(diffValue);

        BigDecimal balance = new BigDecimal(accountInfo.getBalance());

        int comparisonMaxRisk = balance.multiply(new BigDecimal(Configuration.maxRiskPrc)).setScale(5, RoundingMode.HALF_UP).compareTo(diffValue);
        int comparisonMinRisk = balance.multiply(new BigDecimal(Configuration.minRiskPrc)).setScale(5, RoundingMode.HALF_UP).compareTo(diffValue);

        boolean tooBig = comparisonMaxRisk < 0;
        boolean tooSmall = comparisonMinRisk > 0;

        if(tooBig){
            logger.info("Operation too risky. The stop loss would cost: {} {}", diffValue, accountInfo.getCurrency());
        }
        if(tooSmall){
            logger.info("Operation too small. The stop loss would cost: {} {}", diffValue, accountInfo.getCurrency());
        }

        return !tooBig && !tooSmall;
    }

}
