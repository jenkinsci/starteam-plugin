package hudson.plugins.starteam;

import hudson.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;

import com.starbase.starteam.File;

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
	 * @param aConnection
	 *            the connection to the StarTeam Server (required to determine
	 *            the name of the user who made changes)
	 * @throws IOException
	 * 
	 */
	public static boolean writeChangeLog(OutputStream aOutputStream,
			Collection<File> aChanges, StarTeamConnection aConnection)
			throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(aOutputStream,
				Charset.forName("UTF-8"));
		
		PrintWriter printwriter = new PrintWriter( writer ) ;

		printwriter.println("<?xml version='1.0' encoding='UTF-8'?>");
		printwriter.println("<changelog>");
		for (File change : aChanges) {
			printwriter.println("\t<entry>");
			printwriter.println("\t\t<fileName>" + change.getName() + "</fileName>");
			printwriter.println("\t\t<revisionNumber>" + change.getContentVersion()
					+ "</revisionNumber>");
			printwriter.println("\t\t<date>"
					+ Util.xmlEscape(javaDateToStringDate(change
							.getModifiedTime().createDate())) + "</date>");
			printwriter.println("\t\t<message>"
					+ Util.xmlEscape(change.getComment()) + "</message>");
			printwriter.println("\t\t<user>"
					+ aConnection.getUsername(change.getModifiedBy())
					+ "</user>");
			printwriter.println("\t</entry>");
		}
		printwriter.println("</changelog>");
		printwriter.close();
		return true;
	}

	/**
	 * This takes a java.util.Date and converts it to a string.
	 * @param aDate
	 * 		the date to convert
	 * @return A string representation of the date
	 * @author Mike Wille
	 */
	public static String javaDateToStringDate(java.util.Date aDate) {
		if (aDate == null)
			return "";

		GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance();
		cal.clear();
		cal.setTime(aDate);

		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);

		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int min = cal.get(Calendar.MINUTE);
		int sec = cal.get(Calendar.SECOND);

		String date = year + "-" + putZero(month) + "-" + putZero(day);
		if (hour + min + sec > 0)
			date += " " + putZero(hour) + ":" + putZero(min) + ":"
					+ putZero(sec);

		return date;
	}

	private static String putZero(int aIndex) {
		if (aIndex < 10) {
			return "0" + aIndex;
		}
		return aIndex + "";
	}
}
