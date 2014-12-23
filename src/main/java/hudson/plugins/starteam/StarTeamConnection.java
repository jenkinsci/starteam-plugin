/**
 *
 */
package hudson.plugins.starteam;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import static hudson.Util.fixEmptyAndTrim;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.starteam.CommandProcessor;
import com.starteam.File;
import com.starteam.Folder;
import com.starteam.Label;
import com.starteam.Project;
import com.starteam.PromotionState;
import com.starteam.Server;
import com.starteam.ServerInfo;
import com.starteam.View;
import com.starteam.ViewConfiguration;
import com.starteam.exceptions.DuplicateServerListEntryException;
import com.starteam.exceptions.LogonException;


public class StarTeamConnection implements Serializable {
	private static final long serialVersionUID = 1L;
	private final static Pattern labelPattern = Pattern.compile("%\\{(.*?):BUILD_NUMBER\\}");

	public static final String FILE_POINT_FILENAME = "starteam-filepoints.csv";

	private final String hostName;
	private final int port;
	private final String userName;
	private final String password;
	private final String projectName;
	private final String viewName;
	private final String folderName;
	private String labelName;
	private boolean promotionstate;


	private transient Server server;
	private transient View view;
	private transient Folder rootFolder;
	private transient Project project;

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
	public StarTeamConnection(String hostName, int port, String userName, String password, String projectName, String viewName, String folderName, String labelName, boolean promotionstate) {
		checkParameters(hostName, port, userName, password, projectName, viewName, folderName);
		this.hostName = hostName;
		this.port = port;
		this.userName = userName;
		this.password = password;
		this.projectName = projectName;
		this.viewName = viewName;
		this.folderName = folderName;
		this.labelName = labelName;
		this.promotionstate = promotionstate;
	}

	private static void checkParameters(String hostName, int port, String userName, String password, String projectName, String viewName,
			String folderName) {
		if (fixEmptyAndTrim(hostName)==null) throw new NullPointerException("hostName cannot be null");
		if (fixEmptyAndTrim(userName)==null) throw new NullPointerException("user cannot be null");
		if (fixEmptyAndTrim(password)==null) throw new NullPointerException("passwd cannot be null");
		if (fixEmptyAndTrim(projectName)==null)	throw new NullPointerException("projectName cannot be null");
		if ((port < 1) || (port > 65535))
			throw new IllegalArgumentException("Invalid port: " + port);
	}

	private ServerInfo createServerInfo() {
		ServerInfo serverInfo = new ServerInfo();
		serverInfo.setHost(this.hostName);
		serverInfo.setPort(this.port);
		populateDescription(serverInfo);
		return serverInfo;
	}

	/**
	 * Initialize the connection. This means logging on to the server and
	 * finding the project, view and folder we want.
	 * 
	 * @throws StarTeamSCMException if logging on fails or if the project, view,
	 * promotion state, label, or folder cannot be found on the server
	 */
	public void initialize() throws StarTeamSCMException{
		initialize(-1);
	}
	
	/**
	 * Initialize the connection. This means logging on to the server and
	 * finding the project, view and folder we want.
	 * 
	 * @param buildNumber the number of the current build, only used by the 
	 * secret create label functionality.  -1 indicates that this is a polling 
	 * task and labels should not be created
	 * 
	 * @throws StarTeamSCMException if logging on fails or if the project, view,
	 * promotion state, label, or folder cannot be found on the server
	 */
	public void initialize(int buildNumber) throws StarTeamSCMException {
		/* 
		   Identify this as the StarTeam Hudson Plugin 
		   so that it can support the new AppControl capability in StarTeam 2009
		   which allows a StarTeam administrator to block or allow Unknown or specific 
		   client/SDK applications from accessing the repository; without this, the plugin
		   will be seen as an Unknown Client, and may be blocked by StarTeam repositories
		   that take advantage of this feature.  This must be called before a connection
		   to the server is established.
		 */ 
		//com.starbase.starteam.ClientApplication.setName("StarTeam Plugin for Jenkins");

		server = new Server(createServerInfo());
		server.connect();
		logOnToServer();

		project = server.findProject(projectName);
		validateProject();

		view = "".equals(viewName) ? project.getDefaultView() : project.findView(viewName);
		validateView();

		if (isPromotionState()){
			PromotionState promState = view.getPromotionModel().findPromotionState(labelName);
			validatePromotionState(promState);
			view = new View(view, ViewConfiguration.createFrom(promState));
		} else if (isLabel() && !isPollingBuild(buildNumber)) {
			Label label = null;

			if(isCreateLabelString(labelName)){
				label = createLabel(buildNumber);
			} else {
				label = view.findLabel(labelName);
			}
			validateLabel(label);
			view = new View(view, ViewConfiguration.createFrom(label));

		} else {
			view = new View(view, ViewConfiguration.createTip());
		}

		rootFolder = StarTeamFunctions.findFolderInView(view, folderName);
		validateFolder();
	}
	
	private void logOnToServer() throws StarTeamSCMException {
		try {
			server.logOn(userName, password);
		} catch (LogonException e) {
			throw new StarTeamSCMException("Could not log on: " + e.getErrorMessage());
		}
	}
	
	private void validateProject() throws StarTeamSCMException {
		if (project == null){
			throw new StarTeamSCMException("Could not find project " + projectName + " on server " + server.getAddress());
		}
	}
	
	private void validateView() throws StarTeamSCMException {
		if (view == null){
			throw new StarTeamSCMException("Could not find view " + viewName + " in project " + project.getName());
		}
	}
	
	private boolean isPromotionState() {
		return isLabel() && promotionstate;
	}
	
