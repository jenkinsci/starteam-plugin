/**
 * 
 */
package hudson.plugins.starteam;

import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
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

	private final StarTeamViewSelector config;

	private AbstractBuild lastBuild;

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
	 * @param lastBuild 
	 */
	public StarTeamPollingActor(String hostname, int port, String user,
			String passwd, String projectname, String viewname,
			String foldername, StarTeamViewSelector config, TaskListener listener, AbstractBuild lastBuild) {
		this.hostname = hostname;
		this.port = port;
		this.user = user;
		this.passwd = passwd;
		this.projectname = projectname;
		this.viewname = viewname;
		this.foldername = foldername;
		this.listener = listener;
		this.config = config;
		this.lastBuild=lastBuild;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.FilePath.FileCallable#invoke(java.io.File,
	 *      hudson.remoting.VirtualChannel)
	 */
	public Boolean invoke(File f, VirtualChannel channel) throws IOException {

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

		Collection<StarTeamFilePoint> historicFilePoints = null;
		if (lastBuild != null){
			File filePointFile = new File(lastBuild.getRootDir(),StarTeamConnection.FILE_POINT_FILENAME);
			if(filePointFile.exists() ) {
	          historicFilePoints = StarTeamFilePointFunctions.loadCollection(filePointFile);
			}
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
