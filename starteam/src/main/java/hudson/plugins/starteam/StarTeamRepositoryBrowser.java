/**
 * 
 */
package hudson.plugins.starteam;

import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.scm.ChangeLogSet.Entry;

import java.io.IOException;
import java.net.URL;

/**
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @TODO implement maybe?
 */
public abstract class StarTeamRepositoryBrowser extends RepositoryBrowser {

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.RepositoryBrowser#getChangeSetLink(hudson.scm.ChangeLogSet.Entry)
	 */
	@Override
	public URL getChangeSetLink(Entry changeSet) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.model.Describable#getDescriptor()
	 */
	public Descriptor getDescriptor() {
		// TODO Auto-generated method stub
		return null;
	}

}
