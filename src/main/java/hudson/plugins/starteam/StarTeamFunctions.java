package hudson.plugins.starteam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.starteam.File;
import com.starteam.Folder;
import com.starteam.LiveObject;
import com.starteam.PropertyCollection;
import com.starteam.Server;
import com.starteam.Type;
import com.starteam.TypedResource;
import com.starteam.View;
import com.starteam.ViewMember;
import com.starteam.ViewMemberCollection;

public class StarTeamFunctions {

	public static Collection<File> listAllFiles(Map<String, Folder> rootFolderMap, java.io.File workspace) {
		Collection<File> result = new ArrayList<File>();

		for (Map.Entry<String, Folder> f : rootFolderMap.entrySet()) {
			result.addAll(listAllFiles(f.getValue(), workspace));
		}

		return result;
	}

	public static Collection<File> listAllFiles(Folder rootFolder, java.io.File workspace) {
		Collection<File> result = new ArrayList<File>();
		// set root folder
		String alternatePath = rootFolder.getAlternatePathFragment();
		if (alternatePath == null) {
			alternatePath = "";
		}
		java.io.File actualPlace = new java.io.File(workspace, alternatePath);
		rootFolder.setAlternatePathFragment(actualPlace.getAbsolutePath());

		// Get a list of all files
		listAllFiles(result, rootFolder);

		return result;
	}

	private static void listAllFiles(Collection<File> result, Folder folder) {
		File.Type fileType = folder.getView().getServer().getTypes().FILE;
		folder.getView().findItem(fileType, -1);
		ViewMemberCollection allFilesAllDesc = new ViewMemberCollection();

		folder.refreshItems(fileType, new PropertyCollection(fileType.getProperties().find(TypedResource.Type.IDProperty.NAME)), -1);
		allFilesAllDesc.addAll(folder.getItems(fileType));
		List<Folder> fldrs = getDescendantFolders(new ArrayList<Folder>(), folder);
		for (int i = 0; i < fldrs.size(); i++) {
			allFilesAllDesc.addAll(((Folder) fldrs.get(i)).getItems(fileType));
		}

		populateFileProperties(folder.getView().getServer(), allFilesAllDesc);

		for (int i = 0; i < allFilesAllDesc.size(); i++) {
			result.add((File) allFilesAllDesc.getAt(i));
		}
	}

	public static Map<java.io.File, File> convertToFileMap(final Collection<File> starteamFiles) {
		Map<java.io.File, File> result = new TreeMap<java.io.File, File>();
		for (File f : starteamFiles) {
			result.put(new java.io.File(f.getFullName()), f);
		}
		return result;
	}

	private static List<Folder> getDescendantFolders(List<Folder> l, Folder f) {
		l.add(f);
		Folder[] kids = f.getSubFolders();
		for (int i = 0; i < kids.length; i++)
			getDescendantFolders(l, kids[i]);
		return l;
	}

	private static void populateFileProperties(Server server, ViewMemberCollection vmc) {
		Type typ = server.getTypes().FILE;
		PropertyCollection pc = new PropertyCollection();
		pc.add(typ.getProperties().find(LiveObject.Type.NameProperty.NAME));
		pc.add(typ.getProperties().find(ViewMember.Type.RootObjectIDProperty.NAME));
		pc.add(typ.getProperties().find(File.Type.ContentVersionProperty.NAME));
		pc.add(typ.getProperties().find(TypedResource.Type.IDProperty.NAME));
		pc.add(typ.getProperties().find(com.starteam.VersionedObject.Type.RevisionNumberProperty.NAME));
		pc.add(typ.getProperties().find(ViewMember.Type.DotNotationProperty.NAME));
		pc.add(typ.getProperties().find(com.starteam.TrackedObject.Type.ModifiedByProperty.NAME));
		pc.add(typ.getProperties().find(com.starteam.TrackedObject.Type.ModifiedTimeProperty.NAME));
		pc.add(typ.getProperties().find(LiveObject.Type.CreatedTimeProperty.NAME));
		pc.add(typ.getProperties().find(ViewMember.Type.CommentProperty.NAME));
		pc.add(typ.getProperties().find(File.Type.MD5Property.NAME));
		vmc.getCache().populate(pc);
	}

	/**
	 * Find the given folder in the given view.
	 *
	 * @param view
	 *            The view to look in.
	 * @param folderPath
	 *            The root folder-relative path of the folder to look for.
	 *            An empty string signifies the root folder of the view.
	 * @return The star team folder with the given folder path
	 * @throws StarTeamSCMException if the folder cannot be found
	 */
	public static Folder findFolderInView(final View view, final String folderPath)
			throws StarTeamSCMException {
		// Check the root folder of the view
		// leaving folder name blank will get the root view
		if (folderPath.equals("")) {
			return view.getRootFolder();
		}
		
		Folder folder = findFolderWithPathInView(view, folderPath);

		return folder;
	}

	private static Folder findFolderWithPathInView(final View view,
			final String folderPath) throws StarTeamSCMException {
		String[] folders = replaceSeparators(folderPath).split("/");
		Folder stFolder = view.getRootFolder();
		
		for(String folder : folders){
			for(Folder stChildFolder : stFolder.getSubFolders()){
				if(folder.equals(stChildFolder.getName())){
					stFolder = stChildFolder;
					break;
				}
			}
		}
		checkCorrectFolderFound(view, folderPath, stFolder);
		return stFolder;
	}

	private static void checkCorrectFolderFound(final View view,
			final String folderPath, Folder stFolder)
			throws StarTeamSCMException {
		String stFolderHierarchy = replaceSeparators(stFolder.getFolderHierarchy());
		String userEnteredFolderPath = view.getRootFolder().getName()+"/"+replaceSeparators(folderPath)+"/";
		if(!userEnteredFolderPath.equals(stFolderHierarchy)){
			throw new StarTeamSCMException("Couldn't find folder " + folderPath
					+ " in view " + view.getName());
		}
	}

	private static String replaceSeparators(String folderName) {
		if(folderName.contains("\\")){
			folderName = folderName.replace("\\", "/");
		}
		return folderName;
	}
}
