package hudson.plugins.starteam;

import hudson.model.User;
import hudson.scm.ChangeLogSet;

import java.util.Collection;
import java.util.Date;

/**
 * <p>
 * Implementation of {@link ChangeLogSet.Entry} for StarTeam SCM.
 * </p>
 * 
 * @author Eric D. Broyles
 * @version 1.0
 */
public class StarTeamChangeLogEntry extends hudson.scm.ChangeLogSet.Entry {
	private int revisionNumber;

	private String username;

	private String msg;

	private Date date;

	private String fileName;

	@Override
	public Collection<String> getAffectedPaths() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Gets the Hudson user based upon the StarTeam {@link #username}.
	 * 
	 * @see hudson.scm.ChangeLogSet.Entry#getAuthor()
	 */
	@Override
	public User getAuthor() {
		return User.get(username);
	}

	public void setUsername(String aUsername) {
		this.username = aUsername;
	}

	@Override
	public String getMsg() {
		return msg;
	}

	public void setMsg(String aMsg) {
		this.msg = aMsg;
	}

	public int getRevisionNumber() {
		return revisionNumber;
	}

	public void setRevisionNumber(int aRevisionNumber) {
		this.revisionNumber = aRevisionNumber;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date aDate) {
		this.date = aDate;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String aFileName) {
		this.fileName = aFileName;
	}

}
