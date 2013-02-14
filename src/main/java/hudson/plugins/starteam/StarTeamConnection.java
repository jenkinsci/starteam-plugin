/**
 *
 */
package hudson.plugins.starteam;

import hudson.FilePath;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.exception.ExceptionUtils;

import com.starbase.starteam.ClientApplication;
import com.starbase.starteam.File;
import com.starbase.starteam.Folder;
import com.starbase.starteam.Item;
import com.starbase.starteam.LogonException;
import com.starbase.starteam.Project;
import com.starbase.starteam.PropertyNames;
import com.starbase.starteam.Server;
import com.starbase.starteam.ServerAdministration;
import com.starbase.starteam.ServerConfiguration;
import com.starbase.starteam.ServerInfo;
import com.starbase.starteam.Status;
import com.starbase.starteam.User;
import com.starbase.starteam.UserAccount;
import com.starbase.starteam.View;
import com.starbase.util.OLEDate;

/**
 * StarTeamActor is a class that implements connecting to a StarTeam repository,
 * to a given project, view and folder.
 * 
 * Add functionality allowing to delete non starteam file in folder while
 * performing listing of all files. and to perform creation of changelog file
 * during the checkout
 *
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 *
 */
public class StarTeamConnection implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final String FILE_POINT_FILENAME = "starteam-filepoints.csv";

	private final String hostName;
	private final int port;
	private final String userName;
	private final String password;
	private final String projectName;
	private final String viewName;
	private final String folderName;
	private final StarTeamViewSelector configSelector;

	private transient Server server;
	private transient View view;
	private transient Folder rootFolder;
	private transient Project project;
	private transient ServerAdministration srvAdmin;
	/**
	 * Default constructor
	 *
	 * @param hostName
	 *            the starteam server host / ip name
	 * @param port
	 *            starteam server port
	 * @param userName
	 *            user used to connect starteam server
	 * @param password
	 *            user password to connect to starteam server
	 * @param projectName
	 *            starteam project's name
	 * @param viewName
	 *            starteam view's name
	 * @param folderName
	 *            starteam folder's name
	 * @param configSelector 
	 *            configuration selector 
	 *            in case of checking from label, promotion state or time
	 */
	public StarTeamConnection(String hostName, int port, String userName, String password, String projectName, String viewName, String folderName, StarTeamViewSelector configSelector) {
		checkParameters(hostName, port, userName, password, projectName, viewName, folderName);
		this.hostName = hostName;
		this.port = port;
		this.userName = userName;
		this.password = password;
		this.projectName = projectName;
		this.viewName = viewName;
		this.folderName = folderName;
		this.configSelector = configSelector;
	}

	public StarTeamConnection(StarTeamConnection oldConnection, StarTeamViewSelector configSelector) {
		this(oldConnection.hostName, oldConnection.port,
				oldConnection.userName, oldConnection.password,
				oldConnection.projectName, oldConnection.viewName,
				oldConnection.folderName, configSelector);
	}
	private ServerInfo createServerInfo() {
		ServerInfo serverInfo = new ServerInfo();
		serverInfo.setConnectionType(ServerConfiguration.PROTOCOL_TCP_IP_SOCKETS);
		serverInfo.setHost(this.hostName);
		serverInfo.setPort(this.port);

		populateDescription(serverInfo);

		return serverInfo;
	}

	/**
	 * populate the description of the server info.
	 *
	 * @param serverInfo
	 */
	void populateDescription(ServerInfo serverInfo) {
		// Increment a counter until the description is unique
		int counter = 0;
		while (!setDescription(serverInfo, counter))
			++counter;
	}

	private boolean setDescription(ServerInfo serverInfo, int counter) {
		try {
			serverInfo.setDescription("StarTeam connection to " + this.hostName + ((counter == 0) ? "" : " (" + Integer.toString(counter) + ")"));
			return true;
		} catch (com.starbase.starteam.DuplicateServerListEntryException e) {
			return false;
		}
	}

	private void checkParameters(String hostName, int port, String userName, String password, String projectName, String viewName,
			String folderName) {
		if (null == hostName)
			throw new NullPointerException("hostName cannot be null");
		if (null == userName)
			throw new NullPointerException("user cannot be null");
		if (null == password)
			throw new NullPointerException("passwd cannot be null");
		if (null == projectName)
			throw new NullPointerException("projectName cannot be null");
		if (null == viewName)
			throw new NullPointerException("viewName cannot be null");
		if (null == folderName)
			throw new NullPointerException("folderName cannot be null");

		if ((port < 1) || (port > 65535))
			throw new IllegalArgumentException("Invalid port: " + port);
	}

	/**
	 * Initialize the connection. This means logging on to the server and
	 * finding the project, view and folder we want.
	 * 
	 * @throws StarTeamSCMException if logging on fails.
	 */
	public void initialize() throws StarTeamSCMException {
		/* 
		   Identify this as the StarTeam Hudson Plugin 
		   so that it can support the new AppControl capability in StarTeam 2009
		   which allows a StarTeam administrator to block or allow Unknown or specific 
		   client/SDK applications from accessing the repository; without this, the plugin
		   will be seen as an Unknown Client, and may be blocked by StarTeam repositories
		   that take advantage of this feature.  This must be called before a connection
		   to the server is established.
		*/ 
		ClientApplication.setName("StarTeam Plugin for Jenkins");
		
		server = new Server(createServerInfo());
		server.connect();
		try {
			server.logOn(userName, password);
		} catch (LogonException e) {
			throw new StarTeamSCMException("Could not log on: " + e.getErrorMessage());
		}

		project = findProjectOnServer(server, projectName);
		view = findViewInProject(project, viewName);
		if (configSelector != null)
		{
			View configuredView = null;
			try {
				configuredView = configSelector.configView(view);
			} catch (ParseException e) {
				throw new StarTeamSCMException("Could not correctly parse configuration date: " + e.getMessage());
			}
			if (configuredView != null)
				view = configuredView;
		}
		rootFolder = StarTeamFunctions.findFolderInView(view, folderName);

		// Cache some folder data
		final PropertyNames pnames = rootFolder.getPropertyNames();
		final String[] propsToCache = new String[] { pnames.FILE_LOCAL_FILE_EXISTS, pnames.FILE_LOCAL_TIMESTAMP, pnames.FILE_NAME,
				pnames.FILE_FILE_TIME_AT_CHECKIN, pnames.MODIFIED_TIME, pnames.MODIFIED_USER_ID, pnames.FILE_STATUS };
		rootFolder.populateNow(server.getTypeNames().FILE, propsToCache, -1);
	}

	/**
	 * checkout the files from starteam
	 *
	 * @param changeSet a description of changes  
	 * @param filePointFilePath A FilePath reprensenting the file points file where to store the change set
	 * @throws IOException if checkout fails.
	 */
	public void checkOut(StarTeamChangeSet changeSet, PrintStream logger, FilePath filePointFilePath) throws IOException {
	    logger.println("*** Performing checkout on [" + changeSet.getFilesToCheckout().size() + "] files");
	    boolean quietCheckout = changeSet.getFilesToCheckout().size() >= 2000;
	    if (quietCheckout) {
	      logger.println("*** More than 2000 files, quiet mode enabled");
	    }
		for (File f : changeSet.getFilesToCheckout()) {
			boolean dirty = true;
			switch (f.getStatus()) {
				case Status.UNKNOWN:
					dirty = false;
				case Status.NEW:
				case Status.MERGE:
				case Status.MODIFIED:
					// clobber these
					new java.io.File(f.getFullName()).delete();
				    if (!quietCheckout) logger.println("[co] Deleted File: " + f.getFullName());
					break;
				case Status.MISSING:
				case Status.OUTOFDATE:
					dirty = false;
					// just go on and check out
					break;
				default:
					// By default do nothing, go to next iteration
					continue;
			}
			if (!quietCheckout)
				logger.println("[co] " + f.getFullName() + "... attempt");
			try {
				f.checkout(Item.LockType.UNCHANGED, // leave the lock as is, changing lock for item in the past is impossible
						true, // use timestamp from local time
						true, // convert EOL to native format
						true); // update status
			} catch (IOException e) {
				logger
						.print("[checkout] [exception] [Problem checking out file: "
								+ f.getFullName()
								+ "] \n"
								+ ExceptionUtils.getFullStackTrace(e) + "\n");
				throw e;
			} catch (RuntimeException e) {
				logger
						.print("[checkout] [exception] [Problem checking out file: "
								+ f.getFullName()
								+ "] \n"
								+ ExceptionUtils.getFullStackTrace(e) + "\n");
				throw e;
			}
			if (dirty) {
				changeSet.getChanges().add(FileToStarTeamChangeLogEntry(f,"dirty"));
			}
			f.discard();
			if (!quietCheckout) logger.println("[co] " + f.getFullName() + "... ok");
		}
		logger.println("*** removing [" + changeSet.getFilesToRemove().size() + "] files");
		boolean quietDelete = changeSet.getFilesToRemove().size() > 100;
		if (quietDelete) {
			logger.println("*** More than 100 files, quiet mode enabled");
		}
		for (java.io.File f : changeSet.getFilesToRemove()) {
			if (f.exists()) {
				if (!quietDelete) logger.println("[remove] [" + f + "]");
				f.delete();
			} else {
				logger.println("[remove:warn] Planned to remove [" + f + "]");
			}
		}
		logger.println("*** storing change set");
		OutputStream os = null;
		try {
			os = new BufferedOutputStream(filePointFilePath.write());
			StarTeamFilePointFunctions.storeCollection(os, changeSet.getFilePointsToRemember());
		} catch (InterruptedException e) {
			logger.println( "unable to store change set " +  e.getMessage()) ;
		}finally{
			if(os !=null){
				os.close();
			}
		}
		logger.println("***checkout done");
	}

	/**
	 * Returns the name of the user on the StarTeam server with the specified
	 * id. StarTeam stores user IDs as int values and this method will translate
	 * those into the actual user name. <br/> This can be used, for example,
	 * with a StarTeam {@link Item}'s {@link Item#getModifiedBy()} property, to
	 * determine the name of the user who made a modification to the item.
	 *
	 * @param userId
	 *            the id of the user on the StarTeam Server
	 * @return the name of the user as provided by the StarTeam Server
	 */
	public String getUsername(int userId) {
		boolean canReadUserAccts = true;
		User stUser = server.getUser(userId);
		String userName =stUser.getName();
		srvAdmin = server.getAdministration();
		UserAccount[] userAccts = null;
		try {
			userAccts = srvAdmin.getUserAccounts();
		} catch (Exception e) {
			// System.out.println("WARNING: Looks like this user does not have the permission to access UserAccounts on the StarTeam Server!");
			// System.out.println("WARNING: Please contact your administrator and ask to be given the permission \"Administer User Accounts\" on the server.");
			// System.out.println("WARNING: Defaulting to just using User Full Names which breaks the ability to send email to the individuals who break the build in Hudson!");
			canReadUserAccts = false;
		}
		if (canReadUserAccts) {
			UserAccount ua = userAccts[0];
			for (int i=0; i<userAccts.length; i++) {
				ua = userAccts[i];
				if (ua.getName().equals(userName)) {
					System.out.println("INFO: From \'" + userName + "\' found existing user LogonName = " +
							ua.getLogOnName() + " with ID \'" + ua.getID() + "\' and email \'" + ua.getEmailAddress() +"\'");
					return ua.getLogOnName();
				}
			}
		} else {
			// Since the user account running the build does not have user admin perms
			// use the User Full Name
			return server.getUser(userId).getName();
		}
		return "unknown";
	}

	public Folder getRootFolder() {
		return rootFolder;
	}

	public OLEDate getServerTime() {
		return server.getCurrentTime();
	}

	/**
	 * @param server
	 * @param projectname
	 * @return Project specified by the projectname
	 * @throws StarTeamSCMException
	 */
	static Project findProjectOnServer(final Server server, final String projectname) throws StarTeamSCMException {
		for (Project project : server.getProjects()) {
			if (project.getName().equals(projectname)) {
				return project;
			}
		}
		throw new StarTeamSCMException("Couldn't find project " + projectname + " on server " + server.getAddress());
	}

	/**
	 * @param project
	 * @param viewname
	 * @return
	 * @throws StarTeamSCMException
	 */
	static View findViewInProject(final Project project, final String viewname) throws StarTeamSCMException {
		for (View view : project.getAccessibleViews()) {
			if (view.getName().equals(viewname)) {
				return view;
			}
		}
		throw new StarTeamSCMException("Couldn't find view " + viewname + " in project " + project.getName());
	}

	/**
	 * Close the connection.
	 */
	public void close() {
		if (server.isConnected()) {
			if (rootFolder != null)	{
				rootFolder.discardItems(rootFolder.getTypeNames().FILE, -1);
			}
			view.discard();
			project.discard();
			server.disconnect();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		close();
	}

	@Override
	public boolean equals(Object object) {
		if (null == object)
			return false;

		if (!getClass().equals(object.getClass()))
			return false;

		StarTeamConnection other = (StarTeamConnection) object;

		return port == other.port && hostName.equals(other.hostName) && userName.equals(other.userName) &&
				password.equals(other.password) && projectName.equals(other.projectName) && viewName.equals(other.viewName) &&
				folderName.equals(other.folderName);
	}

	@Override
	public int hashCode() {
		return userName.hashCode();
	}

	@Override
	public String toString() {
		return "host: " + hostName + ", port: " + Integer.toString(port) + ", user: " + userName + ", passwd: ******, project: " +
				projectName + ", view: " + viewName + ", folder: " + folderName;
	}

	  /**
	 * @param rootFolder main project directory
	 * @param workspace a workspace directory
	 * @param historicFilePoints a collection containing File Points to be compared (previous build)
	 * @param logger a logger for consuming log messages
	 * @return set of changes  
	 * @throws StarTeamSCMException
	 * @throws IOException
	 */
	public StarTeamChangeSet computeChangeSet(Folder rootFolder, java.io.File workspace, final Collection<StarTeamFilePoint> historicFilePoints, PrintStream logger) throws StarTeamSCMException, IOException {
	    // --- compute changes as per starteam

	    final Collection<com.starbase.starteam.File> starteamFiles = StarTeamFunctions.listAllFiles(rootFolder, workspace);
	    final Map<java.io.File, com.starbase.starteam.File> starteamFileMap = StarTeamFunctions.convertToFileMap(starteamFiles);
	    final Collection<java.io.File> starteamFileSet = starteamFileMap.keySet();
	    final Collection<StarTeamFilePoint> starteamFilePoint = StarTeamFilePointFunctions.convertFilePointCollection(starteamFiles);

	    final Collection<java.io.File> fileSystemFiles = StarTeamFilePointFunctions.listAllFiles(workspace);
	    final Collection<java.io.File> fileSystemRemove = new TreeSet<java.io.File>(fileSystemFiles);
	    fileSystemRemove.removeAll(starteamFileSet);

	    final StarTeamChangeSet changeSet = new StarTeamChangeSet();
	    changeSet.setFilesToCheckout(starteamFiles);
	    changeSet.setFilesToRemove(fileSystemRemove);
	    changeSet.setFilePointsToRemember(starteamFilePoint);

	    // --- compute differences as per historic storage file

	    if (historicFilePoints != null && !historicFilePoints.isEmpty()) {

	      try {

	        changeSet.setComparisonAvailable(true);

	        computeDifference(starteamFilePoint, historicFilePoints, changeSet, starteamFileMap);

	      } catch (Throwable t) {
	        t.printStackTrace(logger);
	      }
	    } else {
	    	for (File file: starteamFiles)
	    	{
	    		changeSet.addChange(FileToStarTeamChangeLogEntry(file));
	    	}
	    }

	    return changeSet;
	  }

	public StarTeamChangeLogEntry FileToStarTeamChangeLogEntry (File f)
	{
		return FileToStarTeamChangeLogEntry(f, "change");
	}

	public StarTeamChangeLogEntry FileToStarTeamChangeLogEntry (File f, String change)
	{
		int revisionNumber = f.getContentVersion();
		int userId = f.getModifiedBy();
		String username = getUsername(userId);
		String msg = f.getComment();
		Date date = new Date(f.getModifiedTime().getLongValue());
		String fileName = f.getName();		

		return new StarTeamChangeLogEntry(fileName,revisionNumber,date,username,msg, change);
	}

	public StarTeamChangeSet computeDifference(final Collection<StarTeamFilePoint> currentFilePoint, final Collection<StarTeamFilePoint> historicFilePoint, StarTeamChangeSet changeSet, Map<java.io.File, com.starbase.starteam.File> starteamFileMap) {
		  final Map<java.io.File, StarTeamFilePoint> starteamFilePointMap = StarTeamFilePointFunctions.convertToFilePointMap(currentFilePoint);
		  Map<java.io.File, StarTeamFilePoint> historicFilePointMap = StarTeamFilePointFunctions.convertToFilePointMap(historicFilePoint);
	
		  final Set<java.io.File> starteamOnly = new HashSet<java.io.File>();
		  starteamOnly.addAll(starteamFilePointMap.keySet());
		  starteamOnly.removeAll(historicFilePointMap.keySet());
	
		  final Set<java.io.File> historicOnly = new HashSet<java.io.File>();
		  historicOnly.addAll(historicFilePointMap.keySet());
		  historicOnly.removeAll(starteamFilePointMap.keySet());
	
		  final Set<java.io.File> common = new HashSet<java.io.File>();
		  common.addAll(starteamFilePointMap.keySet());
		  common.removeAll(starteamOnly);
	
		  final Set<java.io.File> higher = new HashSet<java.io.File>(); // newer revision
		  final Set<java.io.File> lower = new HashSet<java.io.File>(); // typically rollback of a revision
		  StarTeamChangeLogEntry change;
	
		  for (java.io.File f : common) {
			  StarTeamFilePoint starteam = starteamFilePointMap.get(f);
			  StarTeamFilePoint historic = historicFilePointMap.get(f);
	
			  if (starteam.getRevisionNumber() == historic.getRevisionNumber()) {
				  //unchanged files
				  continue;
			  }
			  com.starbase.starteam.File stf = starteamFileMap.get(f);
			  if (starteam.getRevisionNumber() > historic.getRevisionNumber()) {
				  higher.add(f);
				  changeSet.addChange(FileToStarTeamChangeLogEntry(stf,"change"));
			  }
			  if (starteam.getRevisionNumber() < historic.getRevisionNumber()) {
				  lower.add(f);
				  changeSet.addChange(FileToStarTeamChangeLogEntry(stf,"rollback"));
			  }
		  }
	
		  for (java.io.File f : historicOnly) {
			  StarTeamFilePoint historic = historicFilePointMap.get(f);
			  change = new StarTeamChangeLogEntry(f.getName(), historic.getRevisionNumber(), new Date(), "", "", "removed");
			  changeSet.addChange(change);
		  }
		  for (java.io.File f : starteamOnly) {
			  com.starbase.starteam.File stf = starteamFileMap.get(f);
			  changeSet.addChange(FileToStarTeamChangeLogEntry(stf,"added"));
		  }
	
		  return changeSet;
	  }
}
