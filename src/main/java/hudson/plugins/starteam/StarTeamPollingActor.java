/**
 * 
 */
package hudson.plugins.starteam;

import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * This Actor class allow to check for changes in starteam repository between
 * builds.
 * 
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 * 
 */
public class StarTeamPollingActor implements FileCallable<Boolean> {

	/**
	 * serial version id.
	 */
	private static final long serialVersionUID = -5678102033953507247L;

	private String hostname;

	private int port;

	private String user;

	private String passwd;

	private String projectname;

	private String viewname;

	private String foldername;

	private final TaskListener listener;
	
	private final Date sinceDate;

	private final Date currentDate;

	private final StarTeamViewSelector config;

	/**
	 * Default constructor.
	 * @param hostname starteam host name
	 * @param port  starteam port
	 * @param user  starteam connection user name 
	 * @param passwd starteam connection password
	 * @param projectname starteam project name
	 * @param viewname  starteam view name
	 * @param foldername starteam parent folder name
	 * @param config configuration selector
	 * @param sinceDate starteam last build date
	 * @param currentDate starteam current date
	 * @param listener Hudson task listener.
	 */
	public StarTeamPollingActor(String hostname, int port, String user,
			String passwd, String projectname, String viewname,
			String foldername, StarTeamViewSelector config, Date sinceDate, Date currentDate, TaskListener listener) {
		this.hostname = hostname;
		this.port = port;
		this.user = user;
		this.passwd = passwd;
		this.projectname = projectname;
		this.viewname = viewname;
		this.foldername = foldername;
		this.listener = listener;
		this.sinceDate = sinceDate;
		this.currentDate = currentDate;
		this.config = config;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.FilePath.FileCallable#invoke(java.io.File,
	 *      hudson.remoting.VirtualChannel)
	 */
	public Boolean invoke(File f, VirtualChannel channel) throws IOException {

		if (sinceDate == null) {
			return false ;
		}

		StarTeamConnection connection = new StarTeamConnection(
				hostname, port, user, passwd,
				projectname, viewname, foldername, config);
		try {
			connection.initialize();
		} catch (StarTeamSCMException e) {
			listener.getLogger().println(e.getLocalizedMessage());
			connection.close();
			return false;
		}
		Date synchronizedSinceDate = connection
				.calculatePreviousDateWithTimeZoneCheck(sinceDate, currentDate);
		if (connection.findChangedFiles(f, listener.getLogger(),
				synchronizedSinceDate).isEmpty()) {
			connection.close();
			return false;
		}
		connection.close();
		return true;
	}

}
