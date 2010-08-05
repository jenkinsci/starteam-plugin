package hudson.plugins.starteam;

import hudson.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Builds <tt>changelog.xml</tt> for {@link StarTeamSCM}.
 * 
 * Remove use of deprecated classes.
 * 
 * @author Eric D. Broyles
 * @author Steve Favez <sfavez@verisign.com>
 */
public final class StarTeamChangeLogBuilder {

	/**
	 * Stores the history objects to the output stream as xml.
	 * <p>
	 * Current version supports a format like the following:
	 * 
	 * <pre>
	 * &lt;?xml version='1.0' encoding='UTF-8'?&gt;
	 *   &lt;changelog&gt;
	 *         &lt;entry&gt;
	 *                 &lt;revisionNumber&gt;73&lt;/revisionNumber&gt;
	 *                 &lt;date&gt;2008-06-23 09:46:27&lt;/date&gt;
	 *                 &lt;message&gt;Checkin message&lt;/message&gt;
	 *                 &lt;user&gt;Author Name&lt;/user&gt;
	 *         &lt;/entry&gt;
	 *   &lt;/changelog&gt;
	 * 
	 * </pre>
	 * 
	 * </p>
	 * 
	 * @param aOutputStream
	 *            the stream to write to
	 * @param aChanges
	 *            the history objects to store
	 * @throws IOException
	 * 
	 */
	public static boolean writeChangeLog(OutputStream outputStream,
			StarTeamChangeSet changeSet)
			throws IOException {

		GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance();
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
	    dateFormat.setCalendar(cal);
	    dateFormat.setLenient(false);
		
		OutputStreamWriter writer = new OutputStreamWriter(outputStream,
				Charset.forName("UTF-8"));
		
		PrintWriter printwriter = new PrintWriter( writer ) ;

		printwriter.println("<?xml version='1.0' encoding='UTF-8'?>");
		printwriter.println("<changelog>");
		for (StarTeamChangeLogEntry change : changeSet.getChanges()) {
			writeEntry(dateFormat, printwriter, change);
		}
		printwriter.println("</changelog>");
		printwriter.close();
		return true;
	}

	/**
	 * @param dateFormat
	 * @param printwriter
	 * @param change
	 */
	private static void writeEntry(SimpleDateFormat dateFormat,
			PrintWriter printwriter, StarTeamChangeLogEntry change) {
		printwriter.println("\t<entry>");
		printwriter.println("\t\t<fileName>" + change.getFileName() + "</fileName>");
		printwriter.println("\t\t<revisionNumber>" + change.getRevisionNumber()
				+ "</revisionNumber>");
		java.util.Date aDate = change.getDate();
		printwriter.println("\t\t<date>"
				+ Util.xmlEscape(dateFormat.format(aDate)) + "</date>");
		printwriter.println("\t\t<message>"
				+ Util.xmlEscape(change.getMsg()) + "</message>");
		printwriter.println("\t\t<user>"
				+ change.getUsername()
				+ "</user>");
		printwriter.println("\t\t<changeType>"
				+ change.getChangeType()
				+ "</changeType>");
		printwriter.println("\t</entry>");
	}

}
