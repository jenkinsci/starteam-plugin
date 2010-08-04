package hudson.plugins.starteam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;

public class StarTeamFilePointFunctions {

  // projection and collection conversion

  /**
 * @param collection Collection of StarTeam files
 * @return collection of full path file names 
 */
public static Collection<java.io.File> convertToFileCollection(final Collection<com.starbase.starteam.File> collection) {
    Collection<java.io.File> result = new ArrayList<java.io.File>();
    for (com.starbase.starteam.File f:collection) {
      result.add(new java.io.File(f.getFullName()));
    }

    return result;
  }

/**
 * @param collection Collection of StarTeam files
 * @return collection of FilePoints - information vector needed keeping track of file status 
 */
  public static Collection<StarTeamFilePoint> convertFilePointCollection(final Collection<com.starbase.starteam.File> collection) throws IOException {
    Collection<StarTeamFilePoint> result = new ArrayList<StarTeamFilePoint>();
    for (com.starbase.starteam.File f:collection) {
      result.add(new StarTeamFilePoint(f));
    }
    return result;
  }

  public static Collection<StarTeamFilePoint> extractFilePointSubCollection(final Map<java.io.File, StarTeamFilePoint> map, final Collection<java.io.File> collection) {
    Collection<StarTeamFilePoint> result = new ArrayList<StarTeamFilePoint>();
    for (java.io.File f:collection) {
      result.add(map.get(f));
    }
    return result;
  }

  public static Collection<com.starbase.starteam.File> extractFileSubCollection(final Map<java.io.File, com.starbase.starteam.File> map, final Collection<java.io.File> collection) {
    Collection<com.starbase.starteam.File> result = new ArrayList<com.starbase.starteam.File>();
    for (java.io.File f:collection) {
      result.add(map.get(f));
    }
    return result;
  }

  public static Map<java.io.File,StarTeamFilePoint> convertToFilePointMap(final Collection<StarTeamFilePoint> collection) {
    Map<java.io.File,StarTeamFilePoint> result = new HashMap<java.io.File,StarTeamFilePoint>();
    for (StarTeamFilePoint fp:collection) {
      result.put(fp.getFile(),fp);
    }
    return result;
  }

  public static Map<java.io.File,com.starbase.starteam.File> convertToFileMap(final Collection<com.starbase.starteam.File> collection) {
    Map<java.io.File,com.starbase.starteam.File> result = new TreeMap<java.io.File,com.starbase.starteam.File>();
    for (com.starbase.starteam.File f:collection) {
      result.put(new java.io.File(f.getFullName()),f);
    }
    return result;
  }

  // computation

  public static StarTeamChangeSet computeDifference(final Collection<StarTeamFilePoint> currentFilePoint, final Collection<StarTeamFilePoint> historicFilePoint, StarTeamChangeSet changeSet) {
	  final Map<java.io.File, StarTeamFilePoint> starteamFilePointMap = StarTeamFilePointFunctions.convertToFilePointMap(currentFilePoint);
	  Map<java.io.File, StarTeamFilePoint> historicFilePointMap = StarTeamFilePointFunctions.convertToFilePointMap(historicFilePoint);

	  final Set<java.io.File> starteamOnly = new HashSet<java.io.File>();
	  starteamOnly.addAll(starteamFilePointMap.keySet());
	  starteamOnly.removeAll(historicFilePointMap.keySet());

	  final Set<java.io.File> historicOnly = new HashSet<java.io.File>();
	  historicOnly.addAll(historicFilePointMap.keySet());
	  historicOnly.removeAll(starteamFilePointMap.keySet());

	  final Set<java.io.File> common = new HashSet<java.io.File>();
	  common.addAll(starteamFilePointMap.keySet());
	  common.removeAll(starteamOnly);

	  //    final Set<java.io.File> unchanged = new HashSet<java.io.File>();
	  final Set<java.io.File> higher = new HashSet<java.io.File>(); // newer revision
	  final Set<java.io.File> lower = new HashSet<java.io.File>(); // typically rollback of a revision
	  StarTeamChangeLogEntry change;

	  for (java.io.File f : common) {
		  StarTeamFilePoint starteam = starteamFilePointMap.get(f);
		  StarTeamFilePoint historic = historicFilePointMap.get(f);

		  if (starteam.getRevisionNumber() == historic.getRevisionNumber()) {
			  //unchanged.add(f);
			  continue;
		  }
		  if (starteam.getRevisionNumber() > historic.getRevisionNumber()) {
			  higher.add(f);
		  }
		  if (starteam.getRevisionNumber() < historic.getRevisionNumber()) {
			  lower.add(f);
		  }
		  change = new StarTeamChangeLogEntry(starteam.getFullfilepath(), starteam.getRevisionNumber(), new Date(), "", "", "changed");
		  changeSet.addeChange(change);
	  }

	  for (java.io.File f : historicOnly) {
		  StarTeamFilePoint historic = historicFilePointMap.get(f);
		  change = new StarTeamChangeLogEntry(historic.getFullfilepath(), historic.getRevisionNumber(), new Date(), "", "", "removed");
		  changeSet.addeChange(change);
	  }
	  for (java.io.File f : starteamOnly) {
		  StarTeamFilePoint starteam = starteamFilePointMap.get(f);
		  change = new StarTeamChangeLogEntry(starteam.getFullfilepath(), starteam.getRevisionNumber(), new Date(), "", "", "added");
		  changeSet.addeChange(change);
	  }
	  //    changeSet.setHigher(extractFilePointSubCollection(starteamFilePointMap,higher));
	  //    changeSet.setLower(annotate(extractFilePointSubCollection(historicFilePointMap,lower),starteamFilePointMap));
	  //    changeSet.setAdded(extractFilePointSubCollection(starteamFilePointMap,starteamOnly));
	  //    changeSet.setDelete(extractFilePointSubCollection(historicFilePointMap,historicOnly));

	  return changeSet;

  }

