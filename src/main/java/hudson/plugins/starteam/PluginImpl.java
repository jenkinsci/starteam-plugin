package hudson.plugins.starteam;

import hudson.Plugin;
import hudson.scm.SCMS;

/**
 * @author Ilkka Laukkanen
 * @plugin
 */
public class PluginImpl extends Plugin {
	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.Plugin#start()
	 */
	@Override
	public void start() throws Exception {
		// Add to scm plugins
		SCMS.SCMS.add(StarTeamSCM.DESCRIPTOR);
	}

}
