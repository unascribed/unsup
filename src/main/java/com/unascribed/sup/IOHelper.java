package com.unascribed.sup;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
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
	
	protected static byte[] loadAndVerify(URL src, int sizeLimit, URL sigUrl) throws IOException, JsonParserException {
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
		InputStream conn = get(url);
		byte[] resp = Util.collectLimited(conn, sizeLimit);
		return resp;
	}

	protected static InputStream get(URL url) throws IOException {
		Response res = Agent.okhttp.newCall(new Request.Builder()
				.url(url)
				.header("User-Agent", "unsup/"+Util.VERSION)
				.build()).execute();
		if (res.code() != 200) {
			if (res.code() == 404 || res.code() == 410) throw new FileNotFoundException();
			byte[] b = Util.collectLimited(res.body().byteStream(), 512);
			String s = b == null ? "(response too long)" : new String(b, StandardCharsets.UTF_8);
			throw new IOException("Received non-200 response from server for "+url+": "+res.code()+"\n"+s);
		}
		return res.body().byteStream();
	}
	
	protected static String loadString(URL src, int sizeLimit, URL sigUrl) throws IOException, JsonParserException {
		return new String(loadAndVerify(src, sizeLimit, sigUrl), StandardCharsets.UTF_8);
	}
	
	protected static JsonObject loadJson(URL src, int sizeLimit, URL sigUrl) throws IOException, JsonParserException {
		return JsonParser.object().from(new ByteArrayInputStream(loadAndVerify(src, sizeLimit, sigUrl)));
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
	
	protected static DownloadedFile downloadToFile(URL url, File dir, long size, LongConsumer addProgress, Runnable updateProgress, HashFunction hashFunc) throws IOException {
		InputStream conn = get(url);
		byte[] buf = new byte[16384];
		File file = File.createTempFile("download", "", dir);
		Agent.cleanup.add(file::delete);
		long readTotal = 0;
		long lastProgressUpdate = 0;
		MessageDigest digest = hashFunc == null ? null : hashFunc.createMessageDigest();
		try (InputStream in = conn; FileOutputStream out = new FileOutputStream(file)) {
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
		if (readTotal != size) {
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
