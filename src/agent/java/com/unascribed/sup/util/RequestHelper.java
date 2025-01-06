package com.unascribed.sup.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.ConnectException;
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

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.moandjiezana.toml.Toml;
import com.unascribed.sup.Agent;
import com.unascribed.sup.Log;
import com.unascribed.sup.Util;
import com.unascribed.sup.data.HashFunction;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class RequestHelper {
	
	public static final int K = 1024;
	public static final int M = K*1024;
	
	public static final long ONE_SECOND_IN_NANOS = TimeUnit.SECONDS.toNanos(1);
	
	public static String checkSchemeMismatch(URI src, String url) throws URISyntaxException {
		if (url == null) return null;
		URI parsed = new URI(url);
		boolean ok = false;
		if (src.getScheme().equals("file")) {
			// promoting from file to http is ok, as well as using files from files
			ok = "http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme())
					|| "file".equals(parsed.getScheme());
		} else if ("http".equals(src.getScheme()) || "https".equals(src.getScheme())) {
			// going between http and https is ok
			ok = "http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme());
		}
		if (!ok) {
			Log.warn("Ignoring custom URL with bad scheme "+parsed.getScheme());
		}
		return ok ? url : null;
	}
	
	public static byte[] loadAndVerify(URI src, int sizeLimit, URI sigUrl) throws IOException {
		byte[] resp = downloadToMemory(src, sizeLimit);
		if (resp == null) {
			throw new IOException(src+" is larger than "+(sizeLimit/K)+"K, refusing to continue downloading");
		}
		if (Agent.packSig != null && sigUrl != null) {
			try {
				byte[] sigResp = downloadToMemory(sigUrl, 512);
				if (!Agent.packSig.verify(resp, sigResp)) {
					throw new SignatureException("Signature is invalid");
				} else {
					Log.debug("Signature for "+src+" (retrieved from "+sigUrl+") is valid");
				}
			} catch (Throwable t) {
				throw new IOException("Failed to validate signature for "+src, t);
			}
		}
		return resp;
	}

	public static byte[] downloadToMemory(URI url, int sizeLimit) throws IOException {
		return withRetries(10, () -> {
			try {
				InputStream conn = get(url);
				byte[] resp = RequestHelper.collectLimited(conn, sizeLimit);
				return resp;
			} catch (SocketTimeoutException e) {
				throw new Retry("Connection to "+url.getHost()+" timed out",
						SocketTimeoutException::new);
			} catch (ConnectException e) {
				throw new Retry("Connection to "+url.getHost()+" failed",
						ConnectException::new);
			}
		});
	}
	
	private static String currentFirefoxVersion;
	private static final Set<String> alwaysHostile = new HashSet<>(Arrays.asList(Bases.b64ToString("YmV0YS5jdXJzZWZvcmdlLmNvbXx3d3cuY3Vyc2Vmb3JnZS5jb218Y3Vyc2Vmb3JnZS5jb218bWluZWNyYWZ0LmN1cnNlZm9yZ2UuY29tfG1lZGlhZmlsZXouZm9yZ2VjZG4ubmV0fG1lZGlhZmlsZXMuZm9yZ2VjZG4ubmV0fGZvcmdlY2RuLm5ldHxlZGdlLmZvcmdlY2RuLm5ldA==").split("\\|")));

	public static InputStream get(URI url) throws IOException {
		return get(url, false);
	}

	public static InputStream get(URI url, boolean hostile) throws IOException {
		if ("file".equals(url.getScheme())) {
			return new FileInputStream(new File(url));
		}
		if (!hostile && alwaysHostile.contains(url.getHost())) {
			hostile = true;
		}
		if (hostile && currentFirefoxVersion == null) {
			try {
				JsonObject data = loadJson(new URI("https://product-details.mozilla.org/1.0/firefox_versions.json"), 4*K, null);
				currentFirefoxVersion = data.getString("LATEST_FIREFOX_VERSION");
			} catch (Throwable t) {
				currentFirefoxVersion = "133.0";
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
		return RequestHelper.withRetries(10, () -> {
			try {
				Request.Builder reqbldr = new Request.Builder()
						.url(HttpUrl.get(url))
						.header("User-Agent",
								fhostile ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:"+currentFirefoxVersion+") Gecko/20100101 Firefox/"+currentFirefoxVersion
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
					byte[] b = RequestHelper.collectLimited(res.body().byteStream(), 512);
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
			} catch (ConnectException e) {
				throw new Retry("Connection to "+url.getHost()+" failed",
						ConnectException::new);
			} catch (SSLHandshakeException e) {
				if (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains(" path building failed ")) {
					throw new Retry(url.getHost()+" has an invalid TLS certificate — incorrect system time or broken antivirus?",
						e);
				}
				throw new IOException("Failed to retrieve "+url, e);
			} catch (SSLException e) {
				if (e.getMessage() != null && e.getMessage().contains(" unrecognized ")) {
					throw new Retry(url.getHost()+" violated TLS protocol — weird VPN or parental controls?",
						e);
				}
				throw new IOException("Failed to retrieve "+url, e);
			} catch (FileNotFoundException e) {
				throw e;
			} catch (IOException e) {
				if (e.getMessage() != null && e.getMessage().contains(" preface ")) {
					throw new Retry(url.getHost()+" violated HTTP/2 protocol — weird VPN?",
						e);
				}
				throw new IOException("Failed to retrieve "+url, e);
			}
		});
	}
	
	public static final class Retry extends Exception {
		public Retry(String msg, Function<String, Throwable> ifCantRetry) {
			this(msg, ifCantRetry.apply(msg));
		}
		public Retry(String msg, Throwable ifCantRetry) {
			super(msg, ifCantRetry);
		}
	}
	
	public interface RetryCallable<T, E extends Throwable> {
		T call() throws E, Retry;
	}
	
	@SuppressWarnings("unchecked")
	public static <T, E extends Throwable> T withRetries(int tries, RetryCallable<T, E> call) throws E {
		int secs = 1;
		while (true) {
			boolean canRetry = tries > 0;
			tries--;
			try {
				return call.call();
			} catch (Retry r) {
				if (canRetry) {
					Log.warn(r.getMessage()+". Trying again in "+secs+" second"+(secs == 1 ? "" : "s")
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
	
	public static String loadString(URI src, int sizeLimit, URI sigUrl) throws IOException {
		return new String(loadAndVerify(src, sizeLimit, sigUrl), StandardCharsets.UTF_8);
	}
	
	public static JsonObject loadJson(URI src, int sizeLimit, URI sigUrl) throws IOException, JsonParserException {
		try {
			return JsonParser.object().from(new ByteArrayInputStream(loadAndVerify(src, sizeLimit, sigUrl)));
		} catch (JsonParserException e) {
			throw new IOException("Failed to parse "+src+" as JSON: "+e.getMessage()+" (at line "+e.getLinePosition()+" column "+e.getCharPosition()+")");
		}
	}
	
	public static Toml loadToml(URI src, int sizeLimit, URI sigUrl) throws IOException {
		try {
			return new Toml().read(new ByteArrayInputStream(loadAndVerify(src, sizeLimit, sigUrl)));
		} catch (IllegalStateException e) {
			throw new IOException("Failed to parse "+src+" as TOML: "+e.getMessage());
		}
	}
	
	public static Toml loadToml(URI src, int sizeLimit, HashFunction func, String expectedHash) throws IOException {
		byte[] data = downloadToMemory(src, sizeLimit);
		if (data == null) throw new IOException("Size limit of "+(sizeLimit/K)+"K for "+src+" exceeded");
		String hash = Bases.bytesToHex(func.createMessageDigest().digest(data));
		if (!hash.equals(expectedHash))
			throw new IOException("Expected "+expectedHash+" from "+src+", but got "+hash);
		return new Toml().read(new ByteArrayInputStream(data));
	}
	
	public static class DownloadedFile {
		/** null if no hash function was specified */
		public final String hash;
		public final File file;
		public DownloadedFile(String hash, File file) {
			this.hash = hash;
			this.file = file;
		}
	}
	
	public static DownloadedFile downloadToFile(URI url, File dir, long size, LongConsumer addProgress, Runnable updateProgress, HashFunction hashFunc, boolean hostile) throws IOException {
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
					hash = Bases.bytesToHex(digest.digest());
				}
				return new DownloadedFile(hash, file);
			} catch (InterruptedIOException e) {
				throw new Retry("Connection to "+url.getHost()+" timed out",
						e);
			}
		});
	}
	
	public static String hash(HashFunction func, File f) throws IOException {
		MessageDigest digest = func.createMessageDigest();
		byte[] buf = new byte[16384];
		try (FileInputStream in = new FileInputStream(f)) {
			while (true) {
				int read = in.read(buf);
				if (read == -1) break;
				digest.update(buf, 0, read);
			}
		}
		return Bases.bytesToHex(digest.digest());
	}

	/**
	 * Closes the stream when done.
	 */
	private static byte[] collectLimited(InputStream in, int limit) throws IOException {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int totalRead = 0;
			byte[] buf = new byte[limit/4];
			while (true) {
				int read = in.read(buf);
				if (read == -1) break;
				totalRead += read;
				if (totalRead > limit) {
					return null;
				}
				baos.write(buf, 0, read);
			}
			return baos.toByteArray();
		} finally {
			in.close();
		}
	}

}
