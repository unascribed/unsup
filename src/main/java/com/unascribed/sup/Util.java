package com.unascribed.sup;

import java.net.URI;
import java.net.URISyntaxException;

public class Util {

	public static final String VERSION = noinline("${version}");
	
	private static String noinline(String s) {
		return s;
	}

	/**
	 * Convert a string path into a URI, to perform proper escaping/etc.
	 * <p>
	 * Calling {@link URI#resolve(String)} will fail when the path contains characters that need
	 * escaping.
	 */
	public static URI uriOfPath(String path) throws URISyntaxException {
		return new URI(null, null, path, null);
	}

}
