package hudson.plugins.starteam;


/**
 * This exception signals an error with StarTeam SCM.
 * 
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * 
 */
public class StarTeamSCMException extends Exception {

	/**
	 * @param message
	 * @param cause
	 */
	public StarTeamSCMException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param message
	 */
	public StarTeamSCMException(String message) {
		super(message);
	}

}