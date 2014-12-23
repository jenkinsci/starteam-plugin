package hudson.plugins.starteam;

import static hudson.Util.fixEmptyAndTrim;
import static java.util.logging.Level.SEVERE;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;




import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;


public class StarTeamSCM extends SCM {

	private String hostName;
	private int port;
	private String projectName;
	private String viewName;
	private String folderName;
	private String labelName;
	private String credentialsId;
	private boolean promotionstate;

	private String userName;
	private String password;
	private static final Logger log = Logger.getLogger(StarTeamSCM.class.getName());
	
	/* backwards compatibility with configs from plugin 0.6.x */
	@Deprecated private transient String hostname;
	@Deprecated private transient String user;
	@Deprecated private transient String  passwd;
	@Deprecated private transient String projectname;
	@Deprecated private transient String viewname;
	@Deprecated private transient String foldername;
	@Deprecated private transient String labelname; 
	
	   public Object readResolve() {
		   if(hostname!=null) { hostName=hostname; hostname=null; }
		   if(projectname!=null) { projectName=projectname; projectname=null; }
		   if(viewname!=null) { viewName=viewname; viewname=null; }
		   if(foldername!=null) { folderName= foldername.equals(projectName) ? "" : foldername; foldername=null; }
		   if(labelname!=null) { labelName=labelname; labelname=null; }
		   user=null;
		   passwd=null;
	       return this;
	    }
	   /* end of backwards compatibility block */

	
	@DataBoundConstructor
	public StarTeamSCM(String hostName, int port, String projectName, String viewName, String folderName, String labelName, boolean promotionstate, String credentialsId) {
		this.hostName = hostName;
		this.port = port;
		this.projectName = projectName;
		this.viewName = viewName;
		this.folderName = folderName;
		this.labelName = labelName;
		this.promotionstate = promotionstate;
		this.credentialsId = credentialsId;
	}

	@Extension
	public static final StarTeamSCMDescriptorImpl DESCRIPTOR = new StarTeamSCMDescriptorImpl();

	public static class StarTeamSCMDescriptorImpl extends SCMDescriptor<StarTeamSCM> {
		
		private final Collection<StarTeamSCM> scms = new ArrayList<StarTeamSCM>();
		 private String starTeamJarPath;

		public StarTeamSCMDescriptorImpl() {
			super(StarTeamSCM.class, null);
			load();
		}

		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)	throws hudson.model.Descriptor.FormException {
			StarTeamSCM scm = null;
			try {
				scm = req.bindJSON(StarTeamSCM.class, formData);
				scms.add(scm);
			} catch (RuntimeException e) {
				log.log(SEVERE, e.getMessage(), e);
			}
			return scm;

		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			starTeamJarPath = fixEmptyAndTrim(req.getParameter("starteam.starTeamJarPath"));
			save();
			return true;
		}

		@Override
		public String getDisplayName() {
			return "StarTeam";
		}

		@Restricted(NoExternalUse.class)
		void setStarTeamJarPath(String path) {
			starTeamJarPath = path;
		}

		public String getStarTeamJarPath() {
			return starTeamJarPath;
		}
		
