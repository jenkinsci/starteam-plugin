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
 * @author ip90568
 * 
 */
public class StarTeamPollingActor implements FileCallable<Boolean> {

	private final StarTeamConnection connection;
	private final TaskListener listener;
	private final Date sinceDate;

	/**
	 * @param hostname
	 * @param port
	 * @param user
	 * @param passwd
	 * @param projectname
	 * @param viewname
	 * @param foldername
	 * @param sinceDate
	 * @param listener
	 */
	public StarTeamPollingActor(String hostname, int port, String user,
			String passwd, String projectname, String viewname,
			String foldername, Date sinceDate, TaskListener listener) {
		this.connection = new StarTeamConnection(hostname, port, user, passwd,
				projectname, viewname, foldername);
		this.listener = listener;
		this.sinceDate = sinceDate;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.FilePath.FileCallable#invoke(java.io.File,
	 *      hudson.remoting.VirtualChannel)
	 */
	public Boolean invoke(File f, VirtualChannel channel) throws IOException {
		try {
			connection.initialize();
		} catch (StarTeamSCMException e) {
			listener.getLogger().println(e.getLocalizedMessage());
			connection.close();
			return false;
		}
		if (connection.findChangedFiles(f, listener.getLogger(), sinceDate)
				.isEmpty()) {
			return false;
		}
		return true;
	}

	/**
	 * 
	 */
	public void dispose() {
		connection.close();
	}

}
