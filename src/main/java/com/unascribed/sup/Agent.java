package com.unascribed.sup;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntPredicate;
import java.util.function.LongConsumer;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import com.unascribed.sup.QDIni.QDIniException;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;

class Agent {

	public static final String VERSION = "0.0.1";
	
	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_CONFIG_ERROR = 1;
	private static final int EXIT_CONSISTENCY_ERROR = 2;
	private static final int EXIT_BUG = 3;
	private static final int EXIT_USER_REQUEST = 4;
	
	private static final int K = 1024;
	private static final int M = K*1024;
	
	private static final long TENTH_SECOND_IN_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
	
	/** this mutex must be held while doing sensitive operations that shouldn't be interrupted */
	private static final Object dangerMutex = new Object();
	private static final SimpleDateFormat logDateFormat = new SimpleDateFormat("HH:mm:ss");
	
	public static void main(String[] args) throws UnsupportedEncodingException {
		standalone = true;
		premain(args.length >= 1 ? args[0] : null);
	}
	
	public static void premain(String arg) throws UnsupportedEncodingException {
		try {
			createLogStream();
			log("INFO", (standalone ? "Starting in standalone mode" : "Launch hijack successful")+". unsup v"+VERSION);
			detectFilesystemCaseSensitivity();
			if (!loadConfig()) {
				// there is no config file; yield control to the program
				return;
			}
			checkRequiredKeys("version", "source_format", "source");
			int version = config.getInt("version", -1);
			if (version != 1) {
				log("ERROR", "Config file error: Unknown version "+version+" at "+config.getBlame("version")+"! Exiting.");
				System.exit(EXIT_CONFIG_ERROR);
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
				System.exit(EXIT_CONFIG_ERROR);
				return;
			}
			
			if (!noGui) {
				createPuppet();
				cleanup.add(puppet::destroy);
			}
			
			if (config.containsKey("public_key")) {
				try {
					publicKey = new EdDSAPublicKey(new X509EncodedKeySpec(Base64.getDecoder().decode(config.get("public_key"))));
				} catch (Throwable t) {
					t.printStackTrace();
					log("ERROR", "Config file error: public_key is not valid! Exiting.");
					System.exit(EXIT_CONFIG_ERROR);
					return;
				}
			}
			
			File stateFile = new File(".unsup-state.json");
			JsonObject state;
			if (stateFile.exists()) {
				try (InputStream in = new FileInputStream(stateFile)) {
					state = JsonParser.object().from(in);
				} catch (Exception e) {
					e.printStackTrace();
					log("ERROR", "Couldn't load state file! Exiting.");
					System.exit(EXIT_CONSISTENCY_ERROR);
					return;
				}
			} else {
				state = new JsonObject();
			}
			
			PrintStream puppetOut;
			if (puppet != null) {
				puppetOut = new PrintStream(puppet.getOutputStream(), true, "UTF-8");
			} else {
				puppetOut = NullPrintStream.INSTANCE;
			}
			
			setPuppetColorsFromConfig(puppetOut);
			
			puppetOut.println(":build");
			
			puppetOut.println(":subtitle="+config.get("subtitle", ""));
			// we don't want to flash a window on the screen if things aren't going slow, so we tell
			// the puppet to wait 250ms before actually making the window visible, and assign an
			// identifier to our order so we can belay it later if we finished before the timer
			// expired
			puppetOut.println("[openTimeout]250:visible=true");

			
			updateTitle(puppetOut, "Checking for updates...", false);
			try {
				if (fmt == SourceFormat.UNSUP) {
					log("INFO", "Loading unsup-format manifest from "+src);
					JsonObject manifest = loadJson(src, 32*K, null);
					checkManifestFlavor(manifest, "root", IntPredicates.equals(1));
					Version ourVersion = Version.fromJson(state.getObject("current_version"));
					if (!manifest.containsKey("versions")) throw new IOException("Manifest is missing versions field");
					Version theirVersion = Version.fromJson(manifest.getObject("versions").getObject("current"));
					if (theirVersion == null) throw new IOException("Manifest is missing current version field");
					if (ourVersion == null) {
						if (manifest.getBoolean("bootstrappable", false)) {
							log("INFO", "Update available! We have nothing, they have "+theirVersion+" and are bootstrappable");
							JsonObject bootstrap = loadJson(new URL(src, "bootstrap.json"), 2*M, new URL(src, "bootstrap.sig"));
							checkManifestFlavor(bootstrap, "bootstrap", IntPredicates.equals(1));
							Version bootstrapVersion = Version.fromJson(bootstrap.getObject("version"));
							if (bootstrapVersion == null) throw new IOException("Bootstrap manifest is missing version field");
							if (bootstrapVersion.code < theirVersion.code) {
								log("WARN", "Bootstrap manifest version "+bootstrapVersion+" is older than root manifest version "+theirVersion+", will have to perform extra updates");
							}
							HashFunction func = HashFunction.valueOf(manifest.getString("hash_function", "sha256").toUpperCase(Locale.ROOT));
							updateTitle(puppetOut, "Bootstrapping...", false);
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
								String url = file.getString("url");
								JsonArray envs = file.getArray("envs");
								if (envs != null) {
									boolean anyMatch = false;
									for (Object env : envs) {
										if (Objects.equals(env, detectedEnv)) {
											anyMatch = true;
											break;
										}
									}
									if (!anyMatch) {
										log("INFO", "Skipping "+path+" as it's not eligible for env "+detectedEnv);
										continue;
									}
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
								try {
									Files.setAttribute(tmp.toPath(), "dos:hidden", true);
								} catch (Throwable t) {}
							}
							updateTitle(puppetOut, "Bootstrapping...", true);
							final long progressDenomf = progressDenom;
							AtomicLong progress = new AtomicLong();
							File wd = new File("");
							for (FileToDownload ftd : todo) {
								puppetOut.println(":subtitle=Downloading "+ftd.path);
								URL u;
								if (ftd.url != null) {
									u = new URL(ftd.url);
								} else {
									u = new URL(src, "blobs/"+ftd.hash.substring(0, 2)+"/"+ftd.hash);
								}
								DownloadedFile df = downloadToFile(u, tmp, ftd.size, progress::addAndGet, () -> updateProgress(puppetOut, (int)((progress.get()*1000)/progressDenomf)), func);
								if (!df.hash.equals(ftd.hash)) {
									throw new IOException("Hash mismatch on downloaded file for "+ftd.path+" from "+u+" - expected "+ftd.hash+", got "+df.hash);
								}
								File dest = new File(ftd.path);
								if (!dest.getAbsolutePath().startsWith(wd.getAbsolutePath()+"/"))
									throw new IOException("Refusing to download to a file outside of working directory");
								if (dest.exists()) {
									AlertOption resp = openAlert(puppetOut, "File conflict",
											"<b>The file "+ftd.path+" already exists.</b><br/>Do you want to replace it?<br/>Choose Cancel to abort. No files have been changed yet.",
											AlertMessageType.QUESTION, AlertOptionType.YES_NO_CANCEL, AlertOption.YES);
									if (resp == AlertOption.NO) {
										continue;
									} else if (resp == AlertOption.CANCEL) {
										log("INFO", "User cancelled error dialog! Exiting.");
										System.exit(EXIT_USER_REQUEST);
										return;
									}
								}
								ftd.df = df;
								ftd.dest = dest;
							}
							synchronized (dangerMutex) {
								for (FileToDownload ftd : todo) {
									Files.createDirectories(ftd.dest.getParentFile().toPath());
									Files.move(ftd.df.file.toPath(), ftd.dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
								}
								state.put("current_version", bootstrapVersion.toJson());
								saveState(stateFile, state);
							}
							ourVersion = bootstrapVersion;
							log("INFO", "Bootstrap successful!");
							updated = true;
						} else {
							throw new IOException("Attempting to bootstrap from a non-bootstrappable manifest");
						}
					}
					if (theirVersion.code > ourVersion.code) {
						log("INFO", "Update available! We have "+ourVersion+", they have "+theirVersion);
					} else if (ourVersion.code > theirVersion.code) {
						log("INFO", "Remote version is older than local version, doing nothing.");
					} else {
						log("INFO", "We appear to be up-to-date.");
					}
					sourceVersion = ourVersion.name;
				}
			} catch (Throwable t) {
				t.printStackTrace();
				log("WARN", "Error while updating");
				puppetOut.println(":expedite=openTimeout");
				if (openAlert(puppetOut, "unsup error",
						"<b>An error occurred while attempting to update.</b><br/>See the log for more info."+(standalone ? "" : "<br/>Choose Cancel to abort launch."),
						AlertMessageType.ERROR, standalone ? AlertOptionType.OK : AlertOptionType.OK_CANCEL, AlertOption.OK) == AlertOption.CANCEL) {
					log("INFO", "User cancelled error dialog! Exiting.");
					System.exit(EXIT_USER_REQUEST);
					return;
				}
			} finally {
				puppetOut.println(":subtitle=");
			}
			
			if (awaitingExit) blockForever();
			
			puppetOut.println(":belay=openTimeout");
			puppetOut.println(":exit");
			if (puppet != null) {
				log("INFO", "Waiting for puppet to exit...");
				try {
					if (!puppet.waitFor(2, TimeUnit.SECONDS)) {
						log("INFO", "Tired of waiting, killing the puppet.");
						puppet.destroyForcibly();
					}
				} catch (InterruptedException e) {
				}
			}
			
			if (standalone) {
				log("INFO", "Ran in standalone mode, no program will be started.");
				log("INFO", "It is recommended you use unsup as a Java agent via -javaagent:unsup.jar to piggyback on another program's launch.");
			} else {
				log("INFO", "All done, handing over control.");
				// poke the Unsup class so it loads and finalizes all of its values
				Unsup.SOURCE_VERSION.toString();
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
			System.exit(EXIT_CONFIG_ERROR);
			return;
		} finally {
			for (ExceptableRunnable er : cleanup) {
				try {
					er.run();
				} catch (Throwable t) {}
			}
			cleanup = null;
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
				System.exit(EXIT_CONFIG_ERROR);
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
				System.exit(EXIT_CONFIG_ERROR);
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
				return containsWholeWord(cmd, "nogui") || containsWholeWord(cmd, "--nogui");
			}
		}
		return false;
	}

	private static void detectEnv(String forcedEnv) {
		if (standalone && forcedEnv == null) {
			log("ERROR", "Cannot sync an env-based config in standalone mode unless an argument is given specifying the env! Exiting.");
			System.exit(EXIT_CONFIG_ERROR);
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
			System.exit(EXIT_CONFIG_ERROR);
			return;
		}
		if (ourEnv == null) {
			log("ERROR", "use_envs is true, and we found no env markers! Checked for the following markers:");
			for (String s : checkedMarkers) {
				log("ERROR", "- "+s);
			}
			log("ERROR", "Exiting.");
			System.exit(EXIT_CONFIG_ERROR);
			return;
		}
		if (!foundEnvs.contains(ourEnv)) {
			log("ERROR", "Invalid env specified: \""+ourEnv+"\"! Valid envs:");
			for (String s : foundEnvs) {
				log("ERROR", "- "+s);
			}
			log("ERROR", "Exiting.");
			System.exit(EXIT_CONFIG_ERROR);
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
									System.exit(EXIT_USER_REQUEST);
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
	
	private static void setPuppetColorsFromConfig(PrintStream puppetOut) {
		puppetOut.println(":colorBackground="+config.get("colors.background", "000000"));
		puppetOut.println(":colorTitle="+config.get("colors.title", "FFFFFF"));
		puppetOut.println(":colorSubtitle="+config.get("colors.subtitle", "AAAAAA"));
		
		puppetOut.println(":colorProgress="+config.get("colors.progress", "FF0000"));
		puppetOut.println(":colorProgressTrack="+config.get("colors.progress_track", "AAAAAA"));
		
		puppetOut.println(":colorDialog="+config.get("colors.dialog", "FFFFFF"));
		puppetOut.println(":colorButton="+config.get("colors.button", "FFFFFF"));
		puppetOut.println(":colorButtonText="+config.get("colors.button_text", "FFFFFF"));
	}

	// helper methods, called more than once
	
	private static void updateTitle(PrintStream puppetOut, String title, boolean determinate) {
		Agent.title = title;
		puppetOut.println(":mode="+(determinate ? "det" : "ind"));
		puppetOut.println(":title="+title);
	}

	private static void updateProgress(PrintStream puppetOut, int prog) {
		puppetOut.println(":prog="+prog);
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
			System.exit(EXIT_CONFIG_ERROR);
			return null;
		}
		try (InputStream in = u.openStream()) {
			QDIni preset = QDIni.load("<preset "+presetName+">", in);
			config = preset.merge(config);
		} catch (IOException e) {
			e.printStackTrace();
			log("ERROR", "Failed to load preset "+presetName+"! Exiting.");
			System.exit(EXIT_CONFIG_ERROR);
			return null;
		}
		return config;
	}
	
	private static boolean containsWholeWord(String haystack, String needle) {
		if (haystack == null || needle == null) return false;
		return haystack.equals(needle) || haystack.endsWith(" "+needle) || haystack.startsWith(needle+" ") || haystack.contains(" "+needle+" ");
	}
	
	private enum AlertMessageType { QUESTION, INFO, WARN, ERROR, NONE }
	private enum AlertOptionType { OK, OK_CANCEL, YES_NO, YES_NO_CANCEL }
	private enum AlertOption { CLOSED, OK, YES, NO, CANCEL }
	
	private static AlertOption openAlert(PrintStream puppetOut, String title, String body, AlertMessageType messageType, AlertOptionType optionType, AlertOption def) {
		if (puppetOut == NullPrintStream.INSTANCE) {
			return def;
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			alertWaiters.put(name, latch);
			puppetOut.println("["+name+"]:alert="+title+":"+body+":"+messageType.name().toLowerCase(Locale.ROOT)+":"+optionType.name().toLowerCase(Locale.ROOT).replace("_", ""));
			latch.awaitUninterruptibly();
			return AlertOption.valueOf(alertResults.remove(name).toUpperCase(Locale.ROOT));
		}
	}
	
	private static void blockForever() {
		while (true) {
			try {
				Thread.sleep(Integer.MAX_VALUE);
			} catch (InterruptedException e) {}
		}
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
	
	private static JsonObject loadJson(URL src, int sizeLimit, URL sigUrl) throws IOException, JsonParserException {
		byte[] resp = downloadToMemory(src, sizeLimit);
		if (resp == null) {
			throw new IOException("File is larger than "+(sizeLimit/K)+"K, refusing to continue downloading");
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
				throw new IOException("Failed to validate signature for "+src.getFile(), t);
			}
		}
		return JsonParser.object().from(new ByteArrayInputStream(resp));
	}

	private static byte[] downloadToMemory(URL url, int sizeLimit) throws IOException {
		URLConnection conn = openConnection(url);
		byte[] resp = collectLimited(conn.getInputStream(), sizeLimit);
		return resp;
	}

	private static URLConnection openConnection(URL url) throws IOException {
		URLConnection conn = url.openConnection();
		conn.setRequestProperty("User-Agent", "unsup/"+VERSION);
		conn.setUseCaches(false);
		conn.setConnectTimeout(5000);
		conn.connect();
		if (conn instanceof HttpURLConnection) {
			HttpURLConnection http = (HttpURLConnection)conn;
			if (http.getResponseCode() != 200) {
				byte[] b = collectLimited(http.getErrorStream(), 512);
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
	
	private static DownloadedFile downloadToFile(URL url, File dir, long size) throws IOException {
		return downloadToFile(url, dir, size);
	}
	
	private static DownloadedFile downloadToFile(URL url, File dir, long size, LongConsumer addProgress, Runnable updateProgress, HashFunction hashFunc) throws IOException {
		URLConnection conn = openConnection(url);
		byte[] buf = new byte[4096];
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
				if (updateProgress != null && System.nanoTime()-lastProgressUpdate > TENTH_SECOND_IN_NANOS) {
					lastProgressUpdate = System.nanoTime();
					updateProgress.run();
				}
				try {
					// TODO remove
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
		}
		if (updateProgress != null) {
			updateProgress.run();
		}
		String hash = null;
		if (digest != null) {
			hash = toHexString(digest.digest());
		}
		return new DownloadedFile(hash, file);
	}
	
	private static final String[] hex = {
		"00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "0a", "0b", "0c", "0d", "0e", "0f",
		"10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "1a", "1b", "1c", "1d", "1e", "1f",
		"20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "2a", "2b", "2c", "2d", "2e", "2f",
		"30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "3a", "3b", "3c", "3d", "3e", "3f",
		"40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "4a", "4b", "4c", "4d", "4e", "4f",
		"50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "5a", "5b", "5c", "5d", "5e", "5f",
		"60", "61", "62", "63", "64", "65", "66", "67", "68", "69", "6a", "6b", "6c", "6d", "6e", "6f",
		"70", "71", "72", "73", "74", "75", "76", "77", "78", "79", "7a", "7b", "7c", "7d", "7e", "7f",
		"80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "8a", "8b", "8c", "8d", "8e", "8f",
		"90", "91", "92", "93", "94", "95", "96", "97", "98", "99", "9a", "9b", "9c", "9d", "9e", "9f",
		"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "aa", "ab", "ac", "ad", "ae", "af",
		"b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "ba", "bb", "bc", "bd", "be", "bf",
		"c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "ca", "cb", "cc", "cd", "ce", "cf",
		"d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "da", "db", "dc", "dd", "de", "df",
		"e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "ea", "eb", "ec", "ed", "ee", "ef",
		"f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "fa", "fb", "fc", "fd", "fe", "ff"
	};
	
	private static String toHexString(byte[] bys) {
		StringBuilder sb = new StringBuilder(bys.length*2);
		for (int i = 0; i < bys.length; i++) {
			sb.append(hex[bys[i]&0xFF]);
		}
		return sb.toString();
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
	
	private static void saveState(File stateFile, JsonObject state) throws IOException {
		boolean didExist = stateFile.exists();
		try (FileOutputStream fos = new FileOutputStream(stateFile)) {
			JsonWriter.on(fos).object(state).done();
		}
		if (!didExist) {
			try {
				Files.setAttribute(stateFile.toPath(), "dos:hidden", true);
			} catch (Throwable t) {}
		}
	}
	
	private static synchronized void log(String flavor, String msg) {
		log(standalone ? "sync" : "agent", flavor, msg);
	}
	
	private static synchronized void log(String tag, String flavor, String msg) {
		String line = "["+logDateFormat.format(new Date())+"] [unsup "+tag+"/"+flavor+"]: "+msg;
		System.out.println(line);
		logStream.println(line);
	}
	
	private static boolean awaitingExit = false;
	
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
