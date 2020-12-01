package com.unascribed.sup;

/**
 * Post-load API for accessing unsup data from within the launched program.
 */
public class Unsup {

	/**
	 * The version of unsup responsible for updating on this launch.
	 */
	public static final String UNSUP_VERSION = Util.VERSION;
	
	/**
	 * The last version of the source to be synced to the working directory by unsup.
	 */
	public static final String SOURCE_VERSION = Agent.sourceVersion;
	
	/**
	 * {@code true} if unsup downloaded updates this launch.
	 */
	public static final boolean UPDATED = Agent.updated;
	
}
