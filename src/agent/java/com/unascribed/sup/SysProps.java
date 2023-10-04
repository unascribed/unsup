package com.unascribed.sup;

public class SysProps {

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
	 * Force open the flavor selection dialog. Packwiz mode only.
	 */
	public static final boolean PACKWIZ_CHANGE_FLAVORS = Boolean.getBoolean("unsup.packwiz.changeFlavors");
	
}
