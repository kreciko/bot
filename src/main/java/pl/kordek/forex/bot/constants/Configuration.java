package pl.kordek.forex.bot.constants;

import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.sync.ServerData.ServerEnum;

public interface Configuration {
	ServerEnum server = ServerEnum.DEMO;
	
    String realUsrName = "1426627";
    String realPassword = "youw1nKordian!";
    
    String demoUsrName = "10953995";
    String demoPassword = "youw1nKordian!";
    
	String username = demoUsrName;
	String password = demoPassword;
	
	String[] oneFX = {"EURUSD"};
	String[] halfMajorFX = {"AUDUSD","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY"} ;
	String[] majorFX = {"AUDUSD","EURCHF","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY","GBPUSD","NZDUSD","USDCAD","USDCHF","USDJPY"};
	String[] allFX = {"AUDUSD","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY","GBPUSD","NZDUSD","USDCAD","USDCHF","USDJPY",
        "USDSEK","NZDCHF","GBPNZD","EURSEK","CHFJPY","NZDJPY","EURNZD","AUDCHF","EURAUD","USDSGD","USDNOK","GBPAUD","CADCHF",
    "EURCAD","AUDNZD","AUDCAD","GBPCAD","EURNOK","USDIDX","CADJPY","AUDJPY","NZDCAD"};
	
	String[] instrumentsFX = majorFX;
    
    PERIOD_CODE candlePeriod = PERIOD_CODE.PERIOD_H1;
    Integer chartPeriodInHours = 24;
    Double volume = 0.1;

    String sinceDate = "2019/02/01 15:30:00";
    
    Double stopLossPrc = 0.5;
    Double takeProfitPrc = 0.5;
}
