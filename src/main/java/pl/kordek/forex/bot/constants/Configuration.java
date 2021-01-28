package pl.kordek.forex.bot.constants;

import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.sync.ServerData.ServerEnum;

import java.util.Date;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.time.DateUtils;

public interface Configuration {
	ServerEnum server = ServerEnum.DEMO;

    String realUsrName = "1426627";
    String realPassword = "youw1nKordian!";

    String demoUsrName = "10953995";
    String demoPassword = "youw1nKordian!";

	String username = demoUsrName;
	String password = demoPassword;

	String[] oneFX = {"EURUSD"};

	String[] customFX = {"EURUSD", "USDCHF"};
	String[] halfMajorFX = {"AUDUSD","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY"} ;
	String[] majorFX = {"AUDUSD","EURCHF","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY","GBPUSD","NZDUSD","USDCAD","USDCHF","USDJPY"};
	String[] allFX = {"AUDUSD","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY","GBPUSD","NZDUSD","USDCAD","USDCHF","USDJPY",
        "USDSEK","NZDCHF","GBPNZD","EURSEK","CHFJPY","NZDJPY","EURNZD","AUDCHF","EURAUD","USDSGD","USDNOK","GBPAUD","CADCHF",
    "EURCAD","AUDNZD","AUDCAD","GBPCAD","EURNOK","USDIDX","CADJPY","AUDJPY","NZDCAD"};

	String[] customCmd = {"OIL", "GOLD", "SILVER", "PLATINUM"};

	String[] instrumentsFX = majorFX;//ArrayUtils.addAll(majorFX, customCmd);

    PERIOD_CODE candlePeriod = PERIOD_CODE.PERIOD_M15;
    PERIOD_CODE helperCandlePeriod = PERIOD_CODE.PERIOD_M30;
    Integer chartPeriodInHours = 24;
    Double volume = 0.1;

    String sinceDate = "2020/12/28 15:30:00";
    Date sinceDateDt = DateUtils.addDays(new Date(),-7);

    //multiplicand of stoploss to estimate take profit
    Double takeProfitVsStopLossCoeff = 1.5;

    Double stopLossPrc = 0.35;
    Boolean stopLossSetToSupport = true;
    Double takeProfitPrc = stopLossPrc*takeProfitVsStopLossCoeff;

    Boolean updateStopLoss = false;

    int stopLossBarCount = 7;

    Double acceptableSpread = 2.5;

    //test vars
    Boolean runTest = true;
    String runTestFX = "EURUSD";
    int testedIndex = 1;

    Boolean runBot = true;
}
