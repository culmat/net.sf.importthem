package net.sf.importthem.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import net.sf.importthem.Activator;

/**
 * Class used to initialize default preference values.
 */
public class PrefInitializer extends AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(PrefConstants.GROOVY_SCRIPT,
				"import static net.sf.importthem.handlers.Util.*\nseekProjects(projectMap,selection)");
	}

}
