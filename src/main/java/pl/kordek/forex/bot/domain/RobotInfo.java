package pl.kordek.forex.bot.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.ta4j.core.TradingRecord;

public class RobotInfo implements Serializable{
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -4557097264380136922L;

	
	private List<HashMap<String, TradingRecord>> tradingRecordsMaps;
	private HashMap<String,BlackListOperation> blackList;
	private int robotIteration;
	
	
	


	public RobotInfo(List<HashMap<String, TradingRecord>> tradingRecordsMaps,
			HashMap<String, BlackListOperation> blackList, int robotIteration) {
		super();
		this.tradingRecordsMaps = tradingRecordsMaps;
		this.blackList = blackList;
		this.robotIteration = robotIteration;
	}


	public List<HashMap<String, TradingRecord>> getTradingRecordsMaps() {
		return tradingRecordsMaps;
	}


	public void setTradingRecordsMaps(List<HashMap<String, TradingRecord>> tradingRecordsMaps) {
		this.tradingRecordsMaps = tradingRecordsMaps;
	}


	public int getRobotIteration() {
		return robotIteration;
	}


	public void setRobotIteration(int robotIteration) {
		this.robotIteration = robotIteration;
	}


	public HashMap<String, BlackListOperation> getBlackList() {
		return blackList;
	}


	public void setBlackList(HashMap<String, BlackListOperation> blackList) {
		this.blackList = blackList;
	}
	
	
}
