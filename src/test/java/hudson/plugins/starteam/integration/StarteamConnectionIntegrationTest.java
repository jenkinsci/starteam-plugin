package hudson.plugins.starteam.integration;

import java.io.File;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import hudson.plugins.starteam.StarTeamConnection;
import hudson.plugins.starteam.StarTeamSCMException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * test all starteam functionalities against a real starteam repository
 * 
 * @author Steve Favez <sfavez@verisign.com>
 *
 */
//@Ignore
public class StarteamConnectionIntegrationTest {
	
	/**
	 * starteam connection
	 */
	private StarTeamConnection starTeamConnection ;
	
	/**
	 * directory to use to perform the checkout.
	 */
	private File parentDirectory ;
	
	/**
	 * initalise integration starteam connection
	 * @throws StarTeamSCMException 
	 */
	@Before
	public void setUp() throws StarTeamSCMException {
		//initialise the starteam connection
		String hostName = System.getProperty("test.starteam.hostname", "10.170.12.246");
		int port = Integer.parseInt(System.getProperty("test.starteam.hostport", "55201")); 
		String projectName = System.getProperty("test.starteam.projectname", "NGBL");
		String viewName = System.getProperty("test.starteam.viewname", "NGBL");
		String folderName = System.getProperty("test.starteam.foldername", "NGBL/source/ant");
		String userName = System.getProperty("test.starteam.username", "");
		String password = System.getProperty("test.starteam.password", "");

		starTeamConnection = new StarTeamConnection( hostName, port, userName, password, projectName, viewName, folderName ) ;
		starTeamConnection.initialize() ;
	
		//create the default folder
		parentDirectory = new File("hudson-temp-directory") ;
		if (! parentDirectory.exists()) {
			if (! parentDirectory.mkdir()) {
				Assert.fail( "unable to create the directory" ) ;
			}
		}
		
	}
	
	/**
	 * find all current files in a given starteam repository.
	 */
	@Test
	public void testFindAllFiles() {
		Collection<com.starbase.starteam.File> starteamFiles = starTeamConnection.findAllFiles(parentDirectory, System.out, true).values() ;
		Assert.assertNotNull(starteamFiles) ;
		Assert.assertTrue( starteamFiles.size() > 0 ) ;
		starTeamConnection.close() ;
	}
	
	/**
	 * find all changed files in starteam repository since last year.
	 */
	@Test
	public void testFindChangedFiles() {
		Calendar lastYear = Calendar.getInstance() ;
		lastYear.add(Calendar.MONTH, -3) ;
		Collection<com.starbase.starteam.File> starteamFiles = starTeamConnection.findChangedFiles(parentDirectory , System.out, lastYear.getTime()) ;
		Assert.assertNotNull(starteamFiles) ;
		Assert.assertTrue( starteamFiles.size() > 0 ) ;
		starTeamConnection.close() ;
	}
	
	@Test
	public void testCalculatePreviousSynchronizedDate() {
		Calendar lastHour = Calendar.getInstance() ;
		lastHour.add(Calendar.HOUR, -1) ;
		Calendar currentDate = Calendar.getInstance() ;
		
		Date newDate = starTeamConnection.calculatePreviousDateWithTimeZoneCheck(lastHour.getTime(), currentDate.getTime()) ;
		Assert.assertNotNull(newDate) ;
		
	}
}
