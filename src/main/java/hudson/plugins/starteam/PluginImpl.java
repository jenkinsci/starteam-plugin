package hudson.plugins.starteam;

import hudson.Plugin;

/**
 * adapt to last hudon's api - remove access to SCMS, use of Extension annotation instead.
 * 
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 */
public class PluginImpl extends Plugin {
	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.Plugin#start()
	 */
	@Override
	public void start() throws Exception {
		//nothing to do with new extension functionality.
	}

}
