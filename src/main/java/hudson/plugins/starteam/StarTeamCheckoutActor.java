package hudson.plugins.starteam;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Date;
//import java.util.Map;

import com.starbase.starteam.Folder;

/**
 * A helper class for transparent checkout operations over the network. Can be
 * used with FilePath.act() to do a checkout on a remote node.
 * 
 * It allows now to create the changelog file if required.
 *
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 */
class StarTeamCheckoutActor implements FileCallable<Boolean> {

	/**
	 * serial version id.
	 */
	private static final long serialVersionUID = -3748818546244161292L;

	private final Date previousBuildDate;
	private final FilePath changelog;
	private final BuildListener listener;
	private final String hostname;
	private final int port;
	private final String user;
	private final String passwd;
	private final String projectname;
	private final String viewname;
	private final String foldername;
	private final StarTeamViewSelector config;
	private final AbstractBuild<?,?> build;

	/**
	 * 
	 * Default constructor for the checkout actor.
	 * 
	 * @param hostname
	 * 		starteam host name
	 * @param port
	 * 		starteam port
	 * @param user
	 * 		starteam connection user
	 * @param passwd
	 * 		starteam connection password
	 * @param projectname
	 * 		starteam project name
	 * @param viewname
	 * 		starteam view name
	 * @param foldername
	 * 		starteam folder name
	 * @param config
	 * 		configuration selector
	 * @param previousBuildDate
	 * 		hudson previous build date
	 * @param currentBuildDate
	 * 		hudson current date
	 * @param changelogFile
	 * 		change log file, as a filepath, to be able to write remotely.
	 * @param listener
	 * 		the build listener
	 */
	public StarTeamCheckoutActor(String hostname, int port, String user,
			String passwd, String projectname, String viewname,
			String foldername, StarTeamViewSelector config, Date previousBuildDate, Date currentBuildDate,
			FilePath changelogFile, BuildListener listener,	AbstractBuild build ) {
		this.hostname = hostname;
		this.port = port;
		this.user = user;
		this.passwd = passwd;
		this.projectname = projectname;
		this.viewname = viewname;
		this.foldername = foldername;
		this.previousBuildDate = previousBuildDate;
		this.changelog = changelogFile;
		this.listener = listener;
		this.config = config;
		this.build = build;
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
				projectname, viewname, foldername, config);
		try {
			connection.initialize();
		} catch (StarTeamSCMException e) {
			listener.getLogger().println(e.getLocalizedMessage());
			return false;
		}
		
		// Get a list of files that require updating
		Collection<StarTeamFilePoint> historicFilePoints = null;
		Folder rootFolder = connection.getRootFolder();
		StarTeamChangeSet changeSet;
		try {
			changeSet = connection.computeChangeSet(rootFolder,workspace,historicFilePoints,listener.getLogger());
			// Check 'em out
			listener.getLogger().println("performing checkout ...");
			File buildDir = ( build != null )?  build.getRootDir() : workspace; 

			connection.checkOut(changeSet, listener.getLogger(), buildDir);

			listener.getLogger().println("creating change log file ");
			try {
				createChangeLog(changeSet, workspace, changelog, listener,
						this.previousBuildDate, connection);
			} catch (InterruptedException e) {
				listener.getLogger().println( "unable to create changelog file " +  e.getMessage()) ;
			}
		} catch (StarTeamSCMException e1) {
			e1.printStackTrace(listener.getLogger());
		}
		// close the connection
		connection.close();
		return true;
	}

	/**
	 * create the change log file.
	 * @param aNowFiles
	 * 		list of current starteam files (loaded during checkout)
	 * @param aRootFile
	 * 		starteam root file
	 * @param aChangelogFile
	 * 		the file containing changes
	 * @param aListener
	 * 		the build listener
	 * @param aLastBuildDate
	 * 		the last hudson build date (synchronized with starteam server date)
	 * @param aConnection
	 * 		the starteam connection.
	 * @return
	 * 		true if changelog file has been created, false if not.
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	protected boolean createChangeLog(
			StarTeamChangeSet changes, File aRootFile,
			FilePath aChangelogFile, BuildListener aListener, Date aLastBuildDate,
			StarTeamConnection aConnection) throws IOException, InterruptedException {


		// create empty change log during call.
		if (aLastBuildDate == null) {
			listener
					.getLogger()
					.println(
							"last build date is null, creating an empty change log file");
			createEmptyChangeLog(aChangelogFile, aListener, "log");
			return true;
		}

		
		OutputStream os = new BufferedOutputStream(
				aChangelogFile.write());

		boolean created = false;
		try {
			created = StarTeamChangeLogBuilder.writeChangeLog(os, changes);
		} catch (Exception ex) {
			listener.getLogger().println(
					"change log creation failed due to unexpected error : "
							+ ex.getMessage());
		} finally {
			os.close();
		}

		if (!created)
			createEmptyChangeLog(aChangelogFile, aListener, "log");

		return true;
	}

	/**
	 * create the empty change log file.
	 * @param aChangelogFile
	 * @param aListener
	 * @param aRootTag
	 * @return
	 * @throws InterruptedException 
	 */
	protected final boolean createEmptyChangeLog(FilePath aChangelogFile,
			BuildListener aListener, String aRootTag) throws InterruptedException {
		try {
			OutputStreamWriter writer = new OutputStreamWriter(aChangelogFile.write(),
					Charset.forName("UTF-8"));
			
			PrintWriter printwriter = new PrintWriter( writer ) ;

			printwriter.write("<" + aRootTag + "/>");
			printwriter.close();
			return true;
		} catch (IOException e) {
			e.printStackTrace(aListener.error(e.getMessage()));
			return false;
		}
	}

}
