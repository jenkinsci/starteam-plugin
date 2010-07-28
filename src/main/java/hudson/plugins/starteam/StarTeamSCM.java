package hudson.plugins.starteam;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;


/**
 * StarTeam SCM plugin for Hudson.
 * Add support for change log and synchronization between starteam repository and hudson's workspace.
 * Add support for change log creation.
 * Refactoring to use Extension annotation and to remove use of deprecated API.
 * 
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 */
public class StarTeamSCM extends SCM {

	/**
	 * Singleton descriptor.
	 */
	@Extension
	public static final StarTeamSCMDescriptorImpl DESCRIPTOR = new StarTeamSCMDescriptorImpl();

	private final String user;
	private final String passwd;
	private final String projectname;
	private final String viewname;
	private final String foldername;
	private final String hostname;
	private final int port;
	private final String labelname;
	private final boolean promotionstate;

	private final StarTeamViewSelector config;

	/**
	 * 
	 * default stapler constructor.
	 * 
	 * @param hostname
	 *            starteam host name.
	 * @param port
	 *            starteam port name
	 * @param projectname
	 *            name of the project
	 * @param viewname
	 *            name of the view
	 * @param foldername
	 *            parent folder name.
	 * @param username
	 *            the user name required to connect to starteam's server
	 * @param password
	 *            password required to connect to starteam's server
	 * @param labelname
	 *            label name used for polling view contents
	 * @param promotionstate 
	 *            indication if label name is actual label name or a promotion state name
	 *
	 */
	@DataBoundConstructor
	public StarTeamSCM(String hostname, int port, String projectname,
			String viewname, String foldername, String username, String password, String labelname, boolean promotionstate) {
		this.hostname = hostname;
		this.port = port;
		this.projectname = projectname;
		this.viewname = viewname;
		this.foldername = foldername;
		this.user = username;
		this.passwd = password;
		this.labelname = labelname;
		this.promotionstate = promotionstate;
		StarTeamViewSelector result = null;
		if (this.labelname != null && !this.labelname.isEmpty())
		{
			try {
				if (this.promotionstate)
				{
					result = new StarTeamViewSelector(this.labelname,"Promotion");
				} else {
					result = new StarTeamViewSelector(this.labelname,"Label");
				}
			} catch (ParseException e) {
				e.printStackTrace();
				result = null;
			}
		}
		this.config = result;
	}

	/*
	 * @see hudson.scm.SCM#checkout(hudson.model.AbstractBuild, hudson.Launcher,
	 *      hudson.FilePath, hudson.model.BuildListener, java.io.File)
	 */
	@Override
	public boolean checkout(AbstractBuild build, Launcher launcher,
			FilePath workspace, BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {
		boolean status = false;

		Date previousBuildDate = null;
		if ( build.getPreviousBuild() != null)	{
		    previousBuildDate = build.getPreviousBuild().getTimestamp().getTime();
		}
		Date currentBuildDate = build.getTimestamp().getTime();
		
		//create a FilePath to be able to create changelog file on a remote computer.
		FilePath changeLogFilePath = new FilePath( changelogFile ) ;
		
		// Create an actor to do the checkout, possibly on a remote machine
		StarTeamCheckoutActor co_actor = new StarTeamCheckoutActor(hostname,
				port, user, passwd, projectname, viewname, foldername, config, previousBuildDate, currentBuildDate, changeLogFilePath, listener);
		if (workspace.act(co_actor)) {
			// change log is written during checkout (only one pass for
			// comparison)
			status = true;
		} else {
			listener.getLogger().println("StarTeam checkout failed");
			status = false;
		}
		return status;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#createChangeLogParser()
	 */
	@Override
	public ChangeLogParser createChangeLogParser() {
		return new StarTeamChangeLogParser();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#getDescriptor()
	 */
	@Override
	public StarTeamSCMDescriptorImpl getDescriptor() {
		return DESCRIPTOR;
//		return (StarTeamSCMDescriptorImpl)super.getDescriptor();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#pollChanges(hudson.model.AbstractProject,
	 *      hudson.Launcher, hudson.FilePath, hudson.model.TaskListener)
	 */
	@Override
	public boolean pollChanges(final AbstractProject proj,
			final Launcher launcher, final FilePath workspace,
			final TaskListener listener) throws IOException,
			InterruptedException {
		boolean status = false;
		Run run = proj.getLastBuild();
		Date sinceDate = null;
		if ( run != null) {
		    sinceDate= run.getTimestamp().getTime();
		}
		Date currentServerDate = new Date();
		// Create an actor to do the polling, possibly on a remote machine
		StarTeamPollingActor p_actor = new StarTeamPollingActor(hostname, port,
				user, passwd, projectname, viewname, foldername,
				config, sinceDate,
				currentServerDate, listener);
		if (workspace.act(p_actor)) {
			status = true;
		} else {
			listener.getLogger().println("StarTeam polling failed");
		}
		return status;
	}

	/**
	 * Descriptor class for the SCM class.
	 * 
	 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
	 * 
	 */
	public static final class StarTeamSCMDescriptorImpl extends SCMDescriptor<StarTeamSCM> {

		private final Collection<StarTeamSCM> scms = new ArrayList<StarTeamSCM>();

		public StarTeamSCMDescriptorImpl() {
			super(StarTeamSCM.class, null);
			load() ;
		}

		@Override
		public String getDisplayName() {
			return "StarTeam";
		}

		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)
				throws hudson.model.Descriptor.FormException {
			// Go ahead and create the scm.. the bindParameters() method
			// takes the request and nabs all "starteam." -prefixed
			// parameters from it, then sets the scm instance's fields
			// according to those parameters.
			StarTeamSCM scm = null;
			try {
				scm = req.bindParameters(StarTeamSCM.class, "starteam.");
				scms.add(scm);
			} catch (RuntimeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// We don't have working repo browsers yet...
			// scm.repositoryBrowser = RepositoryBrowsers.createInstance(
			// StarTeamRepositoryBrowser.class, req, "starteam.browser");
			return scm;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest)
		 */
		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// This is used for the global configuration
			return true;
		}

	}

	/**
	 * Get the hostname this SCM is using.
	 * 
	 * @return The hostname.
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * Get the port number this SCM is using.
	 * 
	 * @return The port.
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Get the project name this SCM is connected to.
	 * 
	 * @return The project's name.
	 */
	public String getProjectname() {
		return projectname;
	}

	/**
	 * Get the view name in the project this SCM uses.
	 * 
	 * @return The name of the view.
	 */
	public String getViewname() {
		return viewname;
	}

	/**
	 * Get the root folder name of our monitored workspace.
	 * 
	 * @return The name of the folder.
	 */
	public String getFoldername() {
		return foldername;
	}

	/**
	 * Get the username used to connect to starteam.
	 * 
	 * @return The username.
	 */
	public String getUsername() {
		return user;
	}

	/**
	 * Get the password used to connect to starteam.
	 * 
	 * @return The password.
	 */
	public String getPassword() {
		return passwd;
	}

	public String getLabelname() {
		return labelname;
	}

	public boolean isPromotionstate() {
		return promotionstate;
	}
}
