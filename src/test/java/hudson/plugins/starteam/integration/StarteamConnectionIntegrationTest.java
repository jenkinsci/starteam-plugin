package hudson.plugins.starteam.integration;

import hudson.plugins.starteam.StarTeamChangeLogEntry;
import hudson.plugins.starteam.StarTeamChangeSet;
import hudson.plugins.starteam.StarTeamConnection;
import hudson.plugins.starteam.StarTeamFilePoint;
import hudson.plugins.starteam.StarTeamFunctions;
import hudson.plugins.starteam.StarTeamSCMException;
import hudson.plugins.starteam.StarTeamViewSelector;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.starbase.starteam.Folder;

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

		starTeamConnection = new StarTeamConnection( hostName, port, userName, password, projectName, viewName, folderName, null ) ;
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
		
		Collection<com.starbase.starteam.File> starteamFiles = StarTeamFunctions.listAllFiles(starTeamConnection.getRootFolder(), parentDirectory);
		Assert.assertNotNull(starteamFiles) ;
		Assert.assertTrue( starteamFiles.size() > 0 ) ;
		int i=0;
		for (com.starbase.starteam.File file: starteamFiles)
		{
			Assert.assertNotNull("file ["+i+"] in list of all files is null",file);
			i++;
		}
		starTeamConnection.close() ;
	}
	
	/**
	 * find all changed files in starteam repository.
	 * @throws IOException 
	 * @throws StarTeamSCMException 
	 */
	@Test
	public void testFindChangedFiles() throws StarTeamSCMException, IOException {

		// get connection with view set in past
		Calendar timeInPast = Calendar.getInstance() ;
		timeInPast.add(Calendar.MONTH, -3);
		StarTeamViewSelector selector = new StarTeamViewSelector(timeInPast.getTime());		
		StarTeamConnection oldStarTeamConnection = new StarTeamConnection(starTeamConnection,selector);
		oldStarTeamConnection.initialize();

		// get file list from the view to identify changes since the timeInPast 
		// there is no list of previous files 
		Folder rootFolder = oldStarTeamConnection.getRootFolder();
		File workspace = parentDirectory;
		StarTeamChangeSet oldChangeSet = oldStarTeamConnection.computeChangeSet(rootFolder, workspace, null, System.out);

		// a sanity check - everything in the old set should be new, because it doesn't have a previous build
		Assert.assertNotNull(oldChangeSet) ;
		Assert.assertFalse(oldChangeSet.isComparisonAvailable()) ;
		Assert.assertTrue(oldChangeSet.hasChanges()) ;

		// a sanity check - everything in the old set should be new, because it doesn't have a previous build
		Collection<StarTeamFilePoint> historicStarteamFilePoint = oldChangeSet.getFilePointsToRemember();
		Assert.assertNotNull(historicStarteamFilePoint) ;
		Assert.assertFalse(historicStarteamFilePoint.isEmpty()) ;

		Collection<StarTeamChangeLogEntry> oldLogEntries = oldChangeSet.getChanges();
		Assert.assertNotNull(oldLogEntries) ;
		Assert.assertFalse(oldLogEntries.isEmpty()) ;
		
		// get the change set from connection using historical list of files
		rootFolder = starTeamConnection.getRootFolder();
		StarTeamChangeSet newChangeSet = starTeamConnection.computeChangeSet(rootFolder, workspace, historicStarteamFilePoint, System.out);
		Assert.assertNotNull(newChangeSet) ;
		Assert.assertTrue(newChangeSet.isComparisonAvailable()) ;
		Assert.assertTrue(newChangeSet.hasChanges()) ;

		Collection<StarTeamFilePoint> newStarteamFilePoint = newChangeSet.getFilePointsToRemember();
		Assert.assertNotNull(newStarteamFilePoint) ;
		Assert.assertFalse(newStarteamFilePoint.isEmpty()) ;
	
		Collection<StarTeamChangeLogEntry> newLogEntries = newChangeSet.getChanges();
		Assert.assertNotNull(newLogEntries) ;
		Assert.assertFalse(newLogEntries.isEmpty()) ;
				
		oldStarTeamConnection.close() ;
		starTeamConnection.close() ;
	}
}
