package pl.kordek.forex.bot.exceptions;

public class LoginFailedException extends Exception{
	/**
	 * 
	 */
	private static final long serialVersionUID = -5952479166679634230L;

	public LoginFailedException(String message) {
		super(message);
	}
}
