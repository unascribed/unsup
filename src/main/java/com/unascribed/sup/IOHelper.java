package com.unascribed.sup;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongConsumer;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.moandjiezana.toml.Toml;
import net.i2p.crypto.eddsa.EdDSAEngine;
import okhttp3.Request;
import okhttp3.Response;

class IOHelper {
	
	protected static final int K = 1024;
	protected static final int M = K*1024;
	
	protected static final long ONE_SECOND_IN_NANOS = TimeUnit.SECONDS.toNanos(1);
	
	protected static String checkSchemeMismatch(URL src, String url) throws MalformedURLException {
		if (url == null) return null;
		URL parsed = new URL(url);
		boolean ok = false;
		if (src.getProtocol().equals("file")) {
			// promoting from file to http is ok, as well as using files from files
			ok = "http".equals(parsed.getProtocol()) || "https".equals(parsed.getProtocol())
					|| "file".equals(parsed.getProtocol());
		} else if ("http".equals(src.getProtocol()) || "https".equals(src.getProtocol())) {
			// going between http and https is ok
			ok = "http".equals(parsed.getProtocol()) || "https".equals(parsed.getProtocol());
		}
		if (!ok) {
			Agent.log("WARN", "Ignoring custom URL with bad scheme "+parsed.getProtocol());
		}
		return ok ? url : null;
	}
	
	protected static byte[] loadAndVerify(URL src, int sizeLimit, URL sigUrl) throws IOException {
		byte[] resp = downloadToMemory(src, sizeLimit);
		if (resp == null) {
			throw new IOException(src+" is larger than "+(sizeLimit/K)+"K, refusing to continue downloading");
		}
		if (Agent.publicKey != null && sigUrl != null) {
			try {
				byte[] sigResp = downloadToMemory(sigUrl, 512);
				EdDSAEngine engine = new EdDSAEngine();
				engine.initVerify(Agent.publicKey);
				if (!engine.verifyOneShot(resp, sigResp)) {
					throw new SignatureException("Signature is invalid");
				}
			} catch (Throwable t) {
				throw new IOException("Failed to validate signature for "+src, t);
			}
		}
		return resp;
	}

	protected static byte[] downloadToMemory(URL url, int sizeLimit) throws IOException {
		return withRetries(10, () -> {
			try {
				InputStream conn = get(url);
				byte[] resp = Util.collectLimited(conn, sizeLimit);
				return resp;
			} catch (SocketTimeoutException e) {
				throw new Retry("Connection to "+url.getHost()+" timed out",
						SocketTimeoutException::new);
			}
		});
	}
	
	private static String currentFirefoxVersion;
	private static final Set<String> alwaysHostile = new HashSet<>(Arrays.asList(Util.b64Str("YmV0YS5jdXJzZWZvcmdlLmNvbXx3d3cuY3Vyc2Vmb3JnZS5jb218Y3Vyc2Vmb3JnZS5jb218bWluZWNyYWZ0LmN1cnNlZm9yZ2UuY29tfG1lZGlhZmlsZXouZm9yZ2VjZG4ubmV0fG1lZGlhZmlsZXMuZm9yZ2VjZG4ubmV0fGZvcmdlY2RuLm5ldHxlZGdlLmZvcmdlY2RuLm5ldA==").split("\\|")));

	protected static InputStream get(URL url) throws IOException {
		return get(url, false);
	}

	protected static InputStream get(URL url, boolean hostile) throws IOException {
		if ("file".equals(url.getProtocol())) {
			try {
				return new FileInputStream(new File(url.toURI()));
			} catch (URISyntaxException e) {
				throw new IOException(e);
			}
		}
		if (!hostile && alwaysHostile.contains(url.getHost())) {
			hostile = true;
		}
		if (hostile && currentFirefoxVersion == null) {
			try {
				JsonObject data = loadJson(new URL("https://product-details.mozilla.org/1.0/firefox_versions.json"), 4*K, null);
				currentFirefoxVersion = data.getString("LATEST_FIREFOX_VERSION");
			} catch (Throwable t) {
				currentFirefoxVersion = "109.0";
			}
			int firstDot = currentFirefoxVersion.indexOf('.');
			if (firstDot != -1) {
				int nextDot = currentFirefoxVersion.indexOf('.', firstDot+1);
				if (nextDot != -1) {
					// the UA only has the MAJOR.MINOR, no PATCH
					currentFirefoxVersion = currentFirefoxVersion.substring(0, nextDot);
				}
			}
		}
		final boolean fhostile = hostile;
		return IOHelper.withRetries(10, () -> {
			try {
				Request.Builder reqbldr = new Request.Builder()
						.url(url)
						.header("User-Agent",
								// not a mistake. the rv: is locked at 109 (and the trailer still updates, yes. it's weird.)
								fhostile ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/"+currentFirefoxVersion
								        : "unsup/"+Util.VERSION+" (+https://git.sleeping.town/unascribed/unsup)"
							);
				if (fhostile) {
					reqbldr.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
					reqbldr.header("Accept-Encoding", "gzip, deflate, br");
					reqbldr.header("Accept-Language", "en-US,en;q=0.5");
					reqbldr.header("Sec-Fetch-Dest", "document");
					reqbldr.header("Sec-Fetch-Mode", "navigate");
					reqbldr.header("Sec-Fetch-Site", "same-origin");
					reqbldr.header("Sec-Fetch-User", "?1");
					reqbldr.header("TE", "trailers");
				}
				Response res = Agent.okhttp.newCall(reqbldr.build()).execute();
				if (res.code() != 200) {
					if (res.code() == 404 || res.code() == 410) throw new FileNotFoundException(url.toString());
					byte[] b = Util.collectLimited(res.body().byteStream(), 512);
					String s = b == null ? "(response too long)" : new String(b, StandardCharsets.UTF_8);
					res.close();
					if (res.code()/100 == 500) {
						throw new Retry(url.getHost()+" responded with a server error for "+url+" ("+res.code()+")",
								new IOException("Received non-200 response from server for "+url+": "+res.code()+"\n"+s));
					} else {
						throw new IOException("Received non-200 response from server for "+url+": "+res.code()+"\n"+s);
					}
				}
				return res.body().byteStream();
			} catch (InterruptedIOException e) {
				throw new Retry("Connection to "+url.getHost()+" timed out",
						e);
			} catch (UnknownHostException e) {
				throw new Retry("DNS resolution of "+url.getHost()+" failed",
						e);
			}
		});
	}
	
