package pl.kordek.forex.bot.domain;

import java.io.Serializable;
import java.util.HashMap;

import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.TradingRecord;

public class RobotInfo implements Serializable{
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -4557097264380136922L;

	
	private HashMap<String, TradingRecord> longTradingRecordMap;
	private HashMap<String, TradingRecord> shortTradingRecordMap;
	private HashMap<String, BaseBarSeries> baseBarSeriesMap;
	private HashMap<String,BlackListOperation> blackList;
	private int robotIteration;



	public RobotInfo(HashMap<String, TradingRecord> longTradingRecordMap, HashMap<String, TradingRecord> shortTradingRecordMap, HashMap<String, BaseBarSeries> baseBarSeriesMap,
			HashMap<String, BlackListOperation> blackList, int robotIteration) {
		super();
		this.longTradingRecordMap = longTradingRecordMap;
		this.shortTradingRecordMap = shortTradingRecordMap;
		this.baseBarSeriesMap = baseBarSeriesMap;
		this.blackList = blackList;
		this.robotIteration = robotIteration;
	}


	public HashMap<String, TradingRecord> getLongTradingRecordMap() {
		return longTradingRecordMap;
	}

	public void setLongTradingRecordMap(HashMap<String, TradingRecord> longTradingRecordMap) {
		this.longTradingRecordMap = longTradingRecordMap;
	}

	public HashMap<String, TradingRecord> getShortTradingRecordMap() {
		return shortTradingRecordMap;
	}

	public void setShortTradingRecordMap(HashMap<String, TradingRecord> shortTradingRecordMap) {
		this.shortTradingRecordMap = shortTradingRecordMap;
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

	public HashMap<String, BaseBarSeries> getBaseBarSeriesMap() {
		return baseBarSeriesMap;
	}

	public void setBaseBarSeriesMap(HashMap<String, BaseBarSeries> baseBarSeriesMap) {
		this.baseBarSeriesMap = baseBarSeriesMap;
	}
}
