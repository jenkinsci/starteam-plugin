/**
 * 
 */
package hudson.plugins.starteam;

import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * This Actor class allow to check for changes in starteam repository between
 * builds.
 * 
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 * 
 */
public class StarTeamPollingTask implements FileCallable<Boolean> {

	/**
	 * serial version id.
	 */
	private static final long serialVersionUID = -5678102033953507247L;

	private String hostName;
	private int port;
	private String userName;
	private String password;
	private String projectName;
	private String viewName;
	private String folderName;
	private String labelName;
	private boolean promotionstate;
	private final TaskListener listener;
	private Collection<StarTeamFilePoint> historicFilePoints;

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
	 * @param listener Hudson task listener.
	 * @param historicFilePoints  
	 */
	public StarTeamPollingTask(String hostname, int port, String user, String passwd, String projectname, String viewname,
			String foldername, String labelName, boolean promotionstate, TaskListener listener, Collection<StarTeamFilePoint> historicFilePoints) {
		this.hostName = hostname;
		this.port = port;
		this.userName = user;
		this.password = passwd;
		this.projectName = projectname;
		this.viewName = viewname;
		this.folderName = foldername;
		this.listener = listener;
		this.labelName = labelName;
		this.promotionstate = promotionstate;
		this.historicFilePoints = historicFilePoints;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.FilePath.FileCallable#invoke(java.io.File,
	 *      hudson.remoting.VirtualChannel)
	 */
	public Boolean invoke(File f, VirtualChannel channel) throws IOException {
		StarTeamConnection connection = new StarTeamConnection(hostName, port, userName, password, projectName, viewName, folderName, labelName, promotionstate);
		try {
			connection.initialize();
		} catch (StarTeamSCMException e) {
			listener.getLogger().println(e.getLocalizedMessage());
			connection.close();
			return false;
		}

		StarTeamChangeSet changeSet = null;
		try {
			changeSet = connection.computeChangeSet(connection.getRootFolder(), f, historicFilePoints , listener.getLogger());
		} catch (StarTeamSCMException e) {
			e.printStackTrace(listener.getLogger());
		}
		connection.close();
		if (changeSet != null && changeSet.hasChanges()) {
			return true;
		}
		return false;
	}

}