	protected static final class Retry extends Exception {
		public Retry(String msg, Function<String, Throwable> ifCantRetry) {
			this(msg, ifCantRetry.apply(msg));
		}
		public Retry(String msg, Throwable ifCantRetry) {
			super(msg, ifCantRetry);
		}
	}
	
	protected interface RetryCallable<T, E extends Throwable> {
		T call() throws E, Retry;
	}
	
	protected static <T, E extends Throwable> T withRetries(int tries, RetryCallable<T, E> call) throws E {
		int secs = 1;
		while (true) {
			boolean canRetry = tries > 0;
			tries--;
			try {
				return call.call();
			} catch (Retry r) {
				if (canRetry) {
					Agent.log("WARN", r.getMessage()+". Trying again in "+secs+" second"+(secs == 1 ? "" : "s")
							+", "+(tries == 0 ? "final retry" : tries+" retr"+(tries == 1 ? "y" : "ies")+" left"));
					try {
						TimeUnit.SECONDS.sleep(secs);
					} catch (InterruptedException ignore) {}
					secs += (secs+2)/3;
				} else {
					throw (E)r.getCause();
				}
			}
		}
	}
	
	protected static String loadString(URL src, int sizeLimit, URL sigUrl) throws IOException {
		return new String(loadAndVerify(src, sizeLimit, sigUrl), StandardCharsets.UTF_8);
	}
	
	protected static JsonObject loadJson(URL src, int sizeLimit, URL sigUrl) throws IOException, JsonParserException {
		return JsonParser.object().from(new ByteArrayInputStream(loadAndVerify(src, sizeLimit, sigUrl)));
	}
	
	protected static Toml loadToml(URL src, int sizeLimit, URL sigUrl) throws IOException {
		return new Toml().read(new ByteArrayInputStream(loadAndVerify(src, sizeLimit, sigUrl)));
	}
	
	protected static Toml loadToml(URL src, int sizeLimit, HashFunction func, String expectedHash) throws IOException {
		byte[] data = downloadToMemory(src, sizeLimit);
		if (data == null) throw new IOException("Size limit of "+(sizeLimit/K)+"K for "+src+" exceeded");
		String hash = Util.toHexString(func.createMessageDigest().digest(data));
		if (!hash.equals(expectedHash))
			throw new IOException("Expected "+expectedHash+" from "+src+", but got "+hash);
		return new Toml().read(new ByteArrayInputStream(data));
	}
	
	protected static class DownloadedFile {
		/** null if no hash function was specified */
		public final String hash;
		public final File file;
		protected DownloadedFile(String hash, File file) {
			this.hash = hash;
			this.file = file;
		}
	}
	
	protected static DownloadedFile downloadToFile(URL url, File dir, long size, LongConsumer addProgress, Runnable updateProgress, HashFunction hashFunc, boolean hostile) throws IOException {
		File file = File.createTempFile("download", "", dir);
		Agent.cleanup.add(file::delete);
		return withRetries(10, () -> {
			try {
				long readTotal = 0;
				long lastProgressUpdate = 0;
				MessageDigest digest = hashFunc == null ? null : hashFunc.createMessageDigest();
				try (InputStream in = get(url, hostile)) {
					byte[] buf = new byte[16384];
					try (FileOutputStream out = new FileOutputStream(file)) {
						while (true) {
							int read = in.read(buf);
							if (read == -1) break;
							readTotal += read;
							if (size != -1 && readTotal > size) throw new IOException("Overread; expected "+size+" bytes, but got at least "+readTotal);
							out.write(buf, 0, read);
							if (digest != null) digest.update(buf, 0, read);
							if (addProgress != null) addProgress.accept(read);
							if (updateProgress != null && System.nanoTime()-lastProgressUpdate > ONE_SECOND_IN_NANOS/30) {
								lastProgressUpdate = System.nanoTime();
								updateProgress.run();
							}
						}
					}
				} catch (InterruptedIOException e) {
					if (addProgress != null) addProgress.accept(-readTotal);
					if (updateProgress != null) updateProgress.run();
					throw e;
				}
				if (size != -1 && readTotal != size) {
					throw new IOException("Underread; expected "+size+" bytes, but only got "+readTotal);
				}
				if (updateProgress != null) {
					updateProgress.run();
				}
				String hash = null;
				if (digest != null) {
					hash = Util.toHexString(digest.digest());
				}
				return new DownloadedFile(hash, file);
			} catch (InterruptedIOException e) {
				throw new Retry("Connection to "+url.getHost()+" timed out",
						e);
			}
		});
	}
	
	protected static String hash(HashFunction func, File f) throws IOException {
		MessageDigest digest = func.createMessageDigest();
		byte[] buf = new byte[16384];
		try (FileInputStream in = new FileInputStream(f)) {
			while (true) {
				int read = in.read(buf);
				if (read == -1) break;
				digest.update(buf, 0, read);
			}
		}
		return Util.toHexString(digest.digest());
	}

}
