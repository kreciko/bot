package pl.kordek.forex.bot.constants;

import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.sync.ServerData.ServerEnum;

public interface Configuration {
	public static final ServerEnum server = ServerEnum.DEMO;
	
    public static final String realUsrName = "1426627";
    public static final String realPassword = "youw1nKordian!";
    
    public static final String demoUsrName = "10953995";
    public static final String demoPassword = "youw1nKordian!";
    
	public static final String username = demoUsrName;
	public static final String password = demoPassword;
	
	static final String[] oneFX = {"EURUSD"};
	static final String[] halfMajorFX = {"AUDUSD","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY"} ;
	static final String[] majorFX = {"AUDUSD","EURCHF","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY","GBPUSD","NZDUSD","USDCAD","USDCHF","USDJPY"};
	static final String[] allFX = {"AUDUSD","EURGBP","EURJPY","EURUSD","GBPCHF","GBPJPY","GBPUSD","NZDUSD","USDCAD","USDCHF","USDJPY",
        "USDSEK","NZDCHF","GBPNZD","EURSEK","CHFJPY","NZDJPY","EURNZD","AUDCHF","EURAUD","USDSGD","USDNOK","GBPAUD","CADCHF",
    "EURCAD","AUDNZD","AUDCAD","GBPCAD","EURNOK","USDIDX","CADJPY","AUDJPY","NZDCAD"};
	
	public static final String[] instrumentsFX = oneFX;
    
    public static final PERIOD_CODE candlePeriod = PERIOD_CODE.PERIOD_M1;
    public static final Integer chartPeriodInHours = 24;
    public static final Double volume = 0.1;
}
