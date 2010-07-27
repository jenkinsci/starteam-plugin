package hudson.plugins.starteam;

import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test checkout actor and changelog functionality.
 * @author Steve Favez <sfavez@verisign.com>
 *
 */
//@Ignore
public class StarteamCheckoutActorTest {

	final static String CHECHOUT_DIRECTORY = "hudson-temp-directory" ;
	final static String CHANGE_LOG_FILE = "changes.txt" ;
	
	File parentDirectory = null ;
	
	File changeLogFile = null ;
	
	BuildListener listener = new BuildListenerImpl() ;
	/**
	 * initalise integration starteam connection
	 * @throws StarTeamSCMException 
	 * @throws IOException 
	 */
	@Before
	public void setUp() throws StarTeamSCMException, IOException {

		//create the default folder
		parentDirectory = new File(CHECHOUT_DIRECTORY) ;
		if (! parentDirectory.exists()) {
			if (! parentDirectory.mkdir()) {
				Assert.fail( "unable to create the directory" ) ;
			}
		}
		changeLogFile = new File( parentDirectory, CHANGE_LOG_FILE ) ;
		if (changeLogFile.exists()) {
			changeLogFile.delete() ;
		}
		if (! changeLogFile.createNewFile() ) {
			Assert.fail( "unable to create changelog file" ) ;
		}
		
	}
	
	private StarTeamCheckoutActor createStarteamCheckOutActor( Date aPreviousBuildDate ) {

		String hostName = System.getProperty("test.starteam.hostname", "10.170.12.246");
		int port = Integer.parseInt(System.getProperty("test.starteam.hostport", "55201")); 
		String projectName = System.getProperty("test.starteam.projectname", "NGBL");
		String viewName = System.getProperty("test.starteam.viewname", "NGBL");
		String folderName = System.getProperty("test.starteam.foldername", "NGBL/source/ant");
		String userName = System.getProperty("test.starteam.username", "");
		String password = System.getProperty("test.starteam.password", "");
		
		FilePath changeLogFilePath = new FilePath( changeLogFile ) ;
		StarTeamViewSelector config = null;
		try {
			config = new StarTeamViewSelector("", "");
		} catch (ParseException e) {
			Assert.fail("");
		}
		
		StarTeamCheckoutActor starTeamCheckoutActor =  new StarTeamCheckoutActor( hostName, port, userName, password, projectName, viewName, folderName, config, aPreviousBuildDate, new Date(), changeLogFilePath, listener) ;

		return starTeamCheckoutActor ;
	}
	
	@Test
	public void testPerformCheckOut() throws IOException {
		StarTeamCheckoutActor checkoutActor = createStarteamCheckOutActor(null) ;
		Boolean res = checkoutActor.invoke( parentDirectory , null) ;
		Assert.assertTrue( res ) ;
		Assert.assertTrue( changeLogFile.length() > 0 ) ;
	}
	
	@Test
	public void testPerformCheckWithPreviousDateOut() throws IOException {
		Calendar lastYear = Calendar.getInstance() ;
		lastYear.add(Calendar.MONTH, -3) ;
		StarTeamCheckoutActor checkoutActor = createStarteamCheckOutActor(lastYear.getTime()) ;
		Boolean res = checkoutActor.invoke( parentDirectory , null) ;
		Assert.assertTrue( res ) ;
		Assert.assertTrue( changeLogFile.length() > 0 ) ;
	}
	
	private final static class BuildListenerImpl implements BuildListener {

		PrintStream printStream ;
		PrintWriter printWriter ;

	    public BuildListenerImpl() {
	    	printStream = System.out ;
	        // unless we auto-flash, PrintStream will use BufferedOutputStream internally,
	        // and break ordering
	        this.printWriter = new PrintWriter(new BufferedWriter(
	                 new OutputStreamWriter(printStream) ), true);
	    }

	    public void started(List<Cause> causes) {
	        if (causes==null || causes.isEmpty())
	        	printStream.println("Started");
	        else for (Cause cause : causes) {
	        	printStream.println(cause.getShortDescription());
	        }
	    }

	    public PrintStream getLogger() {
	        return printStream;
	    }

	    public PrintWriter error(String msg) {
	    	printWriter.println("ERROR: "+msg);
	        return printWriter;
	    }

	    public PrintWriter error(String format, Object... args) {
	        return error(String.format(format,args));
	    }

	    public PrintWriter fatalError(String msg) {
	    	printWriter.println("FATAL: "+msg);
	        return printWriter;
	    }

	    public PrintWriter fatalError(String format, Object... args) {
	        return fatalError(String.format(format,args));
	    }

	    public void finished(Result result) {
	    	printWriter.println("Finished: "+result);
	    }
		
	}
	
}