  private static Collection<StarTeamFilePoint> annotate(Collection<StarTeamFilePoint> starTeamFilePoints, Map<java.io.File, StarTeamFilePoint> starteamFilePointMap) {
    Map<java.io.File, StarTeamFilePoint> map = convertToFilePointMap(starTeamFilePoints);
    for (Map.Entry<java.io.File,StarTeamFilePoint> fp:map.entrySet()) {
      StarTeamFilePoint a = fp.getValue();
      StarTeamFilePoint b = starteamFilePointMap.get(fp.getKey());
//      a.setDate(b.getDate());
//      a.setMsg(b.getMsg());
//      a.setUsername(b.getUsername());
    }
    return starTeamFilePoints;
  }

  /** Recursive file system discovery
   * 
   * @param workspace a Hudson workspace directory
   * @return collection of files within workspace
   */
  public static Collection<java.io.File> listAllFiles(final java.io.File workspace) {
    Collection<java.io.File> result = new ArrayList<java.io.File>();
    listAllFiles(result,workspace.getAbsoluteFile());
    return result;
  }

  private static void listAllFiles(final Collection<java.io.File> result, final java.io.File dir) {
    List<java.io.File> sub = new ArrayList<java.io.File>();
    java.io.File[] files = dir.listFiles();
    if (files != null) {
    	for (java.io.File f:files) {
    		if (f.isFile()) {
    			result.add(f);
    		} else
    			if (f.isDirectory()) {
    				sub.add(f);
    			}
    	}
    	for (java.io.File f:sub) {
    		listAllFiles(result,f);
    	}
    } else {
		if (dir.isFile()) {
			result.add(dir);
		}
    }
  }

  // storage

  public static Collection<StarTeamFilePoint> loadCollection(final java.io.File file) throws IOException {
    Collection<String> stringCollection = FileUtils.readLines(file,"ISO-8859-1");
    Collection<StarTeamFilePoint> result = new ArrayList<StarTeamFilePoint>();
    for (String str:stringCollection) {

      int pos = str.indexOf(',');

      String revision = str.substring(0,pos);
      String path = str.substring(pos+1);

      StarTeamFilePoint f = new StarTeamFilePoint(path,Integer.parseInt(revision));
//      f.setFullfilepath(path);
//      f.setFileName(new java.io.File(path).getName());
//      f.setRevisionNumber(Integer.parseInt(revision));

      result.add(f);
    }
    FileUtils.writeLines(file,"ISO-8859-1",stringCollection);

    return result;
  }

  public static void storeCollection(final java.io.File file, final Collection<StarTeamFilePoint> collection) throws IOException {
    Collection<String> stringCollection = new ArrayList<String>();
    for (StarTeamFilePoint i:collection) {
      stringCollection.add(i.getRevisionNumber()+","+i.getFullfilepath());
    }
    FileUtils.writeLines(file,"ISO-8859-1",stringCollection);
  }

}
