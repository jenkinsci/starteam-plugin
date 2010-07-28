/**
 *
 */
package hudson.plugins.starteam;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starbase.starteam.File;
import com.starbase.starteam.Folder;
import com.starbase.starteam.Item;
import com.starbase.starteam.LogonException;
import com.starbase.starteam.Project;
import com.starbase.starteam.PropertyNames;
import com.starbase.starteam.Server;
import com.starbase.starteam.ServerConfiguration;
import com.starbase.starteam.ServerAdministration;
import com.starbase.starteam.ServerInfo;
import com.starbase.starteam.Status;
import com.starbase.starteam.User;
import com.starbase.starteam.UserAccount;
import com.starbase.starteam.View;
import com.starbase.starteam.ViewConfiguration;
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
			try {
				view = configSelector.configView(view);
			} catch (ParseException e) {
				throw new StarTeamSCMException("Could not correctly parse configuration date: " + e.getMessage());
			}
		}
		rootFolder = findFolderInView(view, folderName);

		// Cache some folder data
		final PropertyNames pnames = rootFolder.getPropertyNames();
		final String[] propsToCache = new String[] { pnames.FILE_LOCAL_FILE_EXISTS, pnames.FILE_LOCAL_TIMESTAMP, pnames.FILE_NAME,
				pnames.FILE_FILE_TIME_AT_CHECKIN, pnames.MODIFIED_TIME, pnames.MODIFIED_USER_ID, pnames.FILE_STATUS };
		rootFolder.populateNow(server.getTypeNames().FILE, propsToCache, -1);
	}

	/**
	 * checkout the files from starteam
	 *
	 * @param filesToCheckOut
	 * @throws IOException if checkout fails.
	 */
	public void checkOut(Collection<File> filesToCheckOut, PrintStream logger) throws IOException {
		logger.println("*** Performing checkout");
		for (File f : filesToCheckOut) {
			switch (f.getStatus()) {
				case Status.MERGE:
				case Status.MODIFIED:
				case Status.UNKNOWN:
				case Status.NEW:
					// clobber these
					new java.io.File(f.getFullName()).delete();
				logger.println("[co] Deleted File: " + f.getFullName());
					break;
				case Status.MISSING:
				case Status.OUTOFDATE:
					// just go on and check out
					break;
				default:
					// By default do nothing, go to next iteration
					continue;
			}
			logger.print("[co] " + f.getFullName() + "... \n");
			f.checkout(Item.LockType.UNLOCKED, // check out as unlocked
					true, // use timestamp from local time
					true, // convert EOL to native format
					true); // update status
			f.discard();
			// logger.println("ok");
		}
		// logger.println("*** done");
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
			System.out.println("WARNING: Looks like this user does not have the permission to access UserAccounts on the StarTeam Server!");
			System.out.println("WARNING: Please contact your administrator and ask to be given the permission \"Administer User Accounts\" on the server.");
			System.out.println("WARNING: Defaulting to just using User Full Names which breaks the ability to send email to the individuals who break the build in Hudson!");
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
			// Build the base email name from the User Full Name
			String shortname = server.getUser(userId).getName();
			if (shortname.indexOf(",")>0) {
				// check for a space and assume "lastname, firstname"
				shortname = shortname.charAt((shortname.indexOf(" ")+1))+ shortname.substring(0, shortname.indexOf(","));
			} else {
				// check for a space and assume "firstname lastname"
				if (shortname.indexOf(" ")>0) {
					shortname = shortname.charAt(0) + shortname.substring((shortname.indexOf(" ")+1),shortname.length());

				}  // otherwise, do nothing, just return the name we have.
			}
			return shortname;
		}
		return "unknown";
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
	 * List all files in a given folder, create local subfolders if necessary,
	 * and delete all local files that are not in starteam (deleted or moved) if
	 * clearNoStarteamFiles is set to true.
	 * 
	 * @param folder
	 *            The folder
	 * @param clearNoStarteamFiles
	 *            Whether or not to clear folder from file that are not in
	 *            starteam.
	 * @return a Map of Files, keyed on full pathname.
	 */
	private Map<String, File> listAllFiles(Folder folder, PrintStream logger, boolean clearNoStarteamFiles) {
		//logger.println("*** Looking for versioned files in " + folder.getName());
		Map<String, File> files = new HashMap<String, File>();
		// If working directory doesn't exist, create it
		java.io.File workdir = new java.io.File(folder.getPath());
		if (!workdir.exists()) {
			boolean success = workdir.mkdirs();
			if ( !success ) {
				logger.println("*** Creation of working directory failed : "
						+ workdir.getAbsolutePath());
			} else {
				//logger.println("*** Creation of working directory suceeded : "
				//		+ workdir.getAbsolutePath());
			}
		}
		// list of all starteam files and folder names in the current folder
		List<String> starteamFilesInDirectory = new ArrayList<String>();
		// call for subfolders
		for (Folder f : folder.getSubFolders()) {
			starteamFilesInDirectory.add(f.getName());
			files.putAll(listAllFiles(f, logger, clearNoStarteamFiles));
		}
		// find items in this folder
		for (Item i : folder.getItems(folder.getView().getProject().getServer().getTypeNames().FILE)) {
			File f = (com.starbase.starteam.File) i;
			try {
				// This sometimes throws... deep inside starteam =(
				files.put(f.getParentFolderHierarchy() + f.getName(), f);
				starteamFilesInDirectory.add(f.getName());
			} catch (RuntimeException e) {
				logger.println("Exception in listAllFiles: " + e.getLocalizedMessage());
			}
		}
		// clear local files and directory that are not in starteam.
		if (clearNoStarteamFiles) {
			clearCurrentDirectoryOfNonStarteamFiles(starteamFilesInDirectory,
					workdir);
		}
		folder.discard();
		return files;
	}

	/**
	 * clear directory of non starteam files (and delete non starteam folder
	 * also)
	 *
	 * @param starteamFiles
	 *            list of starteam files / directories names
	 * @param workingDirectory
	 *            local working directory
	 */
	private void clearCurrentDirectoryOfNonStarteamFiles(
			List<String> starteamFiles, java.io.File workingDirectory) {

		//pre-condition : if one of the param is null, or workingDirectory is not a directory, then return
		if ( (starteamFiles == null) || (workingDirectory == null) || !(workingDirectory.isDirectory()) ) {
			return ;
		}

		// find existing files in the current folder
		java.io.File[] filesInFolder = workingDirectory.listFiles();
		// check that all files in the current directory are in starteam
		for (java.io.File currentFile : filesInFolder) {
			// if starteam files doesn't contains one of the given file name,
			// remove it
			if (!starteamFiles.contains(currentFile.getName())) {
				deleteFileOrDirectory(currentFile);
			}

		}
	}

	/**
	 * recursive methods allowing to delete recursively directory or file
	 *
	 * @param aFileOrDirectory
	 *            the file or directory to delete (recursively)
	 */
	private void deleteFileOrDirectory(java.io.File aFileOrDirectory) {
		if (aFileOrDirectory.isDirectory()) {
			// delete all chidren files and directories.
			java.io.File[] childrens = aFileOrDirectory.listFiles();
			for (java.io.File currentFile : childrens) {
				deleteFileOrDirectory(currentFile);
			}
		}
		aFileOrDirectory.delete();
	}

	/**
	 * Recursively look for changes between two file lists.
	 * 
	 * @param thenFiles The list of files representing a past moment in time.
	 * @param nowFiles The list of files representing "now".
	 * @param logger The logger for logging output.
	 * @return a Collection of File objects that have changed since date.
	 */
	private Collection<File> getFileSetDifferences(Map<String, File> thenFiles, Map<String, File> nowFiles, PrintStream logger) {
		List<File> files = new ArrayList<File>();
		// Iterate over all files in the "now" set
		for (Map.Entry<String, File> e : nowFiles.entrySet()) {
			File nowFile = e.getValue();
			// Check if the file existed "then"
			if (thenFiles.containsKey(e.getKey())) {
				File thenFile = thenFiles.get(e.getKey());
				// File exists in the past revision as well
				if (thenFile.getRevisionNumber() < nowFile.getRevisionNumber()) {
					// revision number has increased, add it to changes
					logger.println("[modified] " + nowFile.getFullName());
					files.add(nowFile);
				} else if (!nowFile.getLocalFileExists()) {
					// File not modified but local missing,
					// add to change list
					logger.println("[local missing] " + nowFile.getFullName());
					files.add(nowFile);
				} else {
					// File not modified, discard metadata
					//logger.println("[hit] " + nowFile.getFullName());
					nowFile.discard();
				}
				// discard and remove from "then" files
				thenFile.discard();
				thenFiles.remove(e.getKey());
			} else {
				// File is new
				logger.println("[new] " + nowFile.getFullName());
				files.add(nowFile);
			}
		}
		// Iterate over all remaining files in the "then" set:
		// only files that have been deleted from the repo
		// since "then" remain, therefore we'll report those
		// as changes too.
		for (Map.Entry<String, File> e : thenFiles.entrySet()) {
			logger.println("[deleted] " + e.getValue().getFullName());
			files.add((File) e.getValue());
		}

		return files;
	}

	/**
	 * Find the given folder in the given view.
	 * 
	 * @param view The view to look in.
	 * @param foldername The view-relative path of the folder to look for.
	 * @return The folder or null if a folder by the given name was not found.
	 * @throws StarTeamSCMException
	 */
	private Folder findFolderInView(final View view, final String foldername) throws StarTeamSCMException {
		// Check the root folder of the view
		if (view.getName().equals(foldername)) {
			return view.getRootFolder();
		}

		// Create a File object with the folder name for system-
		// independent matching
		java.io.File thefolder = new java.io.File(foldername);

		// Search for the folder in subfolders
		Folder result = findFolderRecursively(view.getRootFolder(), thefolder);
		if (result == null) {
			throw new StarTeamSCMException("Couldn't find folder " + foldername + " in view " + view.getName());
		}
		return result;
	}

	/**
	 * Do a breadth-first search for a folder with the given name, starting with
	 * children of the provided folder.
	 * 
	 * @param folder the folder whose children to check
	 * @param thefolder the folder to look for
	 * @return found folder
	 */
	private Folder findFolderRecursively(Folder folder, java.io.File thefolder) {
		// Check subfolders, breadth first. checkLater is a collection
		// of folders that didn't match, therefore their children
		// will be checked next.
		Collection<Folder> checkLater = new ArrayList<Folder>();
		for (Folder f : folder.getSubFolders()) {
			// Compare pathnames. The getFolderHierarchy call returns
			// the full folder name (including root folder name which
			// is the same as the view name) terminated by the
			// platform-specific separator.
			if (f.getFolderHierarchy().equals(thefolder.getPath() + java.io.File.separator)) {
				return f;
			} else {
				// add to list of folders whose children will be checked
				checkLater.add(f);
			}
		}
		// recurse unto children
		for (Folder f : checkLater) {
			Folder result = findFolderRecursively(f, thefolder);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * return the list of all files for the current starteam connection.
	 *
	 * @param workspace
	 *            root of local folder
	 * @param logger
	 *            logger used to log events
	 * @param clearNoStarteamFiles
	 *            whether or not deleting local file / directory that are not in
	 *            starteam.
	 * @return a map containing full file name and the corresponding starteam
	 *         object file.
	 */
	public Map<String, File> findAllFiles(java.io.File workspace,
			PrintStream logger, boolean clearNoStarteamFiles) {
		logger.println("*** Get list of all files for " + workspace);

		// set root folder
		rootFolder.setAlternatePathFragment(workspace.getAbsolutePath());

		// Get a list of all files
		Map<String, File> nowFiles = listAllFiles(rootFolder, logger,
				clearNoStarteamFiles);

		logger.println("*** done");
		return nowFiles;
	}

	/**
	 * find all changed files in starteam since a given date. this method create
	 * a list of all current files. and then call the overloaded method with a
	 * list of current files.
	 *
	 * @param workspace
	 *            local root directory.
	 * @param logger
	 *            logger allowing to log event to the console.
	 * @param fromDate
	 *            date of comparison.
	 * @return collection of modified starteam files.
	 */
	public Collection<File> findChangedFiles(java.io.File workspace, PrintStream logger, Date fromDate) {
		boolean clearNoStarteamFiles = false;
		// set root folder
		rootFolder.setAlternatePathFragment(workspace.getAbsolutePath());
		// Get a list of all files
		logger.println("Fetching current files:");
		Map<String, File> nowFiles = listAllFiles(rootFolder, logger,
				clearNoStarteamFiles);
		return findChangedFiles(nowFiles, workspace, logger, fromDate);
	}

	/**
	 * Find all changed files in starteam since a given date.
	 *
	 * @param nowFiles
	 *            list of all current starteam files.
	 * @param workspace
	 *            local root directory.
	 * @param logger
	 *            logger allowing to log event to the console.
	 * @param fromDate
	 *            date of comparison.
	 * @return collection of modified starteam files.
	 */
	public Collection<File> findChangedFiles(Map<String, File> nowFiles, java.io.File workspace, PrintStream logger, Date fromDate) {
		boolean clearNoStarteamFiles = false;
		logger.println("*** Looking for changed files since " + fromDate);
		// set root folder
		rootFolder.setAlternatePathFragment(workspace.getAbsolutePath());
		// Create OLEDate to represent last build time
		OLEDate oleSince = new OLEDate(fromDate);
		// Create a view to represent last build time
		View sinceView = new View(view, ViewConfiguration.createFromTime(oleSince));

		// This list will contain the changed files
		Collection<File> changedFiles = null;

		Folder sinceFolder = null;
		Map<String, File> sinceFiles = null;
		try {
			sinceFolder = findFolderInView(sinceView, folderName);
			sinceFolder.setAlternatePathFragment(workspace.getAbsolutePath());
			logger.println("Fetching files at " + fromDate);
			sinceFiles = listAllFiles(sinceFolder, logger, clearNoStarteamFiles);
			logger.println("done");
			logger.println("Comparing");
			changedFiles = getFileSetDifferences(sinceFiles, nowFiles, logger);
			logger.println("done");
		} catch (StarTeamSCMException e) {
			logger.println("Caught exception: " + e.getLocalizedMessage());
			// Folder not found? That means that every file is a change
			changedFiles = nowFiles.values();
		}

		logger.println("*** done");
		return changedFiles;
	}

	/**
	 * synchronize last build date with starteam server timezone.
	 *
	 * @param aPreviousBuildDate
	 *            the previous Hudson's build date.
	 * @param aCurrentBuildDate
	 *            hudson's current build date
	 * @return corresponding starteam previous build date.
	 */
	public Date calculatePreviousDateWithTimeZoneCheck(Date aPreviousBuildDate,
			Date aCurrentBuildDate) {
		OLEDate starteamServerTime = getServerTime();
		long starteamDateLongValue = starteamServerTime.getLongValue();
		long buildServerDateLongValue = aCurrentBuildDate.getTime();
		// time diff between starteam server and current server
		long timeDiff = starteamDateLongValue - buildServerDateLongValue;
		long lastBuildDateLongValue = aPreviousBuildDate.getTime();
		long lastStarteamDateLongValue = timeDiff + lastBuildDateLongValue;
		Date synchLastBuildDate = new Date(lastStarteamDateLongValue);
		return synchLastBuildDate;
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
}
