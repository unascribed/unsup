package com.unascribed.sup;

import java.util.Locale;

public class SysProps {

	public enum PuppetMode {
		AUTO, SWING, OPENGL;
	}
	
	/**
	 * Enable verbose log output and disable some "friendly" output options.
	 */
	public static final boolean DEBUG = Boolean.getBoolean("unsup.debug");
	/**
	 * Use the Puppet even in standalone mode.
	 */
	public static final boolean GUI_IN_STANDALONE = Boolean.getBoolean("unsup.guiInStandalone");
	/**
	 * Assume yes to all overwrite/reconciliation queries.
	 */
	public static final boolean DISABLE_RECONCILIATION = Boolean.getBoolean("unsup.disableReconciliation");
	/**
	 * Set which mode the Puppet will use, or AUTO to automatically choose one.
	 */
	public static final PuppetMode PUPPET_MODE = PuppetMode.valueOf(System.getProperty("unsup.puppetMode", "auto").toUpperCase(Locale.ROOT));
	
	
	/**
	 * Force open the flavor selection dialog. Packwiz mode only.
	 */
	public static final boolean PACKWIZ_CHANGE_FLAVORS = Boolean.getBoolean("unsup.packwiz.changeFlavors");
	
}
