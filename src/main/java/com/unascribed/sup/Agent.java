package com.unascribed.sup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import com.unascribed.sup.FormatHandler.CheckResult;
import com.unascribed.sup.FormatHandler.FileState;
import com.unascribed.sup.FormatHandler.FilePlan;
import com.unascribed.sup.FormatHandler.UpdatePlan;
import com.unascribed.sup.IOHelper.DownloadedFile;
import com.unascribed.sup.IOHelper.Retry;
import com.unascribed.sup.PuppetHandler.AlertMessageType;
import com.unascribed.sup.PuppetHandler.AlertOption;
import com.unascribed.sup.PuppetHandler.AlertOptionType;
import com.unascribed.sup.QDIni.QDIniException;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import okhttp3.OkHttpClient;

class Agent {

	static final int EXIT_SUCCESS = 0;
	static final int EXIT_CONFIG_ERROR = 1;
	static final int EXIT_CONSISTENCY_ERROR = 2;
	static final int EXIT_BUG = 3;
	static final int EXIT_USER_REQUEST = 4;

	static final boolean DEBUG = Boolean.getBoolean("unsup.debug");
	
	static volatile boolean awaitingExit = false;
	
	private static PrintStream logStream;
	
	private static boolean standalone;
	public static boolean filesystemIsCaseSensitive;
	
	static List<ExceptableRunnable> cleanup = new ArrayList<>();
	static QDIni config;
	
	static String detectedEnv;
	static Set<String> validEnvs;
	
	static EdDSAPublicKey publicKey;
	
	// read by the Unsup class when it loads
	// be careful not to load that class until this is all initialized
	public static String sourceVersion;
	public static boolean updated;

	static JsonObject state;

	private static File stateFile;
	
	/** this mutex must be held while doing sensitive operations that shouldn't be interrupted */
	static final Object dangerMutex = new Object();
	private static final SimpleDateFormat logDateFormat = new SimpleDateFormat("HH:mm:ss");
	
	static final OkHttpClient okhttp = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.callTimeout(120, TimeUnit.SECONDS)
			.cookieJar(new MemoryCookieJar())
			.build();
	
	public static void main(String[] args) throws UnsupportedEncodingException {
		standalone = true;
		premain(args.length >= 1 ? args[0] : null);
	}
	
