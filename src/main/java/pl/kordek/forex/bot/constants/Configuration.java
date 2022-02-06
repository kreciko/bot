package pl.kordek.forex.bot.constants;

import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.sync.ServerData.ServerEnum;

import java.util.Date;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.time.DateUtils;

public interface Configuration {
	ServerEnum server = ServerEnum.DEMO;

	int waitingTime = 60000;

    String realUsrName = "1426627";
    String realPassword = "youw1nKordian!";

    String demoUsrName = "10953995";
    String demoPassword = "youw1nKordian!";

	String username = demoUsrName;
	String password = demoPassword;

	String[] oneFX = {"EURUSD"};

	String[] customFX = {"EURUSD", "USDCHF"};
	String[] halfMajorFX = {"AUDUSD","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY"} ;
	String[] majorFX = {"AUDUSD","EURCHF","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY","GBPUSD","NZDUSD", "USDCHF","USDJPY", "USDCAD"};
	String[] allFX = {"AUDUSD","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY","GBPUSD","NZDUSD","USDCAD","USDCHF","USDJPY",
        "USDSEK","NZDCHF","GBPNZD","EURSEK","CHFJPY","NZDJPY","EURNZD","AUDCHF","EURAUD","USDSGD","USDNOK","GBPAUD","CADCHF",
    "EURCAD","AUDNZD","AUDCAD","GBPCAD","EURNOK","USDIDX","CADJPY","AUDJPY","NZDCAD"};

	String[] customCmd = {"OIL", "GOLD"};

	String[] instrumentsFX = majorFX;

    PERIOD_CODE candlePeriod = PERIOD_CODE.PERIOD_M15;
    PERIOD_CODE parentCandlePeriod = PERIOD_CODE.PERIOD_H1;

    String sinceDate = "2020/12/28 15:30:00";
    Date sinceDateDt = DateUtils.addDays(new Date(),-7);

    //multiplicand of stoploss to estimate take profit
    Double takeProfitVsStopLossCoeff = 1.5;

    int stopLossMaxATR = 0;
    int stopLossMinATR = 0;

    Boolean updateStopLoss = true;

    int stopLossBarCount = 14;

    Double acceptableSpreadVsAtr = 0.5;

    //test vars
    Boolean runTest = false;
    String runTestFX = "USDCHF";
    int testedIndex = 9;
    int testWaitingTime = 1000;

    Boolean runBot = true;

    Boolean considerStratetyStrength = false;

    Double minWinningRate = 0.48;
}
