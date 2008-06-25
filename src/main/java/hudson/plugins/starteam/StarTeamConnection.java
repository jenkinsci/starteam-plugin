/**
 * 
 */
package hudson.plugins.starteam;

import java.io.IOException;
import java.io.PrintStream;
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
import com.starbase.starteam.ServerInfo;
import com.starbase.starteam.Status;
import com.starbase.starteam.View;
import com.starbase.starteam.ViewConfiguration;
import com.starbase.util.OLEDate;

/**
 * StarTeamActor is a class that implements connecting to a StarTeam repository,
 * to a given project, view and folder.
 * 
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * 
 */
public class StarTeamConnection {

	private final ServerInfo serverinfo;

	private final String username;

	private final String password;

	private final String projectname;

	private final String viewname;

	private final String foldername;

	private Server server = null;

	private View view = null;

	private Folder rootFolder = null;

	private Project project = null;

	/**
	 * @param hostname
	 * @param port
	 * @param user
	 * @param passwd
	 * @param projectname
	 * @param viewname
	 * @param foldername
	 */
	public StarTeamConnection(String hostname, int port, String user,
			String passwd, String projectname, String viewname,
			String foldername) {
		// Create the server information object
		serverinfo = new ServerInfo();
		serverinfo
				.setConnectionType(ServerConfiguration.PROTOCOL_TCP_IP_SOCKETS);

		// Do a trick in case there are several hosts with the same
		// description: if an exception is thrown, start adding a counter
		// to the end of the string.
		String desc_base = "StarTeam connection to " + hostname;
		String desc = desc_base;
		do {
			int ctr = 1;
			try {
				serverinfo.setDescription(desc);
				break;
			} catch (com.starbase.starteam.DuplicateServerListEntryException e) {
				desc = desc_base + " (" + Integer.toString(ctr++) + ")";
			}
		} while (true);

		serverinfo.setHost(hostname);
		serverinfo.setPort(port);

		this.username = user;
		this.password = passwd;
		this.projectname = projectname;
		this.viewname = viewname;
		this.foldername = foldername;
	}

	/**
	 * Initialize the connection. This means logging on to the server and
	 * finding the project, view and folder we want.
	 * 
	 * @throws StarTeamSCMException
	 *             if logging on fails.
	 */
	public void initialize() throws StarTeamSCMException {
		server = new Server(serverinfo);
		server.connect();
		try {
			server.logOn(username, password);
		} catch (LogonException e) {
			throw new StarTeamSCMException("Could not log on: "
					+ e.getErrorMessage());
		}

		project = findProjectOnServer(server, projectname);
		view = findViewInProject(project, viewname);
		rootFolder = findFolderInView(view, foldername);

		// Cache some folder data
		final PropertyNames pnames = rootFolder.getPropertyNames();
		final String[] propsToCache = new String[] {
				pnames.FILE_LOCAL_FILE_EXISTS, pnames.FILE_LOCAL_TIMESTAMP,
				pnames.FILE_NAME, pnames.FILE_FILE_TIME_AT_CHECKIN,
				pnames.MODIFIED_TIME, pnames.MODIFIED_USER_ID,
				pnames.FILE_STATUS };
		rootFolder.populateNow(server.getTypeNames().FILE, propsToCache, -1);
	}

	/**
	 * @param filesToCheckOut
	 * @throws IOException
	 *             if checkout fails.
	 */
	public void checkOut(Collection<File> filesToCheckOut, PrintStream logger)
			throws IOException {
		logger.println("*** Performing checkout");
		for (File f : filesToCheckOut) {
			switch (f.getStatus()) {
			case Status.MERGE:
			case Status.MODIFIED:
			case Status.UNKNOWN:
				// clobber these
				new java.io.File(f.getFullName()).delete();
				break;
			case Status.MISSING:
			case Status.OUTOFDATE:
				// just go on an check out
				break;
			default:
				// By default do nothing, go to next iteration
				continue;
			}
			logger.print("[co] " + f.getFullName() + "... ");
			f.checkout(Item.LockType.UNLOCKED, // check out as unlocked
					false, // use timestamp from repo
					true, // convert EOL to native format
					true); // update status
			f.discard();
			logger.println("ok");
		}
		logger.println("*** done");
	}

	/**
	 * @param server
	 * @param projectname
	 * @return
	 * @throws StarTeamSCMException
	 */
	static Project findProjectOnServer(final Server server,
			final String projectname) throws StarTeamSCMException {
		for (Project project : server.getProjects()) {
			if (project.getName().equals(projectname)) {
				return project;
			}
		}
		throw new StarTeamSCMException("Couldn't find project " + projectname
				+ " on server " + server.getAddress());
	}

	/**
	 * @param project
	 * @param viewname
	 * @return
	 * @throws StarTeamSCMException
	 */
	static View findViewInProject(final Project project, final String viewname)
			throws StarTeamSCMException {
		for (View view : project.getAccessibleViews()) {
			if (view.getName().equals(viewname)) {
				return view;
			}
		}
		throw new StarTeamSCMException("Couldn't find view " + viewname
				+ " in project " + project.getName());
	}