	public static void premain(String arg) throws UnsupportedEncodingException {
		long start = System.nanoTime();
		try {
			createLogStream();
			log("INFO", (standalone ? "Starting in standalone mode" : "Launch hijack successful")+". unsup v"+Util.VERSION);
			detectFilesystemCaseSensitivity();
			if (!loadConfig()) {
				log("WARN", "Cannot find a config file, giving up.");
				// by returning but not exiting, we yield control to the program whose launch we hijacked, if any
				return;
			}
			checkRequiredKeys("version", "source_format", "source");
			int version = config.getInt("version", -1);
			if (version != 1) {
				log("ERROR", "Config file error: Unknown version "+version+" at "+config.getBlame("version")+"! Exiting.");
				exit(EXIT_CONFIG_ERROR);
				return;
			}
			config = mergePreset(config, "__global__");
			if (config.containsKey("preset")) {
				config = mergePreset(config, config.get("preset"));
			}
			if (config.getBoolean("use_envs", false)) {
				detectEnv(config.get("force_env", arg));
			}
			
			boolean noGui = determineNoGui();
			
			SourceFormat fmt = config.getEnum("source_format", SourceFormat.class, null);
			URL src;
			try {
				src = new URL(config.get("source"));
			} catch (MalformedURLException e) {
				log("ERROR", "Config error: source URL is malformed! "+e.getMessage()+". Exiting.");
				exit(EXIT_CONFIG_ERROR);
				return;
			}
			
			if (!noGui) {
				PuppetHandler.create();
				cleanup.add(PuppetHandler::destroy);
			}
			
			if (config.containsKey("public_key")) {
				try {
					String line = config.get("public_key");
					if (line.startsWith("ed25519 ")) {
						publicKey = new EdDSAPublicKey(new X509EncodedKeySpec(Base64.getDecoder().decode(line.substring(8))));
					} else {
						throw new IllegalArgumentException("Unknown key kind, expected ed25519");
					}
					cleanup.add(() -> publicKey = null);
				} catch (Throwable t) {
					t.printStackTrace();
					log("ERROR", "Config file error: public_key is not valid at "+config.getBlame("public_key")+"! Exiting.");
					exit(EXIT_CONFIG_ERROR);
					return;
				}
			}
			
			stateFile = new File(".unsup-state.json");
			if (stateFile.exists()) {
				try (InputStream in = new FileInputStream(stateFile)) {
					state = JsonParser.object().from(in);
				} catch (Exception e) {
					e.printStackTrace();
					log("ERROR", "Couldn't load state file! Exiting.");
					exit(EXIT_CONSISTENCY_ERROR);
					return;
				}
			} else {
				state = new JsonObject();
			}
			
			PuppetHandler.setPuppetColorsFromConfig();
			
			PuppetHandler.tellPuppet(":build");
			
			PuppetHandler.tellPuppet(":subtitle="+config.get("subtitle", ""));
			// we don't want to flash a window on the screen if things aren't going slow, so we tell
			// the puppet to wait 750ms before actually making the window visible, and assign an
			// identifier to our order so we can belay it later if we finished before the timer
			// expired
			PuppetHandler.tellPuppet("[openTimeout]750:visible=true");

			
			PuppetHandler.updateTitle("Checking for updates...", false);
			try {
				CheckResult res = null;
				if (fmt == SourceFormat.UNSUP) {
					res = FormatHandlerUnsup.check(src);
				} else if (fmt == SourceFormat.PACKWIZ) {
					res = FormatHandlerPackwiz.check(src);
				}
				if (res != null) {
					sourceVersion = res.ourVersion.name;
					if (res.plan != null) {
						applyUpdate(res);
					}
				}
			} catch (Throwable t) {
				t.printStackTrace();
				log("WARN", "Error while updating");
				PuppetHandler.tellPuppet(":expedite=openTimeout");
				if (PuppetHandler.openAlert("unsup error",
						"<b>An error occurred while attempting to update.</b><br/>See the log for more info."+(standalone ? "" : "<br/>Choose Cancel to abort launch."),
						PuppetHandler.AlertMessageType.ERROR, standalone ? AlertOptionType.OK : AlertOptionType.OK_CANCEL, AlertOption.OK) == AlertOption.CANCEL) {
					log("INFO", "User cancelled error dialog! Exiting.");
					exit(EXIT_USER_REQUEST);
					return;
				}
			} finally {
				PuppetHandler.tellPuppet(":subtitle=");
			}
			
			if (awaitingExit) Util.blockForever();

			PuppetHandler.tellPuppet(":belay=openTimeout");
			if (PuppetHandler.puppetOut != null) {
				log("INFO", "Waiting for puppet to complete done animation...");
				PuppetHandler.tellPuppet(":title=All done");
				PuppetHandler.tellPuppet(":mode=done");
				PuppetHandler.doneAnimatingLatch.await();
			}
			PuppetHandler.tellPuppet(":exit");
			if (PuppetHandler.puppet != null) {
				log("INFO", "Waiting for puppet to exit...");
				if (!PuppetHandler.puppet.waitFor(2, TimeUnit.SECONDS)) {
					log("WARN", "Tired of waiting, killing the puppet.");
					PuppetHandler.puppet.destroyForcibly();
				}
			}
			
			if (standalone) {
				log("INFO", "Ran in standalone mode, no program will be started.");
				log("INFO", "It is recommended you use unsup as a Java agent via -javaagent:unsup.jar to piggyback on another program's launch.");
			} else {
				log("INFO", "All done, handing over control.");
				// poke the Unsup class so it loads and finalizes all of its values
				if (Unsup.SOURCE_VERSION != null) Unsup.SOURCE_VERSION.toString();
				String cmd = System.getProperty("sun.java.command");
				if ("org.multimc.EntryPoint".equals(cmd)) {
					// we actually run before MultiMC's Java-side launcher code, so print a
					// blank line to put "Using onesix launcher." in an island like it's
					// supposed to be
					System.out.println();
				}
			}
		} catch (QDIniException e) {
			log("ERROR", "Config file error: "+e.getMessage()+"! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		} catch (InterruptedException e) {
			throw new AssertionError(e);
		} finally {
			log("INFO", "Finished after "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)+"ms.");
			cleanup();
		}
	}
	