		public ListBoxModel doFillCredentialsIdItems() {
			return new StandardUsernameListBoxModel()
				.withEmptySelection()
				.withAll(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, new ArrayList<DomainRequirement>())
            );
		}
		
		public FormValidation doCheckStarTeamJarPath(@QueryParameter String value) {
			if(fixEmptyAndTrim(value)==null) {
				String sdkEnv = StarTeamSdkLoader.getSdkEnvLocation();
				if(fixEmptyAndTrim(sdkEnv)==null) return FormValidation.warning("No SDK_LOCATION in env, please specify full path to starteam140.jar");
				String starTeamJarPath = StarTeamSdkLoader.getSdkJarFromFolder(sdkEnv);
				if(starTeamJarPath==null) FormValidation.warning("StarTeam SDK not found in SDK_LOCATION, please specify full path to starteam140.jar");
				value = starTeamJarPath;
	}

			try {
				List<String> locations = StarTeamSdkLoader.checkSdkIsOnPath(value);
				if(locations.size()==1)	{
					return FormValidation.okWithMarkup("StarTeam SDK successfully loaded  (<a href='#' class='showDetails'>details</a>)"
							+"<pre style='display:none'>"+locations.get(0)+"</pre>");
				}
				else {
					String locationList = "using SDK from :"+locations.get(0);
					locationList+= "<br>Other locations found:<br>";
					for(int i=1;i<locations.size();i++) locationList+=locations.get(i)+"<br>";
					return FormValidation.warningWithMarkup("StarTeam SDK found in multiple locations (<a href='#' class='showDetails'>details</a>)"
							+"<pre style='display:none'>"+locationList+"</pre>");
				}
				
			} catch (MalformedURLException e) {
				return FormValidation.warning(e,"Could not locate StarTeam SDK, please specify valid path to starteam140.jar");
			} catch (ClassNotFoundException e) {
				return FormValidation.warning(e,"Could not locate StarTeam SDK, please specify full path to starteam140.jar");
			} catch (FileNotFoundException e) {
				return FormValidation.warning(e,"File not found, please specify full path to starteam140.jar");
			} catch (IOException e) {
				return FormValidation.warning(e,"Error, please specify full path to starteam140.jar");
			}
		}
		
		public FormValidation doCheckHostName(@QueryParameter String value) { 
			if(fixEmptyAndTrim(value)==null) return FormValidation.error("Hostname is required");
			return FormValidation.ok();
		}
		public FormValidation doCheckPort(@QueryParameter Integer value) { 
			if(value==null) return FormValidation.error("Port is required");
			return FormValidation.ok();
		}
		public FormValidation doCheckProjectName(@QueryParameter String value) { 
			if(fixEmptyAndTrim(value)==null) return FormValidation.error("Project name is required");
			return FormValidation.ok();
		}
		public FormValidation doCheckViewName(@QueryParameter String value) { 
			return FormValidation.ok();
		}
		public FormValidation doCheckFolderName(@QueryParameter String value) { 
			return FormValidation.ok();
		}
		
		public FormValidation doTestConnection(@QueryParameter("hostName") String hostName,
									      	   @QueryParameter("port") int port,
									      	   @QueryParameter("projectName") String projectName,
									      	   @QueryParameter("viewName") String viewName,
									      	   @QueryParameter("folderName") String folderName,
									      	   @QueryParameter("labelName") String labelName,
									      	   @QueryParameter("promotionstate") boolean isPromotionState,
									      	   @QueryParameter("credentialsId") String credentialsId) {

			StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull(
					CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
							Collections.<DomainRequirement> emptyList()), CredentialsMatchers.withId(credentialsId));

			String username = cred.getUsername();
			String password = cred.getPassword().getPlainText();
			try {
				loadStarTeamSdk();
			} catch (Exception e) {
				return FormValidation.error(e, "Could not load StarTeam SDK library, please specify valid path to starteam140.jar in global configuration");
			}
			try {
				StarTeamConnection connection = new StarTeamConnection(hostName, port, username, password, projectName, viewName, folderName, labelName, isPromotionState);
				connection.initialize();
			} catch (Exception e) {
				return FormValidation.error(e, "Could not connect to StarTeam");
			}
			return FormValidation.ok("Connection Successful");
		}

		public void loadStarTeamSdk() {
			StarTeamSdkLoader.loadSDK(getStarTeamJarPath(), getClass().getClassLoader());
		}
	}
	
	@Override
	 public StarTeamSCMDescriptorImpl getDescriptor() {
	 return (StarTeamSCMDescriptorImpl)super.getDescriptor();
	 }

	@Override
	public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
		
		getDescriptor().loadStarTeamSdk();
		
		FilePath changeLogFilePath = new FilePath(changelogFile) ;
		FilePath filePointFilePath = new FilePath(new File(build.getRootDir(), StarTeamConnection.FILE_POINT_FILENAME));

		setCredentials();	
		StarteamSyncWorkSpaceTask task = new StarteamSyncWorkSpaceTask(hostName, port, projectName, viewName, folderName, userName, password, labelName, promotionstate, build, changeLogFilePath, filePointFilePath, listener);
		workspace.act(task); // The StarteamSyncWorkSpaceTask.invoke() method is now invoked
	}

	@Override
	public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
		return SCMRevisionState.NONE;
	}

	@Override
	public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
		PollingResult status = PollingResult.NO_CHANGES;
		AbstractBuild<?,?> lastBuild = (AbstractBuild<?, ?>) project.getLastBuild();

		Collection<StarTeamFilePoint> historicFilePoints = null;
		if(lastBuild!=null){
			File historicRevisionFile = new File(lastBuild.getRootDir(), StarTeamConnection.FILE_POINT_FILENAME);
			if(historicRevisionFile.exists()){
				historicFilePoints = StarTeamFilePointFunctions.loadCollection(historicRevisionFile);
			}
		}

		setCredentials();	
		StarTeamPollingTask p_actor = new StarTeamPollingTask(hostName, port, userName, password, projectName, viewName, folderName, labelName, promotionstate, listener, historicFilePoints);

		getDescriptor().loadStarTeamSdk();
		if (workspace.act(p_actor)) {
			status = PollingResult.SIGNIFICANT;
		} else {
			if(listener!=null) 
			listener.getLogger().println("StarTeam polling shows no changes");
		}
		return status;
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		return new StarTeamChangeLogParser();
	}

	private void setCredentials() {
		StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull(
				CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(),ACL.SYSTEM,Collections.<DomainRequirement>emptyList()),
				CredentialsMatchers.withId(credentialsId)
				);

		this.userName = cred.getUsername();
		this.password = cred.getPassword().getPlainText();
	}

	//required for jelly to populate fields if configuration saved
	public String getHostName() {return hostName;}
	public int getPort() {return port;}
	public String getProjectName() {return projectName;}
	public String getViewName() {return viewName;}
	public String getFolderName() {return folderName;}
	public String getLabelName() {return labelName;}
	public boolean isPromotionstate() {return promotionstate;}
	public String getCredentialsId() { return credentialsId;}
}
