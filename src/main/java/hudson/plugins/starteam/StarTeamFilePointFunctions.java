package hudson.plugins.starteam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

/**
 * Functions operating on StarTeamFilePoint type.
 */

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

  @SuppressWarnings("unchecked")
  public static Collection<StarTeamFilePoint> loadCollection(final java.io.File file) throws IOException {
    Collection<String> stringCollection = FileUtils.readLines(file,"ISO-8859-1");
    Collection<StarTeamFilePoint> result = new ArrayList<StarTeamFilePoint>();
    for (String str:stringCollection) {

      int pos = str.indexOf(',');

      String revision = str.substring(0,pos);
      String path = str.substring(pos+1);

      StarTeamFilePoint f = new StarTeamFilePoint(path,Integer.parseInt(revision));

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
