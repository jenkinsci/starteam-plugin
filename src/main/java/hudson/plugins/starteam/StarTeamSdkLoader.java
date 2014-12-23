package hudson.plugins.starteam;

import static hudson.Util.fixEmptyAndTrim;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

import org.apache.tools.ant.DirectoryScanner;

public class StarTeamSdkLoader {

	private static final Logger log = Logger.getLogger(StarTeamSdkLoader.class.getName());
	
	public static String getSdkEnvLocation() {
		String sdkEnv = System.getenv("SDK_LOCATION");
		if(fixEmptyAndTrim(sdkEnv)==null) return null;
		return sdkEnv;
	}
	
	public static String getSdkJarFromFolder(String folderPath) {
		DirectoryScanner ds = new DirectoryScanner();
		ds.setBasedir(folderPath);
		ds.setIncludes(new String[] {"starteam*.jar"});
		ds.setExcludes(new String[] {"starteam*-resources.jar","starteam*.bridgeTo.*.jar"});
		ds.scan();
		String[] matches = ds.getIncludedFiles();
		if(matches.length>0) {
			File file = new File(new File(folderPath),matches[0]);
			if(file.exists()) return file.getAbsolutePath();
		}
		return null;
	}

	public static List<String> checkSdkIsOnPath(String starTeamJarPath) throws ClassNotFoundException, IOException {
		if(new File(starTeamJarPath).isDirectory()) {
			String jarDirectory = starTeamJarPath;
			starTeamJarPath = getSdkJarFromFolder(jarDirectory);
			if(starTeamJarPath==null) throw new FileNotFoundException("No starteam*.jar found in "+jarDirectory);
		}
		if(!new File(starTeamJarPath).exists()) throw new FileNotFoundException(starTeamJarPath+" does not exist");
		URLClassLoader loader= new URLClassLoader(new URL[] {new URL("file:///"+starTeamJarPath)});
		loader.loadClass("com.starteam.Server");
		
		String loadLocation =  loader.getResource("com/starteam/Server.class").toString();
		Enumeration<URL> e = loader.getResources("com/starteam/Server.class");
		List<String> otherLocations = new ArrayList<String>();
		while(e.hasMoreElements()) {
			otherLocations.add(e.nextElement().toString());
		}
		otherLocations.remove(loadLocation); // ensure location used is first in list
		otherLocations.add(0, loadLocation);
		return otherLocations;
	}
	
	public static String getBestSdkLocation(String sdkPath) throws ClassNotFoundException, IOException {

		if (fixEmptyAndTrim(sdkPath) == null) {
			String sdkEnv = StarTeamSdkLoader.getSdkEnvLocation();
			if (fixEmptyAndTrim(sdkEnv) == null) {
				String starTeamJarPath = StarTeamSdkLoader.getSdkJarFromFolder(sdkEnv);
				if (starTeamJarPath != null) sdkPath = starTeamJarPath;
			}
		}
		if(sdkPath==null) return null;
		if(new File(sdkPath).isDirectory()) sdkPath = getSdkJarFromFolder(sdkPath);
		StarTeamSdkLoader.checkSdkIsOnPath(sdkPath);
		return sdkPath;
	}

	public static void loadSDK(String starTeamJarPath, ClassLoader cl) {
		try {
			starTeamJarPath = getBestSdkLocation(starTeamJarPath);
			if(starTeamJarPath!=null) {
				Class<?> c = Class.forName("jenkins.util.AntClassLoader");
				Method addURL = c.getDeclaredMethod("addPathFile", File.class);
				addURL.setAccessible(true);
				addURL.invoke(cl, new File(starTeamJarPath));
			}
			log.fine("loading starteam sdk from "+starTeamJarPath);
			cl.loadClass("com.starteam.Server");
			log.fine("sdk loaded successfully");
		} catch(Exception e) {
			throw new RuntimeException("failed to load StarTeam SDK from "+starTeamJarPath,e);
		}
		
	}
}
