package hudson.plugins.starteam;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;


/**
 * StarTeam SCM plugin for Hudson.
 * 
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 */
public class StarTeamSCM extends SCM {

	/**
	 * Singleton descriptor.
	 */
	public static final StarTeamSCMDescriptorImpl DESCRIPTOR = new StarTeamSCMDescriptorImpl();

	private final String user;
	private final String passwd;
	private final String projectname;
	private final String viewname;
	private final String foldername;
	private final String hostname;
	private final int port;

	/**
	 * The constructor.
	 * 
	 * @stapler-constructor
	 */
	public StarTeamSCM(String hostname, int port, String projectname,
			String viewname, String foldername, String username, String password) {
		this.hostname = hostname;
		this.port = port;
		this.projectname = projectname;
		this.viewname = viewname;
		this.foldername = foldername;
		this.user = username;
		this.passwd = password;
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
		// Create an actor to do the checkout, possibly on a remote machine
		StarTeamCheckoutActor co_actor = new StarTeamCheckoutActor(hostname,
				port, user, passwd, projectname, viewname, foldername, build
						.getTimestamp().getTime(), changelogFile, listener);
		if (workspace.act(co_actor)) {
			// TODO: truly create changelog
			status = createEmptyChangeLog(changelogFile, listener, "log");
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
		// Create an actor to do the polling, possibly on a remote machine
		StarTeamPollingActor p_actor = new StarTeamPollingActor(hostname, port,
				user, passwd, projectname, viewname, foldername,
				proj.getLastBuild().getTimestamp().getTime(),
				listener);
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

		protected StarTeamSCMDescriptorImpl() {
			super(StarTeamSCM.class, null);
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
		public boolean configure(StaplerRequest req) throws FormException {
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
}
