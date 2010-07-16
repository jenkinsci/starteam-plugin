package hudson.plugins.starteam;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.FreeStyleProject;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCMDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;


/**
 * StarTeam SCM plugin for Hudson.
 * 
 * @author jan_ruzicka
 */
public class StarTeamSCMTest extends HudsonTestCase {

	StarTeamSCM t;
	String hostName = System.getProperty("test.starteam.hostname", "127.0.0.1");
	int port = Integer.parseInt(System.getProperty("test.starteam.hostport", "1")) ; 
	String projectName = System.getProperty("test.starteam.projectname", "");
	String viewName = System.getProperty("test.starteam.viewname", "");
	String folderName = System.getProperty("test.starteam.foldername", "");
	String userName = System.getProperty("test.starteam.username", "");
	String password = System.getProperty("test.starteam.password", "");

	@Before
	public void setUp() throws Exception {
		super.setUp();
		t = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password) ;
	}
		
	/**
	 * The constructor.
	 */
	@Test
	public void testConstructorStarTeamSCM()
	{
			StarTeamSCM t = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password) ;
			assertEquals(hostName,t.getHostname());
			assertEquals(port,t.getPort());
			assertEquals(projectName,t.getProjectname());
			assertEquals(viewName,t.getViewname());
			assertEquals(folderName,t.getFoldername());
			assertEquals(userName,t.getUsername());
			assertEquals(password,t.getPassword());
	}
    /**
     * Makes sure that the configuration survives the round-trip.
     */
	@Bug(6881)
	@Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        StarTeamSCM scm = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password) ;
        project.setScm(scm);

        // config roundtrip
        submit(new WebClient().getPage(project,"configure").getFormByName("config"));

        // verify that the data is intact
        assertEqualBeans(scm, project.getScm(),
                "hostname,port,projectname,viewname,foldername,username,password");

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
	public void testPollChanges() throws IOException, InterruptedException, ReactorException {
		
		FreeStyleProject proj = createFreeStyleProject();
		proj.setScm(t);
		final Launcher launcher = null;
		File localPath = new File("tmp.workspace");
		final FilePath workspace = new FilePath(localPath );
		final TaskListener listener = new StreamBuildListener (System.out,Charset.forName("UTF-8"));
		boolean status = t.pollChanges( proj,launcher, workspace,listener) ;
		assertFalse(status);
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
