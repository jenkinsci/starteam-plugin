package hudson.plugins.starteam.integration;

import hudson.plugins.starteam.StarTeamConnection;
import hudson.plugins.starteam.StarTeamFunctions;
import hudson.plugins.starteam.StarTeamSCMException;
import hudson.plugins.starteam.StarTeamViewSelector;

import java.io.File;
import java.text.ParseException;
import java.util.Collection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.starbase.starteam.Folder;

public class StarTeamViewSelectorIntegrationTest {

	
	/**
	 * starteam connection
	 */
	private StarTeamConnection starTeamConnection ;
	
	/**
	 * directory to use to perform the checkout.
	 */
	private File parentDirectory ;
	private String labelName;
	private String promotionName;
	private String changeDate;
	private String hostName;
	private int port;
	private String projectName;
	private String viewName;
	private String folderName;
	private String userName;
	private String password;
	
	/**
	 * initalise integration starteam connection
	 * @throws StarTeamSCMException 
	 */
	@Before
	public void setUp() throws StarTeamSCMException {
		hostName = System.getProperty("test.starteam.hostname", "10.170.12.246");
		port = Integer.parseInt(System.getProperty("test.starteam.hostport", "55201")); 
		projectName = System.getProperty("test.starteam.projectname", "NGBL");
		viewName = System.getProperty("test.starteam.viewname", "NGBL");
		folderName = System.getProperty("test.starteam.foldername", "NGBL/source/ant");
		userName = System.getProperty("test.starteam.username", "");
		password = System.getProperty("test.starteam.password", "");

		labelName = System.getProperty("test.starteam.labelname", "hudsonTestLabel");
		promotionName = System.getProperty("test.starteam.promotionname", "hudsonPromotionState");
		changeDate = System.getProperty("test.starteam.changedate", "2010/7/14 00:00:00");
			
		//create the default folder
		parentDirectory = new File("hudson-temp-directory") ;
		if (! parentDirectory.exists()) {
			if (! parentDirectory.mkdir()) {
				Assert.fail( "unable to create the directory" ) ;
			}
		}
		
	}

	@After
	public void tearDown()	{
	    starTeamConnection.close() ;
	}

	/**
	 * By default the selector should pick CURRENT
	 * @throws StarTeamSCMException 
	 */
	@Test
	public final void testCurrent() throws ParseException, StarTeamSCMException {
		StarTeamViewSelector selector = new StarTeamViewSelector(null,null);
		starTeamConnection = new StarTeamConnection( hostName, port, userName, password, projectName, viewName, folderName, selector) ;
		starTeamConnection.initialize() ;
		Folder rootFolder = starTeamConnection.getRootFolder();
		Collection<com.starbase.starteam.File> starteamFiles = StarTeamFunctions.listAllFiles(rootFolder, parentDirectory);
		Assert.assertNotNull(starteamFiles) ;
		Assert.assertTrue( starteamFiles.size() > 0 ) ;
		starTeamConnection.close() ;
	}

	/**
	 * Label should not be found
	 */
	@Test (expected=StarTeamSCMException.class)
	public final void testMissingLabel() throws ParseException, StarTeamSCMException {
		StarTeamViewSelector selector = new StarTeamViewSelector("xyzzy-You Are a Sharlatan","LABEL");
		starTeamConnection = new StarTeamConnection( hostName, port, userName, password, projectName, viewName, folderName, selector) ;
		starTeamConnection.initialize() ;
		Assert.fail("Missing label should cause exeception: 'Couldn't find label [...] in view ...'");
	}

	/**
	 * Label should be accepted 
	 */
	@Test
	public final void testLabels() throws ParseException, StarTeamSCMException {
		StarTeamViewSelector selector = new StarTeamViewSelector(labelName,"LABEL");
		starTeamConnection = new StarTeamConnection( hostName, port, userName, password, projectName, viewName, folderName, selector) ;
		starTeamConnection.initialize() ;
		Folder rootFolder = starTeamConnection.getRootFolder();
		Collection<com.starbase.starteam.File> starteamFiles = StarTeamFunctions.listAllFiles(rootFolder, parentDirectory);
		Assert.assertNotNull(starteamFiles) ;
		Assert.assertTrue( starteamFiles.size() > 0 ) ;
		starTeamConnection.close() ;
	}
	
	/**
	 * Time should be in correct format 
	 */
	@Test
	public final void testTimeValue() throws ParseException, StarTeamSCMException {
		StarTeamViewSelector selector = new StarTeamViewSelector(changeDate,"TIME");
		starTeamConnection = new StarTeamConnection( hostName, port, userName, password, projectName, viewName, folderName, selector) ;
		starTeamConnection.initialize() ;
		Folder rootFolder = starTeamConnection.getRootFolder();
		Collection<com.starbase.starteam.File> starteamFiles = StarTeamFunctions.listAllFiles(rootFolder, parentDirectory);
		Assert.assertNotNull(starteamFiles) ;
		Assert.assertTrue( starteamFiles.size() > 0 ) ;
		starTeamConnection.close() ;
	}

	@Test (expected = com.starbase.starteam.ServerException.class)
	public final void testTimeBeforeTime() throws ParseException, StarTeamSCMException {
		StarTeamViewSelector selector = new StarTeamViewSelector("1970/1/1 00:00:00","TIME");
		starTeamConnection = new StarTeamConnection( hostName, port, userName, password, projectName, viewName, folderName, selector) ;
		starTeamConnection.initialize() ;
		Assert.fail("Time before folder creation should cause exeception: 'The reference view is no longer available. Its root folder has been deleted from the parent view.'");
	}
	
	
	/**
	 * Promotion label should be accepted
	 */
	@Test
	public final void testPromotionValue() throws ParseException, StarTeamSCMException {
		StarTeamViewSelector selector = new StarTeamViewSelector(promotionName,"PROMOTION");
		starTeamConnection = new StarTeamConnection( hostName, port, userName, password, projectName, viewName, folderName, selector) ;
		starTeamConnection.initialize() ;
		Folder rootFolder = starTeamConnection.getRootFolder();
		Collection<com.starbase.starteam.File> starteamFiles = StarTeamFunctions.listAllFiles(rootFolder, parentDirectory);
		Assert.assertNotNull(starteamFiles) ;
		Assert.assertTrue( starteamFiles.size() > 0 ) ;
		starTeamConnection.close() ;
	}
}
