package hudson.plugins.starteam;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * <p>
 * Implementation of {@link ChangeLogSet} for StarTeam SCM.
 * </p>
 * 
 * @author Eric D. Broyles
 * @version 1.0
 */
public class StarTeamChangeLogSet extends ChangeLogSet<StarTeamChangeLogEntry> {

	private List<StarTeamChangeLogEntry> history = null;

	/**
	 * default constructor for log set.
	 * 
	 * @param aBuild
	 * 		the build.
	 * @param logs
	 * 		all logs entry.
	 */
	public StarTeamChangeLogSet(AbstractBuild<?, ?> aBuild,
			List<StarTeamChangeLogEntry> logs) {
		super(aBuild);
		this.history = Collections.unmodifiableList(logs);
	}

	@Override
	public boolean isEmptySet() {
		return history.isEmpty();
	}

	/**
	 * return an iterator on all change log entry.
	 */
	public Iterator<StarTeamChangeLogEntry> iterator() {
		return history.iterator();
	}

	/**
	 * Return the history for this change log set.
	 * 
	 * @return a List of all log entries
	 */
	public List<StarTeamChangeLogEntry> getHistory() {
		return history;
	}

}