	private boolean isLabel() {
		return labelName != null && !labelName.isEmpty();
	}
	
	private void validatePromotionState(PromotionState promState)
			throws StarTeamSCMException {
		if (promState == null){
			throw new StarTeamSCMException("Could not find promotion state " + labelName + " in view " + view.getName());
		}
	}
	
	private boolean isPollingBuild(int buildNumber) {
		return buildNumber == -1;
	}
	
	/**
	 * Checks whether the label entered matches the secret regex for creating labels
	 * 
	 * @param labelName the label name entered in the advanced starteam config option
	 * @return true if the label entered matches the regex "%\\{(.*?):BUILD_NUMBER\\}"
	 * false otherwise
	 */
	private boolean isCreateLabelString(String labelName){
		Matcher m = labelPattern.matcher(labelName);
		return m.find();
	}
	
	private Label createLabel(int buildNumber) throws StarTeamSCMException {
		String expandedLabelName = expandLabelPattern(labelName, buildNumber);
		final String labelDesc = String.format("Jenkins build %d", buildNumber);
		final boolean buildLabel = true;
		final boolean frozen = true;
		Label label = view.createViewLabel(expandedLabelName, labelDesc, com.starteam.util.DateTime.CURRENT_SERVER_TIME, buildLabel, frozen);
		labelName = label.getName();
		return label;
	}
	
	/**
	 * Expands the regex entered in the label name and includes the build number
	 * with the appropriate format
	 * 
	 * @param labelName the label name entered in the advanced starteam config option
	 * @param buildNumber the number of the current build
	 * @return the labep pattern expanded to include the build number
	 * examples using build number 123:
	 * Label pattern							Expanded string
	 * ""										""
	 * "L1"										"L1",
	 * "L1%"									"L1%"
	 * "%{d:BUILD_NUMBER}"						"123"
	 * "%{05d:BUILD_NUMBER}"					"00123"
	 * "%{x:BUILD_NUMBER}"						"7b"
	 * "%{X:BUILD_NUMBER}"						"7B"
	 * "%{04x:BUILD_NUMBER}"					"007b"
	 * "%{#x:BUILD_NUMBER}"						"0x7b"
	 * "prefix.%{d:BUILD_NUMBER}.suffix"		"prefix.123.suffix"}
	 * "%{d:BUILD_NUMBER}-%{x:BUILD_NUMBER}"	"123-7b"},
	 */
	private String expandLabelPattern(final String labelName, final int buildNumber) {
		Matcher m = labelPattern.matcher(labelName);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String fmt = "%" + m.group(1);
			m.appendReplacement(sb, String.format(fmt, buildNumber));
		}
		m.appendTail(sb);
		return sb.toString();
	}
	
	private void validateLabel(Label label) throws StarTeamSCMException {
		if (label == null){
			throw new StarTeamSCMException("Could not find label " + labelName + " in view " + view.getName());
		}
	}
	
	private void validateFolder() throws StarTeamSCMException {
		if (rootFolder == null) {
			throw new StarTeamSCMException("Could not find folder " + folderName + " in view " + view.getName());
		}
	}
	
	/**
	 * Close the connection.
	 */
	public void close() {
		if (server.isConnected()) {
			if (rootFolder != null)	{
				rootFolder.discardItems(server.getTypes().FILE, -1);
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

		//all starteam files
		final Collection<com.starteam.File> starteamFiles = StarTeamFunctions.listAllFiles(rootFolder, workspace);
		//all java.ioFiles and corresponding starteam files
		final Map<java.io.File, com.starteam.File> starteamFileMap = StarTeamFunctions.convertToFileMap(starteamFiles);
		// get file path + revisions for starteam files
		final Collection<StarTeamFilePoint> starteamFilePoint = StarTeamFilePointFunctions.convertFilePointCollection(starteamFiles);


		final StarTeamChangeSet changeSet = new StarTeamChangeSet();
		// add all file path + revisions for starteam files to change set
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
			for (File file: starteamFiles) {
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
		String username = f.getModifiedBy().getName();
		String msg = f.getComment();
		Date date = f.getModifiedTime().toJavaDate();
		String fileName = f.getName();		
		return new StarTeamChangeLogEntry(fileName,revisionNumber,date,username,msg, change);
	}

	public StarTeamChangeSet computeDifference(final Collection<StarTeamFilePoint> currentFilePoint, final Collection<StarTeamFilePoint> historicFilePoint, StarTeamChangeSet changeSet, Map<java.io.File, com.starteam.File> starteamFileMap) {
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
			com.starteam.File stf = starteamFileMap.get(f);
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
			com.starteam.File stf = starteamFileMap.get(f);
			changeSet.addChange(FileToStarTeamChangeLogEntry(stf,"added"));
		}

		return changeSet;
	}

	public Folder getRootFolder() {
		return rootFolder;
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
		} catch (DuplicateServerListEntryException e) {
			return false;
		}
	}

	public void checkout(java.io.File workspace) {
		CommandProcessor cmdProc = new CommandProcessor(view);
		String setProjectAndViewCommand = "set project=\"" + view.getProject().getName() + "\" "
										+ "viewHierarchy=\"" + viewName.replace('/', ':') + "\" "
										+ "folderHierarchy=\"" + folderName + "\"";
		cmdProc.execute(setProjectAndViewCommand);
		
		String command = "co -o -is -cwf -f NCD";
		command = command + " -fp \"" + workspace.getAbsolutePath() + "\"";
		if(labelName != null && promotionstate){
			command = command + " -cfgp \"" + labelName + "\"";
		} else if (isLabel()){
			command = command + " -cfgl \"" + labelName + "\"";
		}

		cmdProc.execute(command);
	}
}
