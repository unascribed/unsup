package com.unascribed.sup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class Util {

	public static final String VERSION = Util.class.getPackage().getImplementationVersion() == null ? "DEV" : Util.class.getPackage().getImplementationVersion();

	/**
	 * Convert a string path into a URI, to perform proper escaping/etc.
	 * <p>
	 * Calling {@link URI#resolve(String)} will fail when the path contains characters that need
	 * escaping.
	 */
	public static URI uriOfPath(String path) throws URISyntaxException {
		return new URI(null, null, path, null);
	}
	
	public static void copy(InputStream from, OutputStream to) throws IOException {
		byte[] buf = new byte[16384];
		while (true) {
			int read = from.read(buf);
			if (read < 0) break;
			to.write(buf, 0, read);
		}
	}

}
