package hudson.plugins.starteam;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Collection;


public class StarteamSyncWorkSpaceTask implements FileCallable<Boolean> {
	
	private static final long serialVersionUID = 1815637601842899185L;
	private final String hostName;
	private final int port;
	private final String userName;
	private final String password;
	private final String projectName;
	private final String viewName;
	private final String folderName;
	private final String labelName;
	private boolean promotionstate;
	private TaskListener listener;
	private final Collection<StarTeamFilePoint> historicFilePoints;
	private final FilePath changeLog;
	private final FilePath filePointFilePath;
	private final int buildNumber;
	
	public StarteamSyncWorkSpaceTask(String hostname, int port, String projectname, String viewname, String foldername, String username, String password, String labelname, boolean promotionstate, Run<?, ?> build, FilePath changeLogFile, FilePath filePointFilePath, TaskListener listener) {
		this.hostName = hostname;
		this.port = port;
		this.userName = username;
		this.password = password;
		this.projectName = projectname;
		this.viewName = viewname;
		this.folderName = foldername;
		this.labelName = labelname;
		this.promotionstate = promotionstate;
		this.listener = listener;
		this.filePointFilePath = filePointFilePath;
		this.changeLog = changeLogFile;
		this.buildNumber = (build == null) ? -1 : build.getNumber();
		
		// Previous versions stored the build object as a member of StarTeamCheckoutActor. AbstractBuild
		// objects are not serializable, therefore the starteam plugin would break when remoting to
		// another machine. Instead of storing the build object the information from the build object
		// that is needed (historicFilePoints) is stored.
		
		// Get a list of files that require updating
		Collection<StarTeamFilePoint> historicFilePoints = null;
		Run<?, ?> lastBuild = (build == null) ? null : build.getPreviousBuild();
		if (lastBuild != null){
			try {
				File filePointFile = new File(lastBuild.getRootDir(), StarTeamConnection.FILE_POINT_FILENAME);
				if (filePointFile.exists()) {
					historicFilePoints = StarTeamFilePointFunctions.loadCollection(filePointFile);
				}
			} catch (IOException e) {
				e.printStackTrace(listener.getLogger());
			}
		}
		this.historicFilePoints = historicFilePoints;
	}
	

//	@Override
	public Boolean invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
		StarTeamConnection connection = new StarTeamConnection(hostName, port, userName, password, projectName, viewName, folderName, labelName, promotionstate);
		try {
			connection.initialize(buildNumber);
			StarTeamChangeSet changeSet;
			try {
				com.starteam.Folder rootFolder = connection.getRootFolder();
				listener.getLogger().println("creating change log file ");
				changeSet = connection.computeChangeSet(rootFolder,workspace,historicFilePoints,listener.getLogger());
				createChangeLog(changeSet, workspace, changeLog, listener, connection);
				listener.getLogger().println("storing change set");
				OutputStream os = null;
				try {
					os = new BufferedOutputStream(filePointFilePath.write());
					StarTeamFilePointFunctions.storeCollection(os, changeSet.getFilePointsToRemember());
				} catch (InterruptedException e) {
					listener.getLogger().println( "unable to store change set " +  e.getMessage()) ;
				}finally{
					if(os !=null){
						os.close();
					}
				}
				listener.getLogger().println("syncing workspace");
				connection.checkout(workspace);
				listener.getLogger().println("syncing workspace complete");
				connection.close();
			} catch (InterruptedException e) {
				listener.getLogger().println( "unable to create changelog file " +  e.getMessage()) ;
			}
		} catch (StarTeamSCMException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * create the change log file.
	 * @param aRootFile
	 * 		starteam root file
	 * @param aChangelogFile
	 * 		the file containing changes
	 * @param aListener
	 * 		the build listener
	 * @param aConnection
	 * 		the starteam connection.
	 * @return
	 * 		true if changelog file has been created, false if not.
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	protected boolean createChangeLog(StarTeamChangeSet changes, File aRootFile,FilePath aChangelogFile, TaskListener aListener, StarTeamConnection aConnection) throws IOException, InterruptedException {

		// create empty change log during call.
		if (changes == null) {
			listener.getLogger().println("last build date is null, creating an empty change log file");
			createEmptyChangeLog(aChangelogFile, aListener, "log");
			return true;
		}

		OutputStream os = new BufferedOutputStream(aChangelogFile.write());

		boolean created = false;
		try {
			created = StarTeamChangeLogBuilder.writeChangeLog(os, changes);
		} catch (Exception ex) {
			listener.getLogger().println("change log creation failed due to unexpected error : " + ex.getMessage());
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
	protected final boolean createEmptyChangeLog(FilePath aChangelogFile, TaskListener aListener, String aRootTag) throws InterruptedException {
		try {
			OutputStreamWriter writer = new OutputStreamWriter(aChangelogFile.write(), Charset.forName("UTF-8"));
			
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
