package pl.kordek.forex.bot.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    public boolean checkProfitableBacktestData(HashMap<String, BackTestInfo> winRatioStrategyMap, String symbol){
        return winRatioStrategyMap.containsKey(symbol);
    }

    public boolean checkNoOpenCorrelations(String symbol, List<PositionInfo> openedPositions){
        if(symbol.length() != 6){
            return true;
        }
        String firstCurr = symbol.substring(0,2);
        String secCurr = symbol.substring(3,5);
        List<PositionInfo> listOfCorrelatedPositions = openedPositions.stream().filter(e -> e.getSymbol().contains(firstCurr) || e.getSymbol().contains(secCurr)).collect(Collectors.toList());
        if(!listOfCorrelatedPositions.isEmpty()){
            logger.info("Trade Quality Check Failed. Correlated position already opened: {}", listOfCorrelatedPositions.get(0).getSymbol());
        }
        return listOfCorrelatedPositions.isEmpty();
    }

    public boolean checkTradeNotTooRisky(TradeInfo tradeInfo) throws APICommunicationException {
        BigDecimal closePrice = tradeInfo.getClosePrice().setScale(5, RoundingMode.HALF_UP);
        BigDecimal stopLoss = tradeInfo.getStopLoss().setScale(5, RoundingMode.HALF_UP);
        BigDecimal diff = closePrice.subtract(stopLoss).abs();
        String symbol = tradeInfo.getSeries().getName();

        String baseCurrency = symbol.substring(0,3);

        AccountInfo accountInfo = api.getAccountInfo();

        BigDecimal contractCurrency = BigDecimal.ONE;
        if(!accountInfo.getCurrency().equals(baseCurrency)) {
            api.initSymbolOperations(accountInfo.getCurrency() + baseCurrency);
            SymbolResponseInfo symbolResponse = api.getSymbolResponseInfo();
            contractCurrency = new BigDecimal(symbolResponse.getBid()).setScale(5, RoundingMode.HALF_UP);
        }


        BigDecimal contractValue = BigDecimal.ONE.divide(contractCurrency,5, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(tradeInfo.getVolume()))
                .multiply(BigDecimal.valueOf(100000));

        BigDecimal diffValue = contractValue.multiply(diff);

        BigDecimal balance = new BigDecimal(accountInfo.getBalance());


        api.initSymbolOperations(tradeInfo.getSeries().getName());

        int comparison = balance.multiply(new BigDecimal(Configuration.maxRiskPrc)).compareTo(diffValue);

        return comparison > 0;
    }

}
