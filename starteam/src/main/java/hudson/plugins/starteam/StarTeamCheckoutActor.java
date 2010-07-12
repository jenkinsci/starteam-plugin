package hudson.plugins.starteam;

import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;

/**
 * A helper class for transparent checkout operations over the network. Can be
 * used with FilePath.act() to do a checkout on a remote node.
 * 
 * @author ip90568
 */
class StarTeamCheckoutActor implements FileCallable<Boolean> {

	private final Date buildDate;
	private final File changelog;
	private final BuildListener listener;
	private String hostname;
	private int port;
	private String user;
	private String passwd;
	private String projectname;
	private String viewname;
	private String foldername;

	/**
	 * @param hostname
	 * @param port
	 * @param user
	 * @param passwd
	 * @param projectname
	 * @param viewname
	 * @param foldername
	 * @param buildDate
	 * @param changelogFile
	 * @param listener
	 */
	public StarTeamCheckoutActor(String hostname, int port, String user,
			String passwd, String projectname, String viewname,
			String foldername, Date buildDate, File changelogFile,
			BuildListener listener) {
		this.hostname = hostname;
		this.port = port;
		this.user = user;
		this.passwd = passwd;
		this.projectname = projectname;
		this.viewname = viewname;
		this.foldername = foldername;
		this.buildDate = buildDate;
		this.changelog = changelogFile;
		this.listener = listener;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.FilePath.FileCallable#invoke(java.io.File,
	 *      hudson.remoting.VirtualChannel)
	 */
	public Boolean invoke(File workspace, VirtualChannel channel)
			throws IOException {
		StarTeamConnection connection = new StarTeamConnection(
				hostname, port, user, passwd,
				projectname, viewname, foldername);
		try {
			connection.initialize();
		} catch (StarTeamSCMException e) {
			listener.getLogger().println(e.getLocalizedMessage());
			return false;
		}

		// Get a list of files that require updating
		Collection<com.starbase.starteam.File> changedFiles = connection.findAllFiles(workspace, listener.getLogger());
		// Check 'em out
		connection.checkOut(changedFiles, listener.getLogger());
		// TODO: create changelog
		connection.close();
		return true;
	}
}
