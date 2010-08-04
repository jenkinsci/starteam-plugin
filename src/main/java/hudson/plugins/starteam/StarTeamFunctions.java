package hudson.plugins.starteam;

import com.starbase.starteam.Folder;
import com.starbase.starteam.View;
import com.starbase.starteam.File;
import com.starbase.starteam.Item;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;

public class StarTeamFunctions {

	/**
	 * Find the given folder in the given view.
	 *
	 * @param view
	 *            The view to look in.
	 * @param foldername
	 *            The view-relative path of the folder to look for.
	 * @return The folder or null if a folder by the given name was not found.
	 * @throws StarTeamSCMException
	 */
	public static Folder findFolderInView(final View view, final String foldername)
			throws StarTeamSCMException {
		// Check the root folder of the view
		if (view.getName().equalsIgnoreCase(foldername)) {
			return view.getRootFolder();
		}

		// Create a File object with the folder name for system-
		// independent matching
		java.io.File thefolder = new java.io.File(foldername.toLowerCase());

		// Search for the folder in subfolders
		Folder result = findFolderInView(view.getRootFolder(), thefolder);
		if (result == null) {
			throw new StarTeamSCMException("Couldn't find folder " + foldername
					+ " in view " + view.getName());
		}
		return result;
	}

	/**
	 * Do a breadth-first search for a folder with the given name, starting with
	 * children of the provided folder.
	 *
	 * @param folder
	 *            the folder whose children to check
	 * @param thefolder
	 *            the folder to look for
	 * @return
	 */
	private static Folder findFolderInView(Folder folder, java.io.File thefolder) {
		// Check subfolders, breadth first. checkLater is a collection
		// of folders that didn't match, therefore their children
		// will be checked next.
		Collection<Folder> checkLater = new ArrayList<Folder>();
		for (Folder f : folder.getSubFolders()) {
			// Compare pathnames. The getFolderHierarchy call returns
			// the full folder name (including root folder name which
			// is the same as the view name) terminated by the
			// platform-specific separator.
			if (f.getFolderHierarchy().equalsIgnoreCase(
					thefolder.getPath() + java.io.File.separator)) {
				return f;
			} else {
				// add to list of folders whose children will be checked
				checkLater.add(f);
			}
		}
		// recurse unto children
		for (Folder f : checkLater) {
			Folder result = findFolderInView(f, thefolder);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

  public static Collection<File> listAllFiles(Map<String,Folder> rootFolderMap, java.io.File workspace) {
		Collection<File> result = new ArrayList<File>();

    for (Map.Entry<String,Folder> f:rootFolderMap.entrySet()) {
      result.addAll(listAllFiles(f.getValue(),workspace));
    }

		return result;
	}

  public static Collection<File> listAllFiles(Folder rootFolder, java.io.File workspace) {
		Collection<File> result = new ArrayList<File>();
    // set root folder
		String alternatePath = rootFolder.getAlternatePathFragment();
		if (alternatePath == null)
		{
			alternatePath = "";
		}
		java.io.File actualPlace = new java.io.File(workspace,alternatePath);
		rootFolder.setAlternatePathFragment(actualPlace.getAbsolutePath());

		// Get a list of all files
		listAllFiles(result, rootFolder);

		return result;
	}

  private static void listAllFiles(Collection<File> result, Folder folder) {
    for (Folder f : folder.getSubFolders()) {
      listAllFiles(result, f);
    }
    // find items in this folder
    for (Item i : folder.getItems(folder.getView().getProject().getServer()
        .getTypeNames().FILE)) {
      File f = (com.starbase.starteam.File) i;
      try {
        // This sometimes throws... deep inside starteam =(
        result.add(f);
      } catch (RuntimeException e) {
        //todo logger.println("Exception in listAllFiles: "
        // + e.getLocalizedMessage());
      }
    }
    folder.discard();
  }


  public static Map<String,String> splitCsvString(String multiplefolder) {
    Map<String,String> folderMap = new HashMap<String,String>();
    if (multiplefolder != null) {
      for (String folderLine:multiplefolder.split("\n")) {
        String folderLineNullable = StringUtils.trimToNull(folderLine);
        if (folderLineNullable != null) {
          String[] starteamWorkspace = folderLineNullable.split(",");
          String starteamFolder = starteamWorkspace.length>0?StringUtils.trimToNull(starteamWorkspace[0]):null;
          String workspacePath = starteamWorkspace.length>1?StringUtils.trimToNull(starteamWorkspace[1]):null;
          if (workspacePath == null) {
            workspacePath = ".";
          }
          if (starteamFolder != null && workspacePath != null) {
            folderMap.put(starteamFolder,workspacePath);
          }
        }
      }
    }
    return folderMap;
  }

public static Map<java.io.File,com.starbase.starteam.File> convertToFileMap(final Collection<com.starbase.starteam.File> collection) {
    Map<java.io.File,com.starbase.starteam.File> result = new TreeMap<java.io.File,com.starbase.starteam.File>();
    for (com.starbase.starteam.File f:collection) {
      result.put(new java.io.File(f.getFullName()),f);
    }
    return result;
  }

}
