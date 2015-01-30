package hudson.plugins.starteam;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCMDescriptor;
import hudson.scm.ChangeLogSet.Entry;
import hudson.slaves.DumbSlave;

import java.nio.charset.Charset;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;


/**
 * StarTeam SCM plugin for Hudson.
 * 
 * @author jan_ruzicka
 */
public class StarTeamSCMTest extends HudsonTestCase {

	private static final int LOG_LIMIT = 1000;
	StarTeamSCM t;
	String hostName = System.getProperty("test.starteam.hostname", "127.0.0.1");
	int port = Integer.parseInt(System.getProperty("test.starteam.hostport", "1")) ; 
	String projectName = System.getProperty("test.starteam.projectname", "");
	String viewName = System.getProperty("test.starteam.viewname", "");
	String folderName = System.getProperty("test.starteam.foldername", "");
	String userName = System.getProperty("test.starteam.username", "");
	String password = System.getProperty("test.starteam.password", "");
	String labelName = System.getProperty("test.starteam.labelname", "hudsonTestLabel");
	String promotionName = System.getProperty("test.starteam.promotionname", "hudsonPromotionState");
	String changeDate = System.getProperty("test.starteam.changedate", "2010/7/14");
	String testFile = System.getProperty("test.starteam.testfile", "");


