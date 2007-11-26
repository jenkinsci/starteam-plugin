/**
 * 
 */
package hudson.plugins.starteam;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.io.File;
import java.io.IOException;

import org.xml.sax.SAXException;

/**
 * @author ip90568
 * 
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
		// TODO Auto-generated method stub
		return ChangeLogSet.createEmpty(build);
	}

}
