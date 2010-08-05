/**
 * 
 */
package hudson.plugins.starteam;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

/**
 * ChangeLogParser implementation for the StarTeam SCM.
 * 
 * @author Eric D. Broyles
 * @version 1.0
 */
public class StarTeamChangeLogParser extends ChangeLogParser {

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.ChangeLogParser#parse(hudson.model.AbstractBuild,
	 *      java.io.File)
	 */
	@Override
	public ChangeLogSet<? extends Entry> parse(AbstractBuild build,
			File changelogFile) throws IOException, SAXException {
		return StarTeamChangeLogParser.parse(build, new FileInputStream(
				changelogFile));
	}
	/**
	 * Parses the change log stream and returns a Perforce change log set.
	 * 
	 * @param aBuild
	 *            the build for the change log
	 * @param aChangeLogStream
	 *            input stream containing the change log
	 * @return the change log set
	 */
	@SuppressWarnings("unchecked")
	public static StarTeamChangeLogSet parse(AbstractBuild aBuild,
			InputStream aChangeLogStream) throws IOException, SAXException {

		ArrayList<StarTeamChangeLogEntry> changeLogEntries = new ArrayList<StarTeamChangeLogEntry>();

		SAXReader reader = new SAXReader();
		Document changeDoc = null;
		StarTeamChangeLogSet changeLogSet = new StarTeamChangeLogSet(aBuild,
				changeLogEntries);

		try {
			changeDoc = reader.read(aChangeLogStream);

			Node historyNode = changeDoc.selectSingleNode("/changelog");
			if (historyNode == null)
				return changeLogSet;

			List<Node> entries = historyNode.selectNodes("entry");
			if (entries == null)
				return changeLogSet;

			for (Node node : entries) {
				StarTeamChangeLogEntry change = new StarTeamChangeLogEntry();

				if (node.selectSingleNode("fileName") != null)
					change.setFileName(node.selectSingleNode("fileName")
							.getStringValue());
				if (node.selectSingleNode("revisionNumber") != null)
					change.setRevisionNumber(Integer.parseInt((node.selectSingleNode(
							"revisionNumber").getStringValue())));

				if (node.selectSingleNode("date") != null)
					change.setDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
							.parse(node.selectSingleNode("date")
									.getStringValue()));

				if (node.selectSingleNode("message") != null)
					change.setMsg(node.selectSingleNode("message")
							.getStringValue());

				if (node.selectSingleNode("user") != null)
					change.setUsername(node.selectSingleNode("user")
							.getStringValue());

				if (node.selectSingleNode("changeType") != null)
					change.setChangeType(node.selectSingleNode("changeType")
							.getStringValue());

				changeLogEntries.add(change);
			}
		} catch (Exception e) {
			throw new IOException("Failed to parse changelog file: "
					+ e.getMessage());
		}

		return changeLogSet;
	}

}
