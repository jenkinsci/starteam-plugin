package hudson.plugins.starteam;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Test checkout actor and changelog functionality.
 * @author Steve Favez <sfavez@verisign.com>
 *
 */
//@Ignore
public class StarTeamChangeLogParserTest {

	final static String CHECHOUT_DIRECTORY = "hudson-temp-directory" ;
	final static String CHANGE_LOG_FILE = "changes.txt" ;
	
	final static String CHANGE_LOG_CONTENTS = 
					"<?xml version='1.0' encoding='UTF-8'?>\n" +
					"<changelog>\n" +
					"	<entry>\n" +
					"		<fileName>config_file.ini</fileName>\n" +
					"		<revisionNumber>3</revisionNumber>\n" +
					"		<date>2010-07-13 22:00:12</date>\n" +
					"		<message>this is a comment for another Example of a change</message>\n" +
					"		<user>JRuzicka</user>\n" +
					"	</entry>\n" +
					"</changelog>\n";
	
	File parentDirectory = null ;
	
	File changeLogFile = null ;
	
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
		FileWriter fw= new FileWriter(changeLogFile);
		fw.write(CHANGE_LOG_CONTENTS);
		fw.flush();
		fw.close();
	}
	
	@Test
	public void testParseString() throws IOException, SAXException, ParseException {
		InputStream aChangeLogStream = new ByteArrayInputStream(CHANGE_LOG_CONTENTS.getBytes("UTF-8")) ;
		AbstractBuild aBuild = null;
		ChangeLogSet res = StarTeamChangeLogParser.parse(aBuild , aChangeLogStream);
		Assert.assertFalse( res.isEmptySet() ) ;
		Iterator it = res.iterator();
		Assert.assertTrue(it.hasNext());
		StarTeamChangeLogEntry entry = (StarTeamChangeLogEntry) it.next();
		Assert.assertEquals("config_file.ini", entry.getFileName() ) ;
		Assert.assertEquals("this is a comment for another Example of a change", entry.getMsg() ) ;
		Assert.assertEquals(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2010-07-13 22:00:12"), entry.getDate() ) ;
		Assert.assertEquals("JRuzicka", entry.getUsername() ) ;
		Assert.assertEquals(3, entry.getRevisionNumber() ) ;
		Assert.assertFalse(it.hasNext());
	}
	
	@Test
	public void testParseFile() throws IOException, SAXException, ParseException {
		AbstractBuild aBuild = null;
		StarTeamChangeLogParser p = new StarTeamChangeLogParser();
		ChangeLogSet res = p.parse(aBuild , changeLogFile);
		Assert.assertFalse( res.isEmptySet() ) ;
		Iterator it = res.iterator();
		Assert.assertTrue(it.hasNext());
		StarTeamChangeLogEntry entry = (StarTeamChangeLogEntry) it.next();
		Assert.assertEquals("config_file.ini", entry.getFileName() ) ;
		Assert.assertEquals("this is a comment for another Example of a change", entry.getMsg() ) ;
		Assert.assertEquals(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2010-07-13 22:00:12"), entry.getDate() ) ;
		Assert.assertEquals("JRuzicka", entry.getUsername() ) ;
		Assert.assertEquals(3, entry.getRevisionNumber() ) ;
		Assert.assertFalse(it.hasNext());
	}
	
}
