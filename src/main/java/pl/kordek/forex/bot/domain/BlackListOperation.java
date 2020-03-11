package pl.kordek.forex.bot.domain;

import java.io.Serializable;

public class BlackListOperation implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 6838757841804609325L;

	
	private String instrument;
	private int typeOfOperation;
	private long closeTime;
	public BlackListOperation(String instrument, int typeOfOperation, long closeTime) {
		super();
		this.instrument = instrument;
		this.typeOfOperation = typeOfOperation;
		this.closeTime = closeTime;
	}
	public String getInstrument() {
		return instrument;
	}
	public void setInstrument(String instrument) {
		this.instrument = instrument;
	}
	public int getTypeOfOperation() {
		return typeOfOperation;
	}
	public void setTypeOfOperation(int typeOfOperation) {
		this.typeOfOperation = typeOfOperation;
	}
	public long getCloseTime() {
		return closeTime;
	}
	public void setCloseTime(long closeTime) {
		this.closeTime = closeTime;
	}
	
	
	

}