	// "step" methods, called only once, exist to turn premain() into a logical overview that can be
	// drilled down into as necessary
	
	private static void applyUpdate(CheckResult res) throws IOException {
		UpdatePlan<?> plan = res.plan;
		boolean bootstrapping = plan.isBootstrap;
		log("DEBUG", "Alright, so here's what I'm thinking:");
		Set<String> unchanged = new HashSet<>(plan.expectedState.keySet());
		for (Map.Entry<String, ? extends FilePlan> en : plan.files.entrySet()) {
			unchanged.remove(en.getKey());
			FileState from = plan.expectedState.get(en.getKey());
			FilePlan to = en.getValue();
			log("DEBUG", "- "+en.getKey()+" is currently "+ponder(from));
			log("DEBUG", "  It has been changed to "+ponder(to.state));
			if (to.url != null) {
				if (to.fallbackUrl != null) {
					log("DEBUG", "  I'll be grabbing that from "+to.url+" (or "+to.fallbackUrl+" if that doesn't work)");
				} else {
					log("DEBUG", "  I'll be grabbing that from "+to.url);
				}
			}
		}
		log("DEBUG", unchanged.size()+" other file"+(unchanged.size() == 1 ? "" : "s")+" have not been changed.");
		if (DEBUG) {
			log("DEBUG", "Sound good? You have 4 seconds to kill the process if not.");
			try {
				Thread.sleep(4000);
			} catch (InterruptedException e) {
			}
			log("DEBUG", "Continuing.");
		}
		long progressDenom = 1;
		File wd = new File("");
		PuppetHandler.updateSubtitle("Verifying consistency");
		Set<String> moveAside = new HashSet<>();
		Map<ConflictType, AlertOption> conflictPreload = new EnumMap<>(ConflictType.class);
		for (Map.Entry<String, ? extends FilePlan> en : plan.files.entrySet()) {
			String path = en.getKey();
			FileState from = plan.expectedState.getOrDefault(path, FileState.EMPTY);
			FilePlan f = en.getValue();
			FileState to = f.state;
			File dest = new File(path);
			if (!dest.getAbsolutePath().startsWith(wd.getAbsolutePath()+File.separator))
				throw new IOException("Refusing to download to a file outside of working directory");
			ConflictType conflictType = ConflictType.NO_CONFLICT;
			if (dest.exists()) {
				boolean normalConflict = false;
				long size = dest.length();
				if (from.hash == null) {
					if (to.sizeMatches(size) && to.hash.equals(IOHelper.hash(to.func, dest))) {
						log("INFO", path+" was created in this update and locally, but the local version matches the update. Skipping");
						continue;
					}
					conflictType = ConflictType.LOCAL_AND_REMOTE_CREATED;
				} else if (from.sizeMatches(size)) {
					String hash = IOHelper.hash(from.func, dest);
					if (from.hash.equals(hash)) {
						log("INFO", path+" matches the expected from hash");
					} else if (to.sizeMatches(size) && to.hash.equals(from.func == to.func ? hash : IOHelper.hash(to.func, dest))) {
						log("INFO", path+" matches the expected to hash, so has already been updated locally. Skipping");
						continue;
					} else {
						log("INFO", "CONFLICT: "+path+" doesn't match the expected from hash ("+hash+" != "+from.hash+")");
						normalConflict = true;
					}
				} else if (to.sizeMatches(size) && to.hash.equals(IOHelper.hash(to.func, dest))) {
					log("INFO", path+" matches the expected to hash, so has already been updated locally. Skipping");
					continue;
				} else {
					log("INFO", "CONFLICT: "+path+" doesn't match the expected from size ("+size+" != "+from.size+")");
					normalConflict = true;
				}
				if (normalConflict) {
					if (to.hash == null) {
						conflictType = ConflictType.LOCAL_CHANGED_REMOTE_DELETED;
					} else {
						conflictType = ConflictType.LOCAL_AND_REMOTE_CHANGED;
					}
				}
			} else {
				if (to.hash == null) {
					log("INFO", path+" was deleted in this update, but it's already missing locally. Skipping");
					continue;
				} else if (from.hash != null) {
					conflictType = ConflictType.LOCAL_DELETED_REMOTE_CHANGED;
				}
			}
			if (conflictType != ConflictType.NO_CONFLICT) {
				AlertOption resp;
				if (conflictPreload.containsKey(conflictType)) {
					resp = conflictPreload.get(conflictType);
				} else {
					resp = PuppetHandler.openAlert("File conflict",
							"<b>The file "+path+" was "+conflictType.msg+".</b><br/>"
									+ "Do you want to replace it with the version from the update?<br/>"
									+ "Choose Cancel to abort. No files have been changed yet."+
									(dest.exists() ? "<br/><br/>If you choose Yes, a copy of the current file will be created at "+path+".orig." : ""),
							AlertMessageType.QUESTION, AlertOptionType.YES_NO_TO_ALL_CANCEL, AlertOption.YES);
					if (resp == AlertOption.NOTOALL) {
						resp = AlertOption.NO;
						conflictPreload.put(conflictType, AlertOption.NO);
					} else if (resp == AlertOption.YESTOALL) {
						resp = AlertOption.YES;
						conflictPreload.put(conflictType, AlertOption.YES);
					}
				}
				if (resp == AlertOption.NO) {
					f.skip = true;
					continue;
				} else if (resp == AlertOption.CANCEL) {
					log("INFO", "User cancelled error dialog! Exiting.");
					exit(EXIT_USER_REQUEST);
					return;
				}
				if (dest.exists()) {
					moveAside.add(path);
				}
			}
			progressDenom += (to.size == -1 ? 1 : to.size);
		}
		File tmp = new File(".unsup-tmp");
		if (!tmp.exists()) {
			tmp.mkdirs();
		}
		final long progressDenomf = progressDenom;
		AtomicLong progress = new AtomicLong();
		Runnable updateProgress = () -> PuppetHandler.updateProgress((int)((progress.get()*1000)/progressDenomf));
		PuppetHandler.updateTitle(bootstrapping ? "Bootstrapping..." : "Updating...", true);
		ExecutorService svc = Executors.newFixedThreadPool(6);
		Set<String> files = new HashSet<>();
		List<Future<?>> futures = new ArrayList<>();
		Map<FilePlan, DownloadedFile> downloads = new IdentityHashMap<>();
		Runnable updateSubtitle = () -> {
			if (files.size() == 0) {
				PuppetHandler.updateSubtitle("Downloading...");
			} else if (files.size() == 1) {
				PuppetHandler.updateSubtitle("Downloading "+files.iterator().next());
			} else {
				StringBuilder sb = new StringBuilder();
				boolean first = true;
				for (String s : files) {
					if (first) {
						first = false;
					} else {
						sb.append(", ");
					}
					sb.append(s.substring(s.lastIndexOf('/')+1));
				}
				PuppetHandler.updateSubtitle("Downloading "+sb);
			}
		};
		for (Map.Entry<String, ? extends FilePlan> en : plan.files.entrySet()) {
			String path = en.getKey();
			FilePlan f = en.getValue();
			if (f.skip) {
				log("INFO", "Skipping download of "+path);
				continue;
			}
			FileState to = f.state;
			if (to.size == 0) {
				continue;
			}
			futures.add(svc.submit(() -> {
				synchronized (files) {
					files.add(path);
					updateSubtitle.run();
				}
				try {
					if (f.primerUrl != null) {
						try (InputStream in = IOHelper.get(f.primerUrl, f.hostile)) {
							byte[] buf = new byte[8192];
							while (true) {
								if (in.read(buf) == -1) break;
							}
						}
						Thread.sleep(2000+ThreadLocalRandom.current().nextInt(1200));
					}
					DownloadedFile df;
					long[] contributedProgress = {0};
					try {
						log("INFO", "Downloading "+path+" from "+describe(f.url));
						df = downloadAndCheckHash(tmp, progress, updateProgress, path, f, f.url, to, contributedProgress);
						if (to.size == -1) progress.incrementAndGet();
					} catch (Throwable t) {
						if (f.fallbackUrl != null) {
							t.printStackTrace();
							progress.addAndGet(-contributedProgress[0]);
							contributedProgress[0] = 0;
							log("WARN", "Failed to download "+path+" from specified URL, trying again from "+describe(f.fallbackUrl));
							df = downloadAndCheckHash(tmp, progress, updateProgress, path, f, f.fallbackUrl, to, contributedProgress);
							if (to.size == -1) progress.incrementAndGet();
						} else {
							throw t;
						}
					}
					synchronized (downloads) {
						downloads.put(f, df);
					}
					return null;
				} finally {
					synchronized (files) {
						files.remove(path);
						updateSubtitle.run();
					}
				}
			}));
		}
		svc.shutdown();
		for (Future<?> future : futures) {
			while (true) {
				try {
					future.get();
					break;
				} catch (InterruptedException e) {
				} catch (ExecutionException e) {
					for (Future<?> f2 : futures) {
						try {
							f2.cancel(false);
						} catch (Throwable t) {}
					}
					if (e.getCause() instanceof IOException) throw (IOException)e.getCause();
					throw new RuntimeException(e);
				}
			}
		}
		PuppetHandler.updateTitle(bootstrapping ? "Bootstrapping..." : "Updating...", false);
		synchronized (dangerMutex) {
			PuppetHandler.updateSubtitle("Applying changes. Do not force close the updater.");
			for (Map.Entry<String, ? extends FilePlan> en : plan.files.entrySet()) {
				String path = en.getKey();
				FilePlan f = en.getValue();
				FileState to = f.state;
				DownloadedFile df = downloads.get(f);
				File dest = new File(path);
				if (!dest.getAbsolutePath().startsWith(wd.getAbsolutePath()+File.separator))
					throw new IOException("Refusing to download to a file outside of working directory");
				Path destPath = dest.toPath();
				if (dest.getParentFile() != null) Files.createDirectories(dest.getParentFile().toPath());
				if (moveAside.contains(path)) {
					Files.move(destPath, destPath.resolveSibling(destPath.getFileName().toString()+".orig"), StandardCopyOption.REPLACE_EXISTING);
				}
				if (to.size == 0) {
					if (to.hash == null) {
						log("INFO", "Deleting "+path);
						Files.deleteIfExists(destPath);
					} else if (dest.exists()) {
						try (FileOutputStream fos = new FileOutputStream(dest)) {
							// open and then immediately close the file to overwrite it with nothing
						}
					} else {
						dest.createNewFile();
					}
				} else {
					Files.move(df.file.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			state = plan.newState;
			state.put("current_version", res.theirVersion.toJson());
			saveState();
		}
		log("INFO", "Update successful!");
		updated = true;
	}

	private static String ponder(FileState state) {
		if (state == null) return "[MISSING. STATE DATA IS INCOMPLETE OR CORRUPT]";
		if (state.hash == null) {
			return "[deleted]";
		}
		return state.toString();
	}

	private static DownloadedFile downloadAndCheckHash(File tmp, AtomicLong progress, Runnable updateProgress, String path, FilePlan f, URL url, FileState to, long[] contributedProgress) throws IOException {
		return IOHelper.withRetries(3, () -> {
			DownloadedFile df = IOHelper.downloadToFile(url, tmp, to.size, to.size == -1 ? l -> {} : l -> {contributedProgress[0]+=l;progress.addAndGet(l);},
					updateProgress, to.func, f.hostile);
			if (!df.hash.equals(to.hash)) {
				throw new Retry("Hash mismatch on downloaded file for "+path+" from "+url+" - expected "+to.hash+", got "+df.hash,
						IOException::new);
			}
			return df;
		});
	}

	private static String describe(URL url) {
		if (url == null) return "(null)";
		if (DEBUG) return url.toString();
		String host = url.getHost();
		if (host == null) return "[null]";
		if (host.isEmpty()) return url.toString();
		switch (host) {
			case "modrinth.com":
			case "cdn.modrinth.com":
					return "Modrinth";
			
			case "mediafilez.forgecdn.net": case "mediafiles.forgecdn.net":
			case "curseforge.com": case "www.curseforge.com":
			case "edge.forgecdn.net":
				return "CurseForge";
			
			case "github.com": case "objects.githubusercontent.com":
				return "GitHub";
				
			default: return host;
		}
	}

	private static void createLogStream() {
		File logTarget = new File("logs");
		if (!logTarget.isDirectory()) {
			logTarget = new File(".");
		}
		File logFile = new File(logTarget, "unsup.log");
		File oldLogFile = new File(logTarget, "unsup.log.1");
		File olderLogFile = new File(logTarget, "unsup.log.2");
		// intentionally ignoring exceptional return here
		// don't really care if any of it fails
		if (logFile.exists()) {
			if (oldLogFile.exists()) {
				if (olderLogFile.exists()) {
					olderLogFile.delete();
				}
				oldLogFile.renameTo(olderLogFile);
			}
			logFile.renameTo(oldLogFile);
		}
		try {
			OutputStream logOut = new FileOutputStream(logFile);
			cleanup.add(logOut::close);
			logStream = new PrintStream(logOut, true, "UTF-8");
		} catch (Exception e) {
			logStream = NullPrintStream.INSTANCE;
			log("WARN", "Failed to open log file "+logFile);
		}
	}
	
	private static void detectFilesystemCaseSensitivity() {
		try {
			File tmp = File.createTempFile("UNSUP", "cAsE-SensitiVITY-teST");
			File tmpLower = new File(tmp.getPath().toLowerCase(Locale.ROOT));
			// we detect case sensitivity by checking if a lowercase version of our file path
			// exists; however, it's possible that file already exists for some reason, so we
			// delete the lowercased file if it exists and see if that caused our mixedcase file
			// to be deleted too. if so, the filesystem must consider both names to be the same
			// directory entry
			if (tmpLower.exists()) {
				if (tmpLower.delete() && !tmp.exists()) {
					filesystemIsCaseSensitive = false;
					log("INFO", "Filesystem is NOT case sensitive");
				} else {
					filesystemIsCaseSensitive = true;
					log("INFO", "Filesystem is case sensitive, but looked case insensitive for a moment");
				}
			} else {
				filesystemIsCaseSensitive = true;
				log("INFO", "Filesystem is case sensitive");
			}
			if (tmp.exists()) {
				tmp.delete();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
			log("INFO", "Failed to test if filesystem is case sensitive. Assuming it is.");
			filesystemIsCaseSensitive = true;
		}
	}
	
	private static boolean loadConfig() {
		File configFile = new File("unsup.ini");
		if (configFile.exists()) {
			try {
				config = QDIni.load(configFile);
				cleanup.add(() -> config = null);
				log("INFO", "Found and loaded unsup.ini. What secrets does it hold?");
			} catch (Exception e) {
				e.printStackTrace();
				log("ERROR", "Found unsup.ini, but couldn't parse it! Exiting.");
				exit(EXIT_CONFIG_ERROR);
			}
			return true;
		} else {
			log("WARN", "No config file found? Doing nothing.");
			return false;
		}
	}
	
	private static void checkRequiredKeys(String... requiredKeys) {
		for (String req : requiredKeys) {
			if (!config.containsKey(req)) {
				log("ERROR", "Config file error: "+req+" is required, but was not defined! Exiting.");
				exit(EXIT_CONFIG_ERROR);
				return;
			}
		}
	}

	private static boolean determineNoGui() {
		if (standalone) return !Boolean.getBoolean("unsup.guiInStandalone");
		if (config.containsKey("no_gui")) return config.getBoolean("no_gui", false);
		if (config.getBoolean("recognize_nogui", false)) {
			String cmd = System.getProperty("sun.java.command");
			if (cmd != null) {
				return Util.containsWholeWord(cmd, "nogui") || Util.containsWholeWord(cmd, "--nogui");
			}
		}
		return false;
	}

	private static void detectEnv(String forcedEnv) {
		if (standalone && forcedEnv == null) {
			log("ERROR", "Cannot sync an env-based config in standalone mode unless an argument is given specifying the env! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		}
		Set<String> foundEnvs = new HashSet<>();
		List<String> checkedMarkers = new ArrayList<>();
		String ourEnv = forcedEnv;
		for (String k : config.keySet()) {
			if (k.startsWith("env.") && k.endsWith(".marker")) {
				String env = k.substring(4, k.length()-7);
				foundEnvs.add(env);
				if (ourEnv == null) {
					for (String possibility : config.getAll(k)) {
						if (possibility.equals("*")) {
							ourEnv = env;
							break;
						} else {
							checkedMarkers.add(possibility);
							try {
								Class.forName(possibility, false, Agent.class.getClassLoader());
								ourEnv = env;
								break;
							} catch (ClassNotFoundException e) {}
						}
					}
				}
			}
		}
		if (foundEnvs.isEmpty()) {
			log("ERROR", "use_envs is true, but found no env declarations! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		}
		if (ourEnv == null) {
			log("ERROR", "use_envs is true, and we found no env markers! Checked for the following markers:");
			for (String s : checkedMarkers) {
				log("ERROR", "- "+s);
			}
			log("ERROR", "Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		}
		if (!foundEnvs.contains(ourEnv)) {
			log("ERROR", "Invalid env specified: \""+ourEnv+"\"! Valid envs:");
			for (String s : foundEnvs) {
				log("ERROR", "- "+s);
			}
			log("ERROR", "Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		}
		if (standalone) {
			log("INFO", "Declared env is "+ourEnv);
		} else {
			log("INFO", "Detected env is "+ourEnv);
		}
		detectedEnv = ourEnv;
		validEnvs = foundEnvs;
		cleanup.add(() -> validEnvs = null);
	}

	// helper methods, called more than once
	
	private static QDIni mergePreset(QDIni config, String presetName) {
		URL u = Agent.class.getClassLoader().getResource("com/unascribed/sup/presets/"+presetName+".ini");
		if (u == null) {
			log("ERROR", "Config file error: Preset "+presetName+" not found at "+config.getBlame("preset")+"! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return null;
		}
		try (InputStream in = u.openStream()) {
			QDIni preset = QDIni.load("<preset "+presetName+">", in);
			config = preset.merge(config);
		} catch (IOException e) {
			e.printStackTrace();
			log("ERROR", "Failed to load preset "+presetName+"! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return null;
		}
		return config;
	}
	
	private static void cleanup() {
		for (ExceptableRunnable er : cleanup) {
			try {
				er.run();
			} catch (Throwable t) {}
		}
		okhttp.dispatcher().executorService().shutdown();
		okhttp.connectionPool().evictAll();
		cleanup = null;
	}
	
	static void saveState() throws IOException {
		saveJson(stateFile, state);
	}
	
	static void saveJson(File file, JsonObject obj) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			JsonWriter.on(fos).object(obj).done();
		}
	}
	
	static void exit(int code) {
		cleanup();
		System.exit(code);
	}
	
	static synchronized void log(String flavor, String msg) {
		log(standalone ? "sync" : "agent", flavor, msg);
	}
	
	static synchronized void log(String tag, String flavor, String msg) {
		String line = "["+logDateFormat.format(new Date())+"] [unsup "+tag+"/"+flavor+"]: "+msg;
		if ("DEBUG" != flavor || DEBUG) System.out.println(line);
		logStream.println(line);
	}
	
}