	/**
	 * List all files in a given folder.
	 * 
	 * @param folder
	 *            The folder
	 * @return a Map of Files, keyed on full pathname.
	 */
	private Map<String, File> listAllFiles(Folder folder, PrintStream logger) {
		logger
				.println("*** Looking for versioned files in "
						+ folder.getName());
		Map<String, File> files = new HashMap<String, File>();
		// If working directory doesn't exist, create it
		java.io.File workdir = new java.io.File(folder.getPath());
		if (!workdir.exists()) {
			logger.println("*** Creating working directory: "
					+ workdir.getAbsolutePath());
			workdir.mkdirs();
		}
		// call for subfolders
		for (Folder f : folder.getSubFolders()) {
			files.putAll(listAllFiles(f, logger));
		}
		// find items in this folder
		for (Item i : folder.getItems(folder.getView().getProject().getServer()
				.getTypeNames().FILE)) {
			File f = (com.starbase.starteam.File) i;
			try {
				// This sometimes throws... deep inside starteam =(
				files.put(f.getParentFolderHierarchy() + f.getName(), f);
			} catch (RuntimeException e) {
				logger.println("Exception in listAllFiles: "
						+ e.getLocalizedMessage());
			}
		}
		folder.discard();
		return files;
	}

	/**
	 * Recursively look for changes between two file lists.
	 * 
	 * @param thenFiles
	 *            The list of files representing a past moment in time.
	 * @param nowFiles
	 *            The list of files representing "now".
	 * @param logger
	 *            The logger for logging output.
	 * @return a Collection of File objects that have changed since date.
	 */
	private Collection<File> getFileSetDifferences(Map<String, File> thenFiles,
			Map<String, File> nowFiles, PrintStream logger) {
		List<File> files = new ArrayList<File>();
		// Iterate over all files in the "now" set
		for (Map.Entry e : nowFiles.entrySet()) {
			File nowFile = (File) e.getValue();
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
					logger.println("[hit] " + nowFile.getFullName());
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
		for (Map.Entry e : thenFiles.entrySet()) {
			logger.println("[deleted] " + ((File) e.getValue()).getFullName());
			files.add((File) e.getValue());
		}

		return files;
	}

	/**
	 * Find the given folder in the given view.
	 * 
	 * @param view
	 *            The view to look in.
	 * @param foldername
	 *            The view-relative path of the folder to look for.
	 * @return The folder or null if a folder by the given name was not found.
	 * @throws StarTeamSCMException
	 */
	private Folder findFolderInView(final View view, final String foldername)
			throws StarTeamSCMException {
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
			throw new StarTeamSCMException("Couldn't find folder " + foldername
					+ " in view " + view.getName());
		}
		return result;
	}

	/**
	 * Do a breadth-first search for a folder with the given name, starting with
	 * children of the provided folder.
	 * 
	 * @param folder
	 *            the folder whose children to check
	 * @param thefolder
	 *            the folder to look for
	 * @return
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
			if (f.getFolderHierarchy().equals(
					thefolder.getPath() + java.io.File.separator)) {
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

	public Collection<File> findAllFiles(java.io.File workspace,
			PrintStream logger) {
		logger.println("*** Get list of all files for " + workspace);

		// set root folder
		rootFolder.setAlternatePathFragment(workspace.getAbsolutePath());

		// Get a list of all files
		Map<String, File> nowFiles = listAllFiles(rootFolder, logger);

		logger.println("*** done");
		return nowFiles.values();
	}

	/**
	 * @param workspace
	 * @param logger
	 * @param fromDate
	 * @return
	 */
	public Collection<File> findChangedFiles(java.io.File workspace,
			PrintStream logger, Date fromDate) {
		logger.println("*** Looking for changed files since " + fromDate);
		// set root folder
		rootFolder.setAlternatePathFragment(workspace.getAbsolutePath());
		// Create OLEDate to represent last build time
		OLEDate oleSince = new OLEDate(fromDate);
		// Create a view to represent last build time
		View sinceView = new View(view, ViewConfiguration
				.createFromTime(oleSince));

		// This list will contain the changed files
		Collection<File> changedFiles = null;

		// Get a list of all files
		logger.println("Fetching current files:");
		Map<String, File> nowFiles = listAllFiles(rootFolder, logger);
		logger.println("done");

		Folder sinceFolder = null;
		Map<String, File> sinceFiles = null;
		try {
			sinceFolder = findFolderInView(sinceView, foldername);
			sinceFolder.setAlternatePathFragment(workspace.getAbsolutePath());
			logger.println("Fetching files at " + fromDate);
			sinceFiles = listAllFiles(sinceFolder, logger);
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
	 * Close the connection.
	 */
	public void close() {
		rootFolder.discardItems(rootFolder.getTypeNames().FILE, -1);
		view.discard();
		project.discard();
		server.disconnect();
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
