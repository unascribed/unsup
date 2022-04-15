package com.unascribed.sup;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntPredicate;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import com.unascribed.sup.QDIni.QDIniException;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;

class Agent {

	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_CONFIG_ERROR = 1;
	private static final int EXIT_CONSISTENCY_ERROR = 2;
	private static final int EXIT_BUG = 3;
	private static final int EXIT_USER_REQUEST = 4;
	
	private static final int K = 1024;
	private static final int M = K*1024;
	
	private static final long ONE_SECOND_IN_NANOS = TimeUnit.SECONDS.toNanos(1);
	
	private static final String DEFAULT_HASH_FUNCTION = HashFunction.SHA2_256.name;
	
	/** this mutex must be held while doing sensitive operations that shouldn't be interrupted */
	private static final Object dangerMutex = new Object();
	private static final SimpleDateFormat logDateFormat = new SimpleDateFormat("HH:mm:ss");
	
	private static final Latch doneAnimatingLatch = new Latch();
	
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
				detectEnv(standalone ? arg : null);
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
				createPuppet();
				cleanup.add(puppet::destroy);
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
			
			if (puppet != null) {
				puppetOut = new BufferedOutputStream(puppet.getOutputStream(), 512);
			}
			
			setPuppetColorsFromConfig();
			
			tellPuppet(":build");
			
			tellPuppet(":subtitle="+config.get("subtitle", ""));
			// we don't want to flash a window on the screen if things aren't going slow, so we tell
			// the puppet to wait 250ms before actually making the window visible, and assign an
			// identifier to our order so we can belay it later if we finished before the timer
			// expired
			tellPuppet("[openTimeout]250:visible=true");

			
			updateTitle("Checking for updates...", false);
			try {
				if (fmt == SourceFormat.UNSUP) {
					doUnsupFormatUpdate(src);
				}
			} catch (Throwable t) {
				t.printStackTrace();
				log("WARN", "Error while updating");
				tellPuppet(":expedite=openTimeout");
				if (openAlert("unsup error",
						"<b>An error occurred while attempting to update.</b><br/>See the log for more info."+(standalone ? "" : "<br/>Choose Cancel to abort launch."),
						AlertMessageType.ERROR, standalone ? AlertOptionType.OK : AlertOptionType.OK_CANCEL, AlertOption.OK) == AlertOption.CANCEL) {
					log("INFO", "User cancelled error dialog! Exiting.");
					exit(EXIT_USER_REQUEST);
					return;
				}
			} finally {
				tellPuppet(":subtitle=");
			}
			
			if (awaitingExit) Util.blockForever();

