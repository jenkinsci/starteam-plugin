package hudson.plugins.starteam;

import java.util.ArrayList;
import java.util.Collection;

import com.starbase.starteam.File;

/**
 * The collection of actions that need to be performed upon checkout.
 *
 * Files to remove: Typically folders get removed in starteam and the files get left on disk.
 *
 * Files to checkout: Files that are out of date, missing, etc.
 *
 * File Points to remember: When using promotions states/labels file changes may be pushed forward
 *    or rolled backwards.  Either way, it is difficult (using starteam) to accurately determine
 *    the previous build when various different labelling strategies are being used (e.g. promotion
 *    states, etc).  For this reason we persist a list of the filepoints used upon checkout in the
 *    build folder.  This is then used to compare current v.s. historic and compute the changelist.
 *
 * Changes to log: LogEntries for changes. This is information to be written to change log
 */
public class StarTeamChangeSet {

  private boolean comparisonAvailable;

  private Collection<java.io.File> filesToRemove = new ArrayList<java.io.File>();

  private Collection<File> filesToCheckout = new ArrayList<File>();

  private Collection<StarTeamFilePoint> filePointsToRemember = new ArrayList<StarTeamFilePoint>();

  private Collection<StarTeamChangeLogEntry> changes = new ArrayList<StarTeamChangeLogEntry>();

  public boolean hasChanges() {
      return !changes.isEmpty() ;
  }

  public Collection<java.io.File> getFilesToRemove() {
    return filesToRemove;
  }

  public void setFilesToRemove(Collection<java.io.File> filesToRemove) {
    this.filesToRemove = filesToRemove;
  }

  public Collection<File> getFilesToCheckout() {
    return filesToCheckout;
  }

  public void setFilesToCheckout(Collection<File> filesToCheckout) {
    this.filesToCheckout = filesToCheckout;
  }

  public void setFilePointsToRemember(Collection<StarTeamFilePoint> filePointsToRemember) {
    this.filePointsToRemember = filePointsToRemember;
  }

  public Collection<StarTeamFilePoint> getFilePointsToRemember() {
    return filePointsToRemember;
  }

  public boolean isComparisonAvailable() {
    return comparisonAvailable;
  }

  public void setComparisonAvailable(boolean comparisonAvailable) {
    this.comparisonAvailable = comparisonAvailable;
  }
  public void addChange(StarTeamChangeLogEntry value) {
	  changes.add(value);
  }

  public Collection<StarTeamChangeLogEntry> getChanges() {
	   return changes;
	}

  @Override
  public String toString() {
    final StringBuffer buffer = new StringBuffer();
    buffer.append( " changes: " ).append( changes.size() );
    return buffer.toString();
  }
}
