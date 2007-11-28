/**
 * 
 */
package hudson.plugins.starteam;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.starbase.starteam.CheckoutOptions;
import com.starbase.starteam.ClientContext;
import com.starbase.starteam.File;
import com.starbase.starteam.Folder;
import com.starbase.starteam.Item;
import com.starbase.starteam.LogonException;
import com.starbase.starteam.Project;
import com.starbase.starteam.Server;
import com.starbase.starteam.ServerConfiguration;
import com.starbase.starteam.ServerInfo;
import com.starbase.starteam.View;
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

	private CheckoutOptions checkoutoptions;

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

		checkoutoptions = new CheckoutOptions(view);
		// Always use host system EOL convention
		checkoutoptions.setEOLConversionEnabled(true);
		ClientContext ctx = new ClientContext();
		if (ctx.getEOL().equals(com.starbase.starteam.ClientContext.EOL_CR)) {
			checkoutoptions.setEOLChars(CheckoutOptions.EOL_CR);
		} else if (ctx.getEOL().equals(
				com.starbase.starteam.ClientContext.EOL_CRLF)) {
			checkoutoptions.setEOLChars(CheckoutOptions.EOL_CRLF);
		} else if (ctx.getEOL().equals(
				com.starbase.starteam.ClientContext.EOL_LF)) {
			checkoutoptions.setEOLChars(CheckoutOptions.EOL_LF);
		}
		// Always use timestamp from repository for files
		checkoutoptions.setTimeStampNow(false);
		// Use forced checkout so that modified files get clobbered
		checkoutoptions.setForceCheckout(true);
	}

	/**
	 * @param changed_files
	 * @throws IOException
	 *             if checkout fails.
	 */
	public void checkOut(Collection<File> changed_files) throws IOException {
		for (File f : changed_files) {
			f.checkout(checkoutoptions);
		}
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
	 * Recursively look for changes within the given folder made after the given
	 * date.
	 * 
	 * @param logger
	 * 
	 * @param folder
	 *            The folder to look in.
	 * @param date
	 *            The date after which changes are taken into account.
	 * @return a Collection of File objects that have changed since date.
	 */
	private Collection<com.starbase.starteam.File> findChangedFiles(
			PrintStream logger, final Folder folder, final Date date) {
		List<com.starbase.starteam.File> files = new ArrayList<com.starbase.starteam.File>();
		// update folder
		folder.refresh();
		// If working directory doesn't exist, create it
		java.io.File workdir = new java.io.File(folder.getPath());
		if (!workdir.exists()) {
			logger.println("Creating working directory: "
					+ workdir.getAbsolutePath());
			workdir.mkdirs();
		}
		// call for subfolders
		for (Folder f : folder.getSubFolders()) {
			files.addAll(findChangedFiles(logger, f, date));
		}
		// find items in this folder
		for (Item i : folder.getItems(folder.getView().getProject().getServer()
				.getTypeNames().FILE)) {
			com.starbase.starteam.File f = (com.starbase.starteam.File) i;
			// Check if local exists
			if (!f.getLocalFileExists()) {
				logger.println("[new] " + f.getFullName());
				files.add(f);
			}
			// Check modification date
			else if (i.getModifiedTime().getDoubleValue() > new OLEDate(date)
					.getDoubleValue()) {
				// There's a version that's newer than our given date, add
				// to the collection
				logger.println("[modified] " + f.getFullName());
				files.add(f);
			}
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

	/**
	 * @param workspace
	 * @param logger
	 * @param sinceDate
	 * @return
	 */
	public Collection<File> findChangedFiles(java.io.File workspace,
			PrintStream logger, Date sinceDate) {
		// set root folder
		rootFolder.setAlternatePathFragment(workspace.getAbsolutePath());
		return findChangedFiles(logger, rootFolder, sinceDate);
	}

	/**
	 * Close the connection.
	 */
	public void close() {
		server.disconnect();
	}

	@Override
	protected void finalize() throws Throwable {
		close();
	}
}