	@Before
	public void setUp() throws Exception {
		super.setUp();
		t = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password, null, false, true) ;
	}
		
	/**
	 * The constructor.
	 */
	@Test
	public void testConstructorStarTeamSCM()
	{
		    boolean promotionState = false;
                    boolean ignoreWorkingFolder = true;
			StarTeamSCM t = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password,  labelName, promotionState, ignoreWorkingFolder) ;
			assertEquals(hostName,t.getHostname());
			assertEquals(port,t.getPort());
			assertEquals(projectName,t.getProjectname());
			assertEquals(viewName,t.getViewname());
			assertEquals(folderName,t.getFoldername());
			assertEquals(userName,t.getUsername());
			assertEquals(password,t.getPassword());
			assertEquals(labelName,t.getLabelname());
			assertEquals(promotionState,t.isPromotionstate());
                        assertEquals(ignoreWorkingFolder,t.isIgnoreWorkingFolder());
	}

	   /**
     * Makes sure that checking out on the slave will work.
     */
    @Bug(7967)
    @Test
    public void testRemoteCheckOut() throws Exception {
        DumbSlave s = createSlave();
        FreeStyleProject p = createFreeStyleProject();
        p.setAssignedLabel(s.getSelfLabel());
        boolean promotionState = false;
        boolean ignoreWorkingFolder = false;
        p.setScm(new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password,  labelName, promotionState, ignoreWorkingFolder));

        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserCause()).get());
        assertTrue(b.getWorkspace().child(testFile).exists());  // use a file that is in the root directory of your project
        b = assertBuildStatusSuccess(p.scheduleBuild2(0).get());
    }
    
	/**
     * Makes sure that the configuration survives the round-trip.
     */
	@Bug(6881)
	@Test
    public void testConfigRoundtrip() throws Exception {
	boolean promotionState = false;
        boolean ignoreWorkingFolder = false;
        FreeStyleProject project = createFreeStyleProject();
        StarTeamSCM scm = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password, labelName, promotionState, ignoreWorkingFolder) ;
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "hostname,port,projectname,viewname,foldername,username,password,labelname,promotionstate");
        
        promotionState = true;
        scm = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password, promotionName, promotionState, true) ;
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "hostname,port,projectname,viewname,foldername,username,password,labelname,promotionstate");

    }

    /**
     * Makes sure that the configuration survives the round-trip.
     */
	@Test
    public void testCheckout() throws Exception {
        // prepare with base label
        boolean promotionState = false;
        boolean ignoreWorkingFolder = false;
        FreeStyleProject project = createFreeStyleProject();
        StarTeamSCM scm = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password, labelName, promotionState, ignoreWorkingFolder) ;
        project.setScm(scm);
        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(build.getLog(LOG_LIMIT));
        assertBuildStatus(Result.SUCCESS,build);
        FreeStyleBuild lastBuild =project.getLastBuild();
        Set<User> commiters = lastBuild.getCulprits();
        Assert.assertNotNull(commiters);
        Assert.assertFalse(commiters.isEmpty());
        ChangeLogSet<? extends Entry> changes = lastBuild.getChangeSet();
        Assert.assertFalse(changes.isEmptySet());

        // try build again - it should not show any changes
        build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(build.getLog(LOG_LIMIT));
        assertBuildStatus(Result.SUCCESS,build);
        lastBuild =project.getLastBuild();
        commiters = lastBuild.getCulprits();
        Assert.assertNotNull(commiters);
        Assert.assertTrue("There should be no changes", commiters.isEmpty());        
        changes = lastBuild.getChangeSet();
        Assert.assertTrue("There should be no changes", changes.isEmptySet());
        
        // move to previous label
        scm = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password, labelName+"Before", promotionState, true) ;
        project.setScm(scm);
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(build.getLog(LOG_LIMIT));
        assertBuildStatus(Result.SUCCESS,build);
        lastBuild =project.getLastBuild();
        commiters = lastBuild.getCulprits();
        Assert.assertNotNull(commiters);
        Assert.assertFalse(commiters.isEmpty());
        
        // move to next label
        scm = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password, labelName+"After", promotionState, true) ;
        project.setScm(scm);
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        
        System.out.println(build.getLog(LOG_LIMIT));
        assertBuildStatus(Result.SUCCESS,build);
        lastBuild =project.getLastBuild();
        commiters = lastBuild.getCulprits();
        Assert.assertNotNull(commiters);
        Assert.assertFalse(commiters.isEmpty());
        
    }
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#createChangeLogParser()
	 */
	@Test
	public void testCreateChangeLogParser() {
		ChangeLogParser p =  new StarTeamChangeLogParser();
		assertNotNull(p);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#getDescriptor()
	 */
	@Test
	public void testGetDescriptor() {
		SCMDescriptor<StarTeamSCM> d=t.getDescriptor();
		assertNotNull(d);
		assertSame(StarTeamSCM.DESCRIPTOR,d);
	}
    /*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#pollChanges(hudson.model.AbstractProject,
	 *      hudson.Launcher, hudson.FilePath, hudson.model.TaskListener)
	 */
	@Test
	public void testPollChanges() throws Exception {
		
		FreeStyleProject project = createFreeStyleProject();
		project.setScm(t);

		final TaskListener listener = new StreamBuildListener (System.out,Charset.forName("UTF-8"));

		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();

		System.out.println(build.getLog(LOG_LIMIT));
		assertBuildStatus(Result.SUCCESS,build);
		FreeStyleBuild lastBuild = project.getLastBuild();
		assertNotNull(lastBuild);

		// polling right after a build should not find any changes.
		boolean result = project.pollSCMChanges(listener);

		assertFalse(result);
	}

	/*
	 * Get the hostname this SCM is using.
	 * 
	 * @see hudson.plugins.starteam.StarTeamSCM#getHostname()
	 */
	@Test
	public void testGetHostname() {
		assertEquals(hostName,t.getHostname());
	}

	/*
	 * Get the port number this SCM is using.
	 * 
	 * @see hudson.plugins.starteam.StarTeamSCM#getPort()
	 */
	@Test
	public void testGetPort() {
		assertEquals(port,t.getPort());
	}

	/*
	 * Get the project name this SCM is connected to.
	 * 
	 * @see hudson.plugins.starteam.StarTeamSCM#getProjectname()
	 */
	@Test
	public void testGetProjectname() {
		assertEquals(projectName, t.getProjectname());
	}

	/*
	 * Get the view name in the project this SCM uses.
	 * 
	 * @see hudson.plugins.starteam.StarTeamSCM#getViewname()
	 */
	@Test
	public void testGetViewname() {
		assertEquals(viewName,t.getViewname());
	}

	/*
	 * Get the root folder name of our monitored workspace.
	 * 
	 * @see hudson.plugins.starteam.StarTeamSCM#getFoldername()
	 */
	@Test
	public void testGetFoldername() {
		assertEquals(folderName,t.getFoldername());
	}

	/*
	 * Get the username used to connect to starteam.
	 * 
	 * @see hudson.plugins.starteam.StarTeamSCM#getUsername()
	 */
	@Test
	public void testGetUsername() {
		assertEquals(userName,t.getUsername());
	}

	/*
	 * Get the password used to connect to starteam.
	 * 
	 * @see hudson.plugins.starteam.StarTeamSCM#getPassword()
	 */
	@Test
	public void testGetPassword() {
		assertEquals(password,t.getPassword());
	}
}