			tellPuppet(":belay=openTimeout");
			if (puppetOut != null) {
				log("INFO", "Waiting for puppet to complete done animation...");
				tellPuppet(":title=All done");
				tellPuppet(":mode=done");
				doneAnimatingLatch.await();
			}
			tellPuppet(":exit");
			if (puppet != null) {
				log("INFO", "Waiting for puppet to exit...");
				if (!puppet.waitFor(2, TimeUnit.SECONDS)) {
					log("WARN", "Tired of waiting, killing the puppet.");
					puppet.destroyForcibly();
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
	
	private static void createLogStream() {
		File logFile;
		if (new File("logs").isDirectory()) {
			logFile = new File("logs/unsup.log");
		} else {
			logFile = new File("unsup.log");
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

	private static void createPuppet() {
		log("INFO", "Attempting to summon a puppet for GUI feedback...");
		out: {
			URI uri;
			try {
				uri = Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			} catch (URISyntaxException e) {
				log("WARN", "Failed to find our own JAR file or directory.");
				puppet = null;
				break out;
			}
			File ourPath;
			try {
				ourPath = new File(uri);
			} catch (IllegalArgumentException e) {
				log("WARN", "Failed to find our own JAR file or directory.");
				puppet = null;
				break out;
			}
			File javaBin = new File(System.getProperty("java.home")+File.separator+"bin");
			String[] executableNames = {
					"javaw.exe",
					"java.exe",
					"javaw",
					"java"
			};
			String java = null;
			for (String exe : executableNames) {
				File f = new File(javaBin, exe);
				if (f.exists()) {
					java = f.getAbsolutePath();
					break;
				}
			}
			if (java == null) {
				log("WARN", "Failed to find Java. Looked in "+javaBin+", but can't find a known executable.");
				puppet = null;
			} else {
				Process p;
				try {
					p = new ProcessBuilder(java, "-cp", ourPath.getAbsolutePath(), "com.unascribed.sup.Puppet")
						.start();
				} catch (Throwable t) {
					log("WARN", "Failed to summon a puppet.");
					log("WARN", "unsup location detected as "+ourPath);
					log("WARN", "Java location detected as "+java);
					t.printStackTrace();
					puppet = null;
					break out;
				}
				log("INFO", "Dark spell successful. Puppet summoned.");
				puppet = p;
			}
		}
		if (puppet == null) {
			log("WARN", "Failed to summon a puppet. Continuing without a GUI.");
		} else {
			Thread puppetErr = new Thread(() -> {
				BufferedReader br = new BufferedReader(new InputStreamReader(puppet.getErrorStream(), StandardCharsets.UTF_8));
				try {
					while (true) {
						String line = br.readLine();
						if (line == null) return;
						if (line.contains("|")) {
							int idx = line.indexOf('|');
							log("puppet", line.substring(0, idx), line.substring(idx+1));
						} else {
							System.err.println("puppet: "+line);
						}
					}
				} catch (IOException e) {}
			}, "unsup puppet error printer");
			puppetErr.setDaemon(true);
			puppetErr.start();
			log("INFO", "Waiting for the puppet to come to life...");
			BufferedReader br = new BufferedReader(new InputStreamReader(puppet.getInputStream(), StandardCharsets.UTF_8));
			String firstLine = null;
			try {
				firstLine = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (firstLine == null) {
				log("WARN", "Puppet failed to come alive. Continuing without a GUI.");
				puppet.destroy();
				puppet = null;
			} else if (!"unsup puppet ready".equals(firstLine)) {
				log("WARN", "Puppet sent unexpected hello line \""+firstLine+"\". (Expected \"unsup puppet ready\") Continuing without a GUI.");
				puppet.destroy();
				puppet = null;
			} else {
				log("INFO", "Puppet is alive! Continuing.");
				Thread puppetOut = new Thread(() -> {
					try {
						while (true) {
							String line = br.readLine();
							if (line == null) return;
							if (line.equals("closeRequested")) {
								log("INFO", "User closed puppet window! Exiting...");
								awaitingExit = true;
								long start = System.nanoTime();
								synchronized (dangerMutex) {
									long diff = System.nanoTime()-start;
									if (diff > TimeUnit.MILLISECONDS.toNanos(500)) {
										log("INFO", "Uninterruptible operations finished, exiting!");
									}
									puppet.destroy();
									if (!puppet.waitFor(1, TimeUnit.SECONDS)) {
										puppet.destroyForcibly();
									}
									exit(EXIT_USER_REQUEST);
								}
							} else if (line.startsWith("alert:")) {
								String[] split = line.split(":");
								String name = split[1];
								String opt = split[2];
								synchronized (alertResults) {
									alertResults.put(name, opt);
									Latch latch = alertWaiters.remove(name);
									if (latch != null) {
										latch.release();
									}
								}
							} else if (line.equals("doneAnimating")) {
								doneAnimatingLatch.release();
							} else {
								log("WARN", "Unknown line from puppet: "+line);
							}
						}
					} catch (IOException | InterruptedException e) {}
				}, "unsup puppet out parser");
				puppetOut.setDaemon(true);
				puppetOut.start();
			}
		}
	}
	
	private static void setPuppetColorsFromConfig() {
		tellPuppet(":colorBackground="+config.get("colors.background", "000000"));
		tellPuppet(":colorTitle="+config.get("colors.title", "FFFFFF"));
		tellPuppet(":colorSubtitle="+config.get("colors.subtitle", "AAAAAA"));
		
		tellPuppet(":colorProgress="+config.get("colors.progress", "FF0000"));
		tellPuppet(":colorProgressTrack="+config.get("colors.progress_track", "AAAAAA"));
		
		tellPuppet(":colorDialog="+config.get("colors.dialog", "FFFFFF"));
		tellPuppet(":colorButton="+config.get("colors.button", "FFFFFF"));
		tellPuppet(":colorButtonText="+config.get("colors.button_text", "FFFFFF"));
	}
	
	private enum ConflictType {
		NO_CONFLICT(null),
		LOCAL_AND_REMOTE_CREATED("created by you and in this update"),
		LOCAL_AND_REMOTE_CHANGED("changed by you and in this update"),
		LOCAL_CHANGED_REMOTE_DELETED("changed by you and deleted in this update"),
		LOCAL_DELETED_REMOTE_CHANGED("deleted by you and changed in this update"),
		;
		public final String msg;
		ConflictType(String msg) {
			this.msg = msg;
		}
	}
	
	private static void doUnsupFormatUpdate(URL src) throws IOException, JsonParserException {
		log("INFO", "Loading unsup-format manifest from "+src);
		JsonObject manifest = loadJson(src, 32*K, new URL(src, "manifest.sig"));
		checkManifestFlavor(manifest, "root", IntPredicates.equals(1));
		Version ourVersion = Version.fromJson(state.getObject("current_version"));
		if (!manifest.containsKey("versions")) throw new IOException("Manifest is missing versions field");
		Version theirVersion = Version.fromJson(manifest.getObject("versions").getObject("current"));
		if (theirVersion == null) throw new IOException("Manifest is missing current version field");
		String ourFlavor = state.getString("flavor");
		JsonArray theirFlavors = manifest.getArray("flavors");
		if (ourFlavor == null && theirFlavors != null) {
			List<JsonObject> flavorObjects = theirFlavors.stream()
					.map(o -> (JsonObject)o)
					.filter(o -> o.getArray("envs") == null || arrayContains(o.getArray("envs"), detectedEnv))
					.collect(Collectors.toList());
			if (flavorObjects.isEmpty()) {
				// there are no flavors eligible for our environment, so we can continue while ignoring flavors
			} else if (flavorObjects.size() == 1) {
				// there is exactly one eligible flavor choice, no sense in bothering the user
				ourFlavor = flavorObjects.get(0).getString("id");
			} else {
				List<String> flavorNames = flavorObjects.stream()
						.map(o -> o.getString("name"))
						.collect(Collectors.toList());
				Map<String, String> namesToIds = flavorObjects.stream()
						.collect(Collectors.toMap(o -> o.getString("name"), o -> o.getString("id")));
				Set<String> ids = flavorObjects.stream()
						.map(o -> o.getString("id"))
						.collect(Collectors.toSet());
				String def = config.get("flavor");
				if (def != null && !ids.contains(def)) {
					log("WARN", "Default flavor specified in unsup.ini does not exist in the manifest.");
					def = null;
				}
				String flavor = openChoiceAlert("Choose flavor", "<b>There are multiple flavor choices available.</b><br/>Please choose one.", flavorNames, def);
				if (flavor == null) {
					log("ERROR", "This is a flavorful manifest, but the unsup.ini doesn't specify a default flavor and we don't have a GUI to ask for a choice! Exiting.");
					exit(EXIT_CONFIG_ERROR);
					return;
				}
				String id = namesToIds.get(flavor);
				log("INFO", "Chose flavor "+id);
				ourFlavor = id;
			}
			state.put("flavor", ourFlavor);
			saveJson(stateFile, state);
		}
		boolean bootstrapped = false;
		if (ourVersion == null) {
			log("INFO", "Update available! We have nothing, they have "+theirVersion);
			JsonObject bootstrap = loadJson(new URL(src, "bootstrap.json"), 2*M, new URL(src, "bootstrap.sig"));
			checkManifestFlavor(bootstrap, "bootstrap", IntPredicates.equals(1));
			Version bootstrapVersion = Version.fromJson(bootstrap.getObject("version"));
			if (bootstrapVersion == null) throw new IOException("Bootstrap manifest is missing version field");
			if (bootstrapVersion.code < theirVersion.code) {
				log("WARN", "Bootstrap manifest version "+bootstrapVersion+" is older than root manifest version "+theirVersion+", will have to perform extra updates");
			}
			HashFunction func = HashFunction.byName(bootstrap.getString("hash_function", DEFAULT_HASH_FUNCTION));
			warnIfInsecure(func);
			updateTitle("Bootstrapping...", false);
			long progressDenom = 0;
			class FileToDownload { String path; String hash; long size; String url; DownloadedFile df; File dest; }
			List<FileToDownload> todo = new ArrayList<>();
			for (Object o : bootstrap.getArray("files")) {
				if (!(o instanceof JsonObject)) throw new IOException("Entry "+o+" in files array is not an object");
				JsonObject file = (JsonObject)o;
				String path = file.getString("path");
				if (path == null) throw new IOException("Entry in files array is missing path");
				String hash = file.getString("hash");
				if (hash == null) throw new IOException(path+" in files array is missing hash");
				if (hash.length() != func.sizeInHexChars)  throw new IOException(path+" in files array hash "+hash+" is wrong length ("+hash.length()+" != "+func.sizeInHexChars+")");
				long size = file.getLong("size", -1);
				if (size < 0) throw new IOException(path+" in files array has invalid or missing size");
				if (size == 0 && !hash.equals(func.emptyHash)) throw new IOException(path+" in files array is empty file, but hash isn't the empty hash ("+hash+" != "+func.emptyHash+")");
				String url = checkSchemeMismatch(src, file.getString("url"));
				JsonArray envs = file.getArray("envs");
				if (!arrayContains(envs, detectedEnv)) {
					log("INFO", "Skipping "+path+" as it's not eligible for env "+detectedEnv);
					continue;
				}
				JsonArray flavors = file.getArray("flavors");
				if (flavors != null && !arrayContains(flavors, ourFlavor)) {
					log("INFO", "Skipping "+path+" as it's not eligible for flavor "+ourFlavor);
					continue;
				}
				FileToDownload ftd = new FileToDownload();
				ftd.path = path;
				ftd.hash = hash;
				ftd.size = size;
				ftd.url = url;
				todo.add(ftd);
				progressDenom += size;
			}
			File tmp = new File(".unsup-tmp");
			if (!tmp.exists()) {
				tmp.mkdirs();
			}
			updateTitle("Bootstrapping...", true);
			final long progressDenomf = progressDenom;
			AtomicLong progress = new AtomicLong();
			Runnable updateProgress = () -> updateProgress((int)((progress.get()*1000)/progressDenomf));
			File wd = new File("");
			AlertOption passOnePreload = null;
			for (FileToDownload ftd : todo) {
				tellPuppet(":subtitle=Downloading "+ftd.path);
				URL blobUrl = new URL(src, "blobs/"+ftd.hash.substring(0, 2)+"/"+ftd.hash);
				URL u;
				if (ftd.url != null) {
					u = new URL(ftd.url);
				} else {
					u = blobUrl;
				}
				File dest = new File(ftd.path);
				if (!dest.getAbsolutePath().startsWith(wd.getAbsolutePath()+File.separator))
					throw new IOException("Refusing to download to a file outside of working directory: "+dest);
				if (dest.exists()) {
					if (dest.length() == ftd.size) {
						String hash = hash(func, dest);
						if (ftd.hash.equals(hash)) {
							log("INFO", ftd.path+" already exists and the hash matches, keeping it and skipping download");
							progress.addAndGet(ftd.size);
							updateProgress.run();
							continue;
						} else {
							log("INFO", ftd.path+" already exists but the hash doesn't match, redownloading it ("+hash+" != "+ftd.hash+")");
						}
					} else {
						log("INFO", ftd.path+" already exists but the size doesn't match, redownloading it ("+dest.length()+" != "+ftd.size+")");
					}
					AlertOption resp;
					if (passOnePreload == null) {
						resp = openAlert("File conflict",
								"<b>The file "+ftd.path+" already exists.</b><br/>Do you want to replace it?<br/>Choose Cancel to abort. No files have been changed yet.",
								AlertMessageType.QUESTION, AlertOptionType.YES_NO_TO_ALL_CANCEL, AlertOption.YES);
						if (resp == AlertOption.NOTOALL) {
							resp = passOnePreload = AlertOption.NO;
						} else if (resp == AlertOption.YESTOALL) {
							resp = passOnePreload = AlertOption.YES;
						}
					} else {
						resp = passOnePreload;
					}
					if (resp == AlertOption.NO) {
						continue;
					} else if (resp == AlertOption.CANCEL) {
						log("INFO", "User cancelled error dialog! Exiting.");
						exit(EXIT_USER_REQUEST);
						return;
					}
				}
				if (ftd.size == 0) {
					ftd.dest = dest;
					continue;
				}
				long origProgress = progress.get();
				DownloadedFile df;
				try {
					log("INFO", "Downloading "+ftd.path+" from "+u);
					df = downloadToFile(u, tmp, ftd.size, progress::addAndGet, updateProgress, func);
					if (!df.hash.equals(ftd.hash)) {
						throw new IOException("Hash mismatch on downloaded file for "+ftd.path+" from "+u+" - expected "+ftd.hash+", got "+df.hash);
					}
				} catch (Throwable t) {
					if (ftd.url != null) {
						t.printStackTrace();
						progress.set(origProgress);
						log("WARN", "Failed to download "+ftd.path+" from specified URL, trying again from "+blobUrl);
						df = downloadToFile(blobUrl, tmp, ftd.size, progress::addAndGet, updateProgress, func);
						if (!df.hash.equals(ftd.hash)) {
							throw new IOException("Hash mismatch on downloaded file for "+ftd.path+" from "+u+" - expected "+ftd.hash+", got "+df.hash);
						}
					} else {
						throw t;
					}
				}
				ftd.df = df;
				ftd.dest = dest;
			}
			updateTitle("Bootstrapping...", false);
			synchronized (dangerMutex) {
				tellPuppet(":subtitle=Applying changes");
				for (FileToDownload ftd : todo) {
					if (ftd.dest == null) continue;
					if (ftd.dest.getParentFile() != null) {
						Files.createDirectories(ftd.dest.getParentFile().toPath());
					}
					if (ftd.size == 0) {
						if (ftd.dest.exists()) {
							try (FileOutputStream fos = new FileOutputStream(ftd.dest)) {
								// open and then immediately close the file to overwrite it with nothing
							}
						} else {
							ftd.dest.createNewFile();
						}
					} else {
						Files.move(ftd.df.file.toPath(), ftd.dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
				}
				state.put("current_version", bootstrapVersion.toJson());
				saveJson(stateFile, state);
			}
			ourVersion = bootstrapVersion;
			log("INFO", "Bootstrap successful!");
			updated = true;
		}
		if (theirVersion.code > ourVersion.code) {
			if (!bootstrapped) {
				log("INFO", "Update available! We have "+ourVersion+", they have "+theirVersion);
				AlertOption updateResp = openAlert("Update available",
						"<b>An update from "+ourVersion.name+" to "+theirVersion.name+" is available!</b><br/>Do you want to install it?",
						AlertMessageType.QUESTION, AlertOptionType.YES_NO, AlertOption.YES);
				if (updateResp == AlertOption.NO) {
					log("INFO", "Ignoring update by user choice.");
					return;
				}
			}
			class Change {
				String path;
				HashFunction fromHashFunc; String fromHash; long fromSize; int fromCode;
				HashFunction toHashFunc; String toHash; long toSize; int toCode;
				String url;
				DownloadedFile df; File dest;
				boolean moveAside;
			}
			updateTitle("Updating...", false);
			tellPuppet(":subtitle=Calculating update");
			Map<String, Change> changesByPath = new LinkedHashMap<>();
			boolean yappedAboutConsistency = false;
			int updates = theirVersion.code-ourVersion.code;
			for (int i = 0; i < updates; i++) {
				int code = ourVersion.code+(i+1);
				JsonObject ver = loadJson(new URL(src, "versions/"+code+".json"), 2*M, new URL(src, "versions/"+code+".sig"));
				checkManifestFlavor(ver, "update", IntPredicates.equals(1));
				HashFunction func = HashFunction.byName(ver.getString("hash_function", DEFAULT_HASH_FUNCTION));
				warnIfInsecure(func);
				for (Object o : ver.getArray("changes")) {
					if (!(o instanceof JsonObject)) throw new IOException("Entry "+o+" in changes array is not an object");
					JsonObject file = (JsonObject)o;
					String path = file.getString("path");
					if (path == null) throw new IOException("Entry in changes array is missing path");
					String fromHash = file.getString("from_hash");
					if (fromHash != null && fromHash.length() != func.sizeInHexChars)  throw new IOException(path+" in changes array from_hash "+fromHash+" is wrong length ("+fromHash.length()+" != "+func.sizeInHexChars+")");
					long fromSize = file.getLong("from_size", -1);
					if (fromSize < 0) throw new IOException(path+" in changes array has invalid or missing from_size");
					if (fromSize == 0 && (fromHash != null && !fromHash.equals(func.emptyHash))) throw new IOException(path+" from in changes array is empty file, but hash isn't the empty hash or null ("+fromHash+" != "+func.emptyHash+")");
					String toHash = file.getString("to_hash");
					if (toHash != null && toHash.length() != func.sizeInHexChars)  throw new IOException(path+" in changes array to_hash "+toHash+" is wrong length ("+toHash.length()+" != "+func.sizeInHexChars+")");
					long toSize = file.getLong("to_size", -1);
					if (toSize < 0) throw new IOException(path+" in changes array has invalid or missing toSize");
					if (toSize == 0 && (toHash != null && !toHash.equals(func.emptyHash))) throw new IOException(path+" to in changes array is empty file, but hash isn't the empty hash or null ("+toHash+" != "+func.emptyHash+")");
					if (fromSize == toSize && Objects.equals(fromHash, toHash)) {
						log("WARN", path+" in changes array has same from and to hash/size? Ignoring");
						continue;
					}
					String url = checkSchemeMismatch(src, file.getString("url"));
					JsonArray envs = file.getArray("envs");
					if (!arrayContains(envs, detectedEnv)) {
						log("INFO", "Skipping "+path+" as it's not eligible for env "+detectedEnv);
						continue;
					}
					JsonArray flavors = file.getArray("flavors");
					if (flavors != null && !arrayContains(flavors, ourFlavor)) {
						log("INFO", "Skipping "+path+" as it's not eligible for flavor "+ourFlavor);
						continue;
					}
					if (changesByPath.containsKey(path)) {
						Change c = changesByPath.get(path);
						if (c.toHashFunc == func) {
							if (!c.toHash.equals(fromHash) || c.toSize != fromSize) {
								throw new IOException("Bad update: "+path+" in "+c.toCode+" specified to become "+c.toHash+" size "+c.toSize+", but "+code+" expects it to have been "+fromHash+" size "+fromSize);
							}
						} else if (!yappedAboutConsistency) {
							yappedAboutConsistency = true;
							log("WARN", "Cannot perform consistency check on multi-update due to mismatched hash functions");
						}
						c.toHash = toHash;
						c.toHashFunc = func;
						c.toSize = toSize;
						c.toCode = code;
						c.url = url;
					} else {
						Change c = new Change();
						c.path = path;
						c.fromHash = fromHash;
						c.fromHashFunc = func;
						c.fromSize = fromSize;
						c.fromCode = code;
						c.toHash = toHash;
						c.toHashFunc = func;
						c.toSize = toSize;
						c.toCode = code;
						c.url = url;
						changesByPath.put(path, c);
					}
				}
			}
			long progressDenom = 0;
			File wd = new File("");
			tellPuppet(":subtitle=Verifying consistency");
			Map<ConflictType, AlertOption> conflictPreload = new EnumMap<>(ConflictType.class);
			for (Change c : changesByPath.values()) {
				File dest = new File(c.path);
				if (!dest.getAbsolutePath().startsWith(wd.getAbsolutePath()+File.separator))
					throw new IOException("Refusing to download to a file outside of working directory");
				ConflictType conflictType = ConflictType.NO_CONFLICT;
				if (dest.exists()) {
					boolean normalConflict = false;
					long size = dest.length();
					if (c.fromHash == null) {
						if (c.toSize == size && c.toHash.equals(hash(c.toHashFunc, dest))) {
							log("INFO", c.path+" was created in this update and locally, but the local version matches the update. Skipping");
							continue;
						}
						conflictType = ConflictType.LOCAL_AND_REMOTE_CREATED;
					} else if (size == c.fromSize) {
						String hash = hash(c.fromHashFunc, dest);
						if (c.fromHash.equals(hash)) {
							log("INFO", c.path+" matches the expected from hash");
						} else if (c.toSize == size && c.toHash.equals(c.fromHashFunc == c.toHashFunc ? hash : hash(c.toHashFunc, dest))) {
							log("INFO", c.path+" matches the expected to hash, so has already been updated locally. Skipping");
							continue;
						} else {
							log("INFO", "CONFLICT: "+c.path+" doesn't match the expected from hash ("+hash+" != "+c.fromHash+")");
							normalConflict = true;
						}
					} else if (c.toSize == size && c.toHash.equals(hash(c.toHashFunc, dest))) {
						log("INFO", c.path+" matches the expected to hash, so has already been updated locally. Skipping");
						continue;
					} else {
						log("INFO", "CONFLICT: "+c.path+" doesn't match the expected from size ("+size+" != "+c.fromSize+")");
						normalConflict = true;
					}
					if (normalConflict) {
						if (c.toHash == null) {
							conflictType = ConflictType.LOCAL_CHANGED_REMOTE_DELETED;
						} else {
							conflictType = ConflictType.LOCAL_AND_REMOTE_CHANGED;
						}
					}
				} else {
					if (c.toHash == null) {
						log("INFO", c.path+" was deleted in this update, but it's already missing locally. Skipping");
						continue;
					} else if (c.fromHash != null) {
						conflictType = ConflictType.LOCAL_DELETED_REMOTE_CHANGED;
					}
				}
				if (conflictType != ConflictType.NO_CONFLICT) {
					AlertOption resp;
					if (conflictPreload.containsKey(conflictType)) {
						resp = conflictPreload.get(conflictType);
					} else {
						resp = openAlert("File conflict",
								"<b>The file "+c.path+" was "+conflictType.msg+".</b><br/>"
										+ "Do you want to replace it with the version from the update?<br/>"
										+ "Choose Cancel to abort. No files have been changed yet."+
										(dest.exists() ? "<br/><br/>If you choose Yes, a copy of the current file will be created at "+c.path+".orig." : ""),
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
						continue;
					} else if (resp == AlertOption.CANCEL) {
						log("INFO", "User cancelled error dialog! Exiting.");
						exit(EXIT_USER_REQUEST);
						return;
					}
					if (dest.exists()) {
						c.moveAside = true;
					}
				}
				progressDenom += c.toSize;
				c.dest = dest;
			}
			File tmp = new File(".unsup-tmp");
			if (!tmp.exists()) {
				tmp.mkdirs();
				try {
					Files.setAttribute(tmp.toPath(), "dos:hidden", true);
				} catch (Throwable t) {}
			}
			final long progressDenomf = progressDenom;
			AtomicLong progress = new AtomicLong();
			Runnable updateProgress = () -> updateProgress((int)((progress.get()*1000)/progressDenomf));
			updateTitle("Updating...", true);
			for (Change c : changesByPath.values()) {
				if (c.toSize == 0 || c.dest == null) {
					continue;
				}
				tellPuppet(":subtitle=Downloading "+c.path);
				URL blobUrl = new URL(src, "blobs/"+c.toHash.substring(0, 2)+"/"+c.toHash);
				URL u;
				if (c.url != null) {
					u = new URL(c.url);
				} else {
					u = blobUrl;
				}
				long origProgress = progress.get();
				DownloadedFile df;
				try {
					log("INFO", "Downloading "+c.path+" from "+u);
					df = downloadToFile(u, tmp, c.toSize, progress::addAndGet, updateProgress, c.toHashFunc);
					if (!df.hash.equals(c.toHash)) {
						throw new IOException("Hash mismatch on downloaded file for "+c.path+" from "+u+" - expected "+c.toHash+", got "+df.hash);
					}
				} catch (Throwable t) {
					if (c.url != null) {
						t.printStackTrace();
						progress.set(origProgress);
						log("WARN", "Failed to download "+c.path+" from specified URL, trying again from "+blobUrl);
						df = downloadToFile(blobUrl, tmp, c.toSize, progress::addAndGet, updateProgress, c.toHashFunc);
						if (!df.hash.equals(c.toHash)) {
							throw new IOException("Hash mismatch on downloaded file for "+c.path+" from "+u+" - expected "+c.toHash+", got "+df.hash);
						}
					} else {
						throw t;
					}
				}
				c.df = df;
			}
			updateTitle("Updating...", false);
			synchronized (dangerMutex) {
				tellPuppet(":subtitle=Applying changes. Do not force close the updater.");
				for (Change c : changesByPath.values()) {
					if (c.dest == null) continue;
					Path destPath = c.dest.toPath();
					if (c.dest.getParentFile() != null) Files.createDirectories(c.dest.getParentFile().toPath());
					if (c.moveAside) {
						Files.move(destPath, destPath.resolveSibling(destPath.getFileName().toString()+".orig"), StandardCopyOption.REPLACE_EXISTING);
					}
					if (c.toSize == 0) {
						if (c.toHash == null) {
							log("INFO", "Deleting "+c.path);
							Files.deleteIfExists(destPath);
						} else {
							if (c.dest.exists()) {
								try (FileOutputStream fos = new FileOutputStream(c.dest)) {
									// open and then immediately close the file to overwrite it with nothing
								}
							} else {
								c.dest.createNewFile();
							}
						}
					} else {
						Files.move(c.df.file.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				state.put("current_version", theirVersion.toJson());
				saveJson(stateFile, state);
			}
			ourVersion = theirVersion;
			log("INFO", "Update successful!");
			updated = true;
		} else if (ourVersion.code > theirVersion.code) {
			log("INFO", "Remote version is older than local version, doing nothing");
		} else {
			log("INFO", "We appear to be up-to-date. Nothing to do");
		}
		sourceVersion = ourVersion.name;
	}

	// helper methods, called more than once
	
	private static void tellPuppet(String order) {
		if (puppetOut == null) return;
		try {
			byte[] utf = order.getBytes(StandardCharsets.UTF_8);
			for (byte b : utf) {
				if (b == 0) {
					// replace NUL with overlong NUL
					puppetOut.write(0xC0);
					puppetOut.write(0x80);
				} else {
					puppetOut.write(b);
				}
			}
			puppetOut.write(0);
			puppetOut.flush();
		} catch (IOException e) {
			e.printStackTrace();
			log("WARN", "IO error while talking to puppet. Killing and continuing without GUI.");
			puppet.destroyForcibly();
			puppet = null;
			puppetOut = null;
		}
	}
	
	private static void updateTitle(String title, boolean determinate) {
		Agent.title = title;
		tellPuppet(":prog=0");
		tellPuppet(":mode="+(determinate ? "det" : "ind"));
		tellPuppet(":title="+title);
	}

	private static void updateProgress(int prog) {
		tellPuppet(":prog="+prog);
		if (Math.abs(lastReportedProgress-prog) >= 100 || System.nanoTime()-lastReportedProgressTime > TimeUnit.SECONDS.toNanos(3)) {
			lastReportedProgress = prog;
			lastReportedProgressTime = System.nanoTime();
			log("INFO", title+" "+(prog/10)+"%");
		}
	}

	private static QDIni mergePreset(QDIni config, String presetName) {
		URL u = Agent.class.getClassLoader().getResource("presets/"+presetName+".ini");
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
	
	private static void warnIfInsecure(HashFunction func) {
		if (func.insecure) {
			if (publicKey != null) {
				log("WARN", "Using insecure hash function "+func+" for a signed manifest! This is a very bad idea!");
			} else {
				log("WARN", "Using insecure hash function "+func);
			}
		}
	}
	
	private enum AlertMessageType { QUESTION, INFO, WARN, ERROR, NONE }
	private enum AlertOptionType { OK, OK_CANCEL, YES_NO, YES_NO_CANCEL, YES_NO_TO_ALL_CANCEL }
	private enum AlertOption { CLOSED, OK, YES, NO, CANCEL, YESTOALL, NOTOALL }
	
	private static AlertOption openAlert(String title, String body, AlertMessageType messageType, AlertOptionType optionType, AlertOption def) {
		if (puppetOut == null) {
			return def;
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			alertWaiters.put(name, latch);
			tellPuppet("["+name+"]:alert="+title+":"+body+":"+messageType.name().toLowerCase(Locale.ROOT)+":"+optionType.name().toLowerCase(Locale.ROOT).replace("_", ""));
			latch.awaitUninterruptibly();
			return AlertOption.valueOf(alertResults.remove(name).toUpperCase(Locale.ROOT));
		}
	}
	
	private static String openChoiceAlert(String title, String body, Collection<String> choices, String def) {
		if (puppetOut == null) {
			return def;
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			alertWaiters.put(name, latch);
			StringJoiner joiner = new StringJoiner("\u001C");
			choices.forEach(c -> joiner.add(c.replace(':', '\u001B')));
			tellPuppet("["+name+"]:alert="+title+":"+body+":choice="+joiner+":"+def);
			latch.awaitUninterruptibly();
			return alertResults.remove(name);
		}
	}
	
	private static String checkSchemeMismatch(URL src, String url) throws MalformedURLException {
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
			log("WARN", "Ignoring custom URL with bad scheme "+parsed.getProtocol());
		}
		return ok ? url : null;
	}
	
	private static byte[] loadAndVerify(URL src, int sizeLimit, URL sigUrl) throws IOException, JsonParserException {
		byte[] resp = downloadToMemory(src, sizeLimit);
		if (resp == null) {
			throw new IOException(src+" is larger than "+(sizeLimit/K)+"K, refusing to continue downloading");
		}
		if (publicKey != null && sigUrl != null) {
			try {
				byte[] sigResp = downloadToMemory(sigUrl, 512);
				EdDSAEngine engine = new EdDSAEngine();
				engine.initVerify(publicKey);
				if (!engine.verifyOneShot(resp, sigResp)) {
					throw new SignatureException("Signature is invalid");
				}
			} catch (Throwable t) {
				throw new IOException("Failed to validate signature for "+src, t);
			}
		}
		return resp;
	}
	
	private static String loadString(URL src, int sizeLimit, URL sigUrl) throws IOException, JsonParserException {
		return new String(loadAndVerify(src, sizeLimit, sigUrl), StandardCharsets.UTF_8);
	}
	
	private static JsonObject loadJson(URL src, int sizeLimit, URL sigUrl) throws IOException, JsonParserException {
		return JsonParser.object().from(new ByteArrayInputStream(loadAndVerify(src, sizeLimit, sigUrl)));
	}

	private static byte[] downloadToMemory(URL url, int sizeLimit) throws IOException {
		URLConnection conn = openConnection(url);
		byte[] resp = Util.collectLimited(conn.getInputStream(), sizeLimit);
		return resp;
	}

	private static URLConnection openConnection(URL url) throws IOException {
		URLConnection conn = url.openConnection();
		conn.setRequestProperty("User-Agent", "unsup/"+Util.VERSION);
		conn.setUseCaches(false);
		conn.setConnectTimeout(5000);
		conn.connect();
		if (conn instanceof HttpURLConnection) {
			HttpURLConnection http = (HttpURLConnection)conn;
			if (http.getResponseCode() != 200) {
				byte[] b = Util.collectLimited(http.getErrorStream(), 512);
				String s = b == null ? "(response too long)" : new String(b, StandardCharsets.UTF_8);
				throw new IOException("Received non-200 response from server for "+url+": "+http.getResponseCode()+"\n"+s);
			}
		}
		return conn;
	}
	
	private static class DownloadedFile {
		/** null if no hash function was specified */
		public final String hash;
		public final File file;
		private DownloadedFile(String hash, File file) {
			this.hash = hash;
			this.file = file;
		}
	}
	
	private static DownloadedFile downloadToFile(URL url, File dir, long size, LongConsumer addProgress, Runnable updateProgress, HashFunction hashFunc) throws IOException {
		URLConnection conn = openConnection(url);
		byte[] buf = new byte[16384];
		File file = File.createTempFile("download", "", dir);
		cleanup.add(file::delete);
		long readTotal = 0;
		long lastProgressUpdate = 0;
		MessageDigest digest = hashFunc == null ? null : hashFunc.createMessageDigest();
		try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
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
	
	private static String hash(HashFunction func, File f) throws IOException {
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
	
	private static boolean arrayContains(JsonArray arr, Object obj) {
		if (arr != null) {
			boolean anyMatch = false;
			for (Object en : arr) {
				if (Objects.equals(en, obj)) {
					anyMatch = true;
					break;
				}
			}
			if (!anyMatch) {
				return false;
			}
		}
		return true;
	}
	
	private static int checkManifestFlavor(JsonObject manifest, String flavor, IntPredicate versionPredicate) throws IOException {
		if (!manifest.containsKey("unsup_manifest")) throw new IOException("unsup_manifest key is missing");
		String str = manifest.getString("unsup_manifest");
		if (str == null) throw new IOException("unsup_manifest key is not a string");
		int dash = str.indexOf('-');
		if (dash == -1) throw new IOException("unsup_manifest value does not contain a dash");
		String lhs = str.substring(0, dash);
		if (!(flavor.equals(lhs))) throw new IOException("Manifest is of flavor "+lhs+", but we expected "+flavor);
		String rhs = str.substring(dash+1);
		int rhsI;
		try {
			rhsI = Integer.parseInt(rhs);
		} catch (IllegalArgumentException e) {
			throw new IOException("unsup_manifest value right-hand side is not a number: "+rhs);
		}
		if (!versionPredicate.test(rhsI)) throw new IOException("Don't know how to parse "+str+" manifest (version too new)");
		return rhsI;
	}
	
	private static void saveJson(File file, JsonObject obj) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			JsonWriter.on(fos).object(obj).done();
		}
	}
	
	private static void cleanup() {
		for (ExceptableRunnable er : cleanup) {
			try {
				er.run();
			} catch (Throwable t) {}
		}
		cleanup = null;
	}
	
	private static void exit(int code) {
		cleanup();
		System.exit(code);
	}
	
	private static synchronized void log(String flavor, String msg) {
		log(standalone ? "sync" : "agent", flavor, msg);
	}
	
	private static synchronized void log(String tag, String flavor, String msg) {
		String line = "["+logDateFormat.format(new Date())+"] [unsup "+tag+"/"+flavor+"]: "+msg;
		System.out.println(line);
		logStream.println(line);
	}
	
	private static volatile boolean awaitingExit = false;
	
	private static PrintStream logStream;
	
	private static boolean standalone;
	public static boolean filesystemIsCaseSensitive;
	
	private static List<ExceptableRunnable> cleanup = new ArrayList<>();
	private static Process puppet;
	private static QDIni config;
	
	private static String detectedEnv;
	private static Set<String> validEnvs;
	
	private static String title;
	private static int lastReportedProgress = 0;
	private static long lastReportedProgressTime = 0;
	
	private static Map<String, String> alertResults = new HashMap<>();
	private static Map<String, Latch> alertWaiters = new HashMap<>();
	
	private static EdDSAPublicKey publicKey;
	
	// read by the Unsup class when it loads
	// be careful not to load that class until this is all initialized
	public static String sourceVersion;
	public static boolean updated;

	private static OutputStream puppetOut;

	private static JsonObject state;

	private static File stateFile;
	
	private static class Version {
		public final String name;
		public final int code;
		
		public Version(String name, int code) {
			this.name = name;
			this.code = code;
		}
		
		public JsonObject toJson() {
			JsonObject obj = new JsonObject();
			obj.put("name", name);
			obj.put("code", code);
			return obj;
		}
		
		public static Version fromJson(JsonObject obj) {
			if (obj == null) return null;
			return new Version(obj.getString("name"), obj.getInt("code"));
		}
		
		@Override
		public String toString() {
			return name+" ("+code+")";
		}
	}
	
}
