package com.unascribed.sup;

import java.util.Locale;

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
	 * Pretend use_envs is set to false.
	 */
	public static final boolean IGNORE_ENVS = Boolean.getBoolean("unsup.ignoreEnvs");
	
	/**
	 * Override the language rather than using the one detected by Java.
	 */
	public static final String LANGUAGE = System.getProperty("unsup.language");
	
	
	/**
	 * Force open the flavor selection dialog. Packwiz mode only.
	 */
	public static final boolean PACKWIZ_CHANGE_FLAVORS = Boolean.getBoolean("unsup.packwiz.changeFlavors");
	

	/**
	 * Wrap execution of the Puppet in this command.
	 */
	public static final String PUPPET_WRAPPER_COMMAND = System.getProperty("unsup.puppet.wrapperCommand");
	
	/**
	 * Set which mode the Puppet will use, or auto to automatically choose one.
	 */
	public static final PuppetMode PUPPET_MODE = PuppetMode.valueOf(System.getProperty("unsup.puppetMode", "auto").toUpperCase(Locale.ROOT));
	public enum PuppetMode {
		AUTO, SWING, OPENGL;
	}
	
	/**
	 * Set which platform the OpenGL Puppet will use, or auto to let GLFW choose.
	 */
	public static final PuppetPlatform PUPPET_PLATFORM = PuppetPlatform.valueOf(System.getProperty("unsup.puppet.opengl.platform", "auto").toUpperCase(Locale.ROOT));
	public enum PuppetPlatform {
		AUTO, WIN32, COCOA, WAYLAND, X11, NULL
	}
	
}
