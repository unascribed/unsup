package com.unascribed.sup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import com.unascribed.sup.PuppetHandler.AlertMessageType;
import com.unascribed.sup.PuppetHandler.AlertOption;
import com.unascribed.sup.PuppetHandler.AlertOptionType;
import com.unascribed.sup.data.ConflictType;
import com.unascribed.sup.data.SourceFormat;
import com.unascribed.sup.handler.PackwizHandler;
import com.unascribed.sup.handler.NativeHandler;
import com.unascribed.sup.handler.AbstractFormatHandler.CheckResult;
import com.unascribed.sup.handler.AbstractFormatHandler.FilePlan;
import com.unascribed.sup.handler.AbstractFormatHandler.FileState;
import com.unascribed.sup.handler.AbstractFormatHandler.UpdatePlan;
import com.unascribed.sup.pieces.ExceptableRunnable;
import com.unascribed.sup.pieces.MemoryCookieJar;
import com.unascribed.sup.pieces.QDIni;
import com.unascribed.sup.pieces.QDIni.QDIniException;
import com.unascribed.sup.signing.SigProvider;
import com.unascribed.sup.util.RequestHelper;
import com.unascribed.sup.util.RequestHelper.DownloadedFile;
import com.unascribed.sup.util.RequestHelper.Retry;
import com.unascribed.sup.util.Strings;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.brotli.BrotliInterceptor;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okhttp3.tls.HandshakeCertificates;

public class Agent {

	public static final int EXIT_SUCCESS = 0;
	public static final int EXIT_CONFIG_ERROR = 1;
	public static final int EXIT_CONSISTENCY_ERROR = 2;
	public static final int EXIT_BUG = 3;
	public static final int EXIT_USER_REQUEST = 4;

	static volatile boolean awaitingExit = false;
	
	static boolean standalone;
	
	public static List<ExceptableRunnable> cleanup = new ArrayList<>();
	public static QDIni config;
	
	public static String detectedEnv;
	public static Set<String> validEnvs;
	
	public static final SigProvider unsupSig = SigProvider.of("signify RWTSwM40VCzVER3YWt55m4Fvsg0sjZLEICikuU3cD91gR/2lii/jk67B");
	public static SigProvider packSig;
	
	// read by the Unsup class when it loads
	// be careful not to load that class until this is all initialized
	public static String sourceVersion;
	public static boolean updated;
	
	private static boolean updatedComponents;

	public static JsonObject state;
	private static File stateFile;
	
	/** this mutex must be held while doing sensitive operations that shouldn't be interrupted */
	static final Object dangerMutex = new Object();
	
	public static OkHttpClient okhttp;
	
	public static void main(String[] args) {
		standalone = true;
		premain(args.length >= 1 ? args[0] : null);
	}
	
	public static void premain(String arg) {
		long start = System.nanoTime();
		try {
			Log.init();
			Log.info((standalone ? "Starting in standalone mode" : "Launch hijack successful")+". unsup v"+Util.VERSION);
			if (!loadConfig()) {
				Log.warn("Cannot find a config file, giving up.");
				// by returning but not exiting, we yield control to the program whose launch we hijacked, if any
				return;
			}
			if (config.getBoolean("use_envs", false)) {
				detectEnv(config.get("force_env", arg));
			}
			
			boolean noGui = determineNoGui();
			
			SourceFormat fmt = config.getEnum("source_format", SourceFormat.class, null);
			URI src;
			try {
				src = new URI(config.get("source"));
			} catch (URISyntaxException e) {
				Log.error("Config error: source URL is malformed! "+e.getMessage()+". Exiting.");
				exit(EXIT_CONFIG_ERROR);
				return;
			}
			
			if (!noGui) {
				PuppetHandler.create();
				cleanup.add(PuppetHandler::destroy);
			}
			
			if (config.containsKey("public_key")) {
				try {
					packSig = SigProvider.parse(config.get("public_key"));
					cleanup.add(() -> packSig = null);
				} catch (Throwable t) {
					Log.error("Config file error: public_key is not valid at "+config.getBlame("public_key")+"! Exiting.", t);
					exit(EXIT_CONFIG_ERROR);
					return;
				}
			}
			
			if (config.getBoolean("update_mmc_pack", false)) {
				MMCUpdater.scan();
			}
			
			stateFile = new File(".unsup-state.json");
			if (stateFile.exists()) {
				try (InputStream in = new FileInputStream(stateFile)) {
					state = JsonParser.object().from(in);
				} catch (Exception e) {
					Log.error("Couldn't load state file! Exiting.", e);
					exit(EXIT_CONSISTENCY_ERROR);
					return;
				}
			} else {
				state = new JsonObject();
			}
			
			PuppetHandler.sendConfig();
			
			PuppetHandler.tellPuppet(":build");
			PuppetHandler.tellPuppet(":subtitle="+config.get("subtitle", ""));
			// we don't want to flash a window on the screen if things aren't going slow, so we tell
			// the puppet to wait before actually making the window visible, and assign an id to our
			// order so we can belay it later if we finished before the timer expired
			PuppetHandler.tellPuppet("[openTimeout]1250:visible=true");
			
			setupOkHttp();
			checkForUpdate(fmt, src, SysProps.DISABLE_RECONCILIATION, false);

			if (awaitingExit) Agent.blockForever();

			PuppetHandler.tellPuppet(":belay=openTimeout");
			if (updatedComponents) {
				PuppetHandler.openAlert("dialog.component_update.title",
						"dialog.component_update",
						PuppetHandler.AlertMessageType.INFO, AlertOptionType.OK, AlertOption.OK);
			} else if (PuppetHandler.puppetOut != null) {
				Log.info("Waiting for puppet to complete done animation...");
				PuppetHandler.tellPuppet(":title=title.done");
				PuppetHandler.tellPuppet(":mode=done");
				if (!PuppetHandler.puppet.waitFor(3, TimeUnit.SECONDS)) {
					Log.warn("Tired of waiting, killing the puppet.");
					PuppetHandler.puppet.destroyForcibly();
				}
			}
			
			if (updatedComponents) {
				Log.info("A component update has been applied - exiting for game restart.");
				exit(EXIT_SUCCESS);
			} else if (standalone) {
				Log.info("Ran in standalone mode, no program will be started.");
			} else {
				Log.info("All done, handing over control.");
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
			Log.error("Config file error: "+e.getMessage()+"! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		} catch (InterruptedException e) {
			throw new AssertionError(e);
		} finally {
			Log.info("Finished after "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)+"ms.");
			cleanup();
		}
	}

	// "step" methods, called only once, exist to turn premain() into a logical overview that can be
	// drilled down into as necessary
	
	private static boolean loadConfig() {
		File configFile = new File("unsup.ini");
		if (configFile.exists()) {
			try {
				config = QDIni.load(configFile);
				cleanup.add(() -> config = null);
				Log.debug("Found and loaded unsup.ini. What secrets does it hold?");
			} catch (Exception e) {
				Log.error("Found unsup.ini, but couldn't parse it! Exiting.", e);
				exit(EXIT_CONFIG_ERROR);
			}
			checkRequiredKeys("version", "source_format", "source");
			int version = config.getInt("version", -1);
			if (version != 1) {
				Log.error("Config file error: Unknown version "+version+" at "+config.getBlame("version")+"! Exiting.");
				exit(EXIT_CONFIG_ERROR);
				return false;
			}
			String lang = Locale.getDefault().toLanguageTag();
			if (SysProps.LANGUAGE != null) lang = SysProps.LANGUAGE;
			Log.debug("Language: "+lang);
			if (!"en-US".equals(lang)) {
				config = mergePreset(config, "lang/"+lang, false);
			}
			config = mergePreset(config, "lang/en-US", true);
			config = mergePreset(config, "__global__", false);
			if (config.containsKey("preset")) {
				config = mergePreset(config, config.get("preset"), true);
			}
			return true;
		} else {
			Log.warn("No config file found? Doing nothing.");
			return false;
		}
	}
	
	private static void checkRequiredKeys(String... requiredKeys) {
		for (String req : requiredKeys) {
			if (!config.containsKey(req)) {
				Log.error("Config file error: "+req+" is required, but was not defined! Exiting.");
				exit(EXIT_CONFIG_ERROR);
				return;
			}
		}
	}
	
	private static boolean determineNoGui() {
		if (standalone) return !SysProps.GUI_IN_STANDALONE;
		if (config.containsKey("no_gui")) return config.getBoolean("no_gui", false);
		if (config.getBoolean("recognize_nogui", false)) {
			String cmd = System.getProperty("sun.java.command");
			if (cmd != null) {
				return Strings.containsWholeWord(cmd, "nogui") || Strings.containsWholeWord(cmd, "--nogui");
			}
		}
		return false;
	}

	private static void detectEnv(String forcedEnv) {
		if (standalone && forcedEnv == null) {
			Log.error("Cannot sync an env-based config in standalone mode unless an argument is given specifying the env! Exiting.");
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
			Log.error("use_envs is true, but found no env declarations! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		}
		if (ourEnv == null) {
			Log.error("use_envs is true, and we found no env markers! Checked for the following markers:");
			for (String s : checkedMarkers) {
				Log.error("- "+s);
			}
			Log.error("Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		}
		if (!foundEnvs.contains(ourEnv)) {
			Log.error("Invalid env specified: \""+ourEnv+"\"! Valid envs:");
			for (String s : foundEnvs) {
				Log.error("- "+s);
			}
			Log.error("Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		}
		if (standalone) {
			Log.info("Declared env is "+ourEnv);
		} else {
			Log.info("Detected env is "+ourEnv);
		}
		detectedEnv = ourEnv;
		validEnvs = foundEnvs;
		cleanup.add(() -> validEnvs = null);
	}

	private static void setupOkHttp() throws AssertionError {
		Dns dns = Dns.SYSTEM;
		HandshakeCertificates certs = new HandshakeCertificates.Builder()
				.addPlatformTrustedCertificates()
				// certs not shipped by old Mojang Java (they're staying on some antique version for Intel Windows driver bs reasons)
				.addTrustedCertificate(CACerts.ISRG_ROOT_X1) // Let's Encrypt
				.addTrustedCertificate(CACerts.USERTRUST_ECC) // Sectigo (GitHub)
				.addTrustedCertificate(CACerts.AMAZON_ROOT_CA_1) // CurseForge
				.addTrustedCertificate(CACerts.GTS_ROOT_R4) // Modrinth
				.build();
		OkHttpClient bootstrapOkhttp = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.callTimeout(120, TimeUnit.SECONDS)
			.sslSocketFactory(certs.sslSocketFactory(), certs.trustManager())
			.addInterceptor(BrotliInterceptor.INSTANCE)
			.build();
		switch (config.get("dns", "system")) {
			case "system":
				Log.debug("Using system DNS for DNS queries");
				dns = Dns.SYSTEM;
				break;
			case "quad9": {
				List<InetAddress> quad9Hosts;
				try {
					quad9Hosts = Arrays.asList(
						InetAddress.getByName("9.9.9.10"),
						InetAddress.getByName("2620:fe::10"),
						InetAddress.getByName("149.112.112.10"),
						InetAddress.getByName("2620:fe::fe:10")
					);
				} catch (UnknownHostException e) {
					// not a possible throw for a well-formed IP string
					throw new AssertionError(e);
				}
				dns = new DnsOverHttps.Builder()
						.url(HttpUrl.parse("https://dns10.quad9.net/dns-query"))
						.bootstrapDnsHosts(quad9Hosts)
						.client(bootstrapOkhttp)
						.build();
				Log.debug("Using Quad9 for DNS queries");
				break;
			}
			default: {
				String dnsStr = config.get("dns");
				if (dnsStr.startsWith("https://")) {
					dns = new DnsOverHttps.Builder()
							.url(HttpUrl.parse(dnsStr))
							.client(bootstrapOkhttp)
							.build();
					Log.debug("Using "+dnsStr+" for DNS queries");
				} else {
					Log.error("Config file error: dns is not valid at "+config.getBlame("dns")+" - expected 'system', 'quad9', or an HTTPS URL, but got '"+dnsStr+"'! Exiting.");
					exit(EXIT_CONFIG_ERROR);
					return;
				}
				break;
			}
		}
		okhttp = bootstrapOkhttp.newBuilder()
				.cookieJar(new MemoryCookieJar())
				.dns(dns)
				.build();
	}
	
	private static boolean checkForUpdate(SourceFormat fmt, URI src, boolean autoaccept, boolean reentering) {
		PuppetHandler.updateTitle("title.checking", false);
		try {
			CheckResult res = null;
			if ("merge".equals(src.getScheme())) {
				Log.warn("Using an experimental feature: Manifest merging");
				JsonObject realState = state;
				JsonObject mergeStates = realState.getObject("mergeStates");
				if (mergeStates == null) {
					mergeStates = new JsonObject();
					realState.put("mergeStates", mergeStates);
				}
				for (String s : src.getRawSchemeSpecificPart().split(";")) {
					JsonObject thisState = mergeStates.getObject(s, new JsonObject());
					state = thisState;
					if (checkForUpdate(fmt, new URI(s), autoaccept, true)) {
						// if the user has accepted an update, then accept the rest of them implicitly
						autoaccept = true;
					}
					mergeStates.put(s, state);
				}
				state = realState;
				saveState();
				return true;
			} else {
				Log.debug("Retrieving from "+src+" in "+fmt+" format");
				if (fmt == SourceFormat.UNSUP) {
					res = NativeHandler.check(src, autoaccept);
				} else if (fmt == SourceFormat.PACKWIZ) {
					res = PackwizHandler.check(src, autoaccept);
				} else {
					throw new AssertionError();
				}
			}
			if (res != null) {
				sourceVersion = res.ourVersion.name;
				if (res.plan != null) {
					applyUpdate(res);
					if (!reentering) saveState();
					return true;
				}
			}
			return false;
		} catch (Throwable t) {
			Log.warn("Error while updating", t);
			PuppetHandler.tellPuppet(":expedite=openTimeout");
			if (PuppetHandler.openAlert("dialog.error.title",
					"dialog.error."+(standalone ? "standalone" : "normal"),
					PuppetHandler.AlertMessageType.ERROR, standalone ? AlertOptionType.OK : AlertOptionType.OK_CANCEL, AlertOption.OK) == AlertOption.CANCEL) {
				Log.info("User cancelled error dialog! Exiting.");
				exit(EXIT_USER_REQUEST);
			}
			return false;
		} finally {
			PuppetHandler.tellPuppet(":subtitle=");
		}
	}
	
	private static void applyUpdate(CheckResult res) throws IOException {
		UpdatePlan<?> plan = res.plan;
		boolean bootstrapping = plan.isBootstrap;
		Log.debug("Alright, so here's what I'm thinking:");
		Set<String> unchanged = new HashSet<>(plan.expectedState.keySet());
		for (Map.Entry<String, ? extends FilePlan> en : plan.files.entrySet()) {
			unchanged.remove(en.getKey());
			FileState from = plan.expectedState.get(en.getKey());
			FilePlan to = en.getValue();
			Log.debug("- "+en.getKey()+" is currently "+ponder(from));
			Log.debug("  It has been changed to "+ponder(to.state));
			if (to.url != null) {
				if (to.fallbackUrl != null) {
					Log.debug("  I'll be grabbing that from "+to.url+" (or "+to.fallbackUrl+" if that doesn't work)");
				} else {
					Log.debug("  I'll be grabbing that from "+to.url);
				}
			}
		}
		Log.debug(unchanged.size()+" other file"+(unchanged.size() == 1 ? "" : "s")+" have not been changed.");
		for (Map.Entry<String, String> en : res.componentVersions.entrySet()) {
			String ours = MMCUpdater.currentComponentVersions.get(en.getKey());
			if (ours != null && !ours.equals(en.getValue())) {
				Log.debug("Component "+en.getKey()+" will be updated from "+ours+" to "+en.getValue());
			}
		}
		if (SysProps.DEBUG) {
			Log.debug("Sound good? You have 4 seconds to kill the process if not.");
			try {
				Thread.sleep(4000);
			} catch (InterruptedException e) {
			}
			Log.debug("Continuing.");
		}
		long progressDenom = 1;
		File wd = new File("");
		PuppetHandler.updateSubtitle("subtitle.verifying");
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
					if (to.sizeMatches(size) && to.hash.equals(RequestHelper.hash(to.func, dest))) {
						Log.info(path+" was created in this update and locally, but the local version matches the update. Skipping");
						continue;
					}
					conflictType = ConflictType.LOCAL_AND_REMOTE_CREATED;
				} else if (from.sizeMatches(size)) {
					String hash = RequestHelper.hash(from.func, dest);
					if (from.hash.equals(hash)) {
						Log.debug(path+" matches the expected from hash");
					} else if (to.sizeMatches(size) && to.hash.equals(from.func == to.func ? hash : RequestHelper.hash(to.func, dest))) {
						Log.info(path+" matches the expected to hash, so has already been updated locally. Skipping");
						continue;
					} else {
						Log.info("CONFLICT: "+path+" doesn't match the expected from hash ("+hash+" != "+from.hash+")");
						normalConflict = true;
					}
				} else if (to.sizeMatches(size) && to.hash.equals(RequestHelper.hash(to.func, dest))) {
					Log.info(path+" matches the expected to hash, so has already been updated locally. Skipping");
					continue;
				} else {
					Log.info("CONFLICT: "+path+" doesn't match the expected from size ("+size+" != "+from.size+")");
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
					Log.info(path+" was deleted in this update, but it's already missing locally. Skipping");
					continue;
				} else if (from.hash != null) {
					conflictType = ConflictType.LOCAL_DELETED_REMOTE_CHANGED;
				}
			}
			if (conflictType != ConflictType.NO_CONFLICT) {
				AlertOption resp;
				if (SysProps.DISABLE_RECONCILIATION) {
					resp = AlertOption.YES;
				} else if (conflictPreload.containsKey(conflictType)) {
					resp = conflictPreload.get(conflictType);
				} else {
					resp = PuppetHandler.openAlert("dialog.conflict.title",
							"dialog.conflict.leadin."+conflictType.translationKey+"¤"+path+"¤dialog.conflict.body¤"+(dest.exists() ? "dialog.conflict.aside_trailer" : ""),
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
					Log.info("User cancelled conflict dialog! Exiting.");
					exit(EXIT_USER_REQUEST);
					return;
				}
				if (dest.exists() && !SysProps.DISABLE_RECONCILIATION) {
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
		PuppetHandler.updateTitle(bootstrapping ? "title.bootstrapping" : "title.updating", true);
		ExecutorService svc = Executors.newFixedThreadPool(6);
		Set<String> files = new HashSet<>();
		List<Future<?>> futures = new ArrayList<>();
		Map<FilePlan, DownloadedFile> downloads = new IdentityHashMap<>();
		Runnable updateSubtitle = () -> {
			if (files.size() == 0) {
				PuppetHandler.updateSubtitle("subtitle.downloading_indeterminate");
			} else if (files.size() == 1) {
				PuppetHandler.updateSubtitle("subtitle.downloading¤"+files.iterator().next());
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
				PuppetHandler.updateSubtitle("subtitle.downloading¤"+sb);
			}
		};
		for (Map.Entry<String, ? extends FilePlan> en : plan.files.entrySet()) {
			String path = en.getKey();
			FilePlan f = en.getValue();
			if (f.skip) {
				Log.info("Skipping download of "+path);
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
						try (InputStream in = RequestHelper.get(f.primerUrl, f.hostile)) {
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
						if ("file".equals(f.url.getScheme())) {
							Log.info("Copying "+path);
						} else {
							Log.info("Downloading "+path+" from "+describe(f.url));
						}
						df = downloadAndCheckHash(tmp, progress, updateProgress, path, f, f.url, to, contributedProgress);
						if (to.size == -1) progress.incrementAndGet();
					} catch (Throwable t) {
						if (f.fallbackUrl != null) {
							progress.addAndGet(-contributedProgress[0]);
							contributedProgress[0] = 0;
							Log.warn("Failed to download "+path+" from specified URL, trying again from "+describe(f.fallbackUrl), t);
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
		PuppetHandler.updateTitle(bootstrapping ? "title.bootstrapping" : "title.updating", false);
		synchronized (dangerMutex) {
			PuppetHandler.updateSubtitle("subtitle.applying");
			for (int pass = 0; pass < 2; pass++) {
				for (Map.Entry<String, ? extends FilePlan> en : plan.files.entrySet()) {
					String path = en.getKey();
					FilePlan f = en.getValue();
					FileState to = f.state;
					DownloadedFile df = downloads.get(f);
					if (df == null && to.size != 0) {
						// Conflict dialog was rejected, skip this file.
						continue;
					}
					File dest = new File(path);
					if (!dest.getAbsolutePath().startsWith(wd.getAbsolutePath()+File.separator))
						throw new IOException("Refusing to download to a file outside of working directory");
					Path destPath;
					try {
						destPath = dest.toPath();
					} catch (InvalidPathException e) {
						if (pass == 0) Log.error("Destination file path "+dest+" is not valid on this OS/filesystem/charset combination!", e);
						continue;
					}
					if (pass == 1 && dest.getParentFile() != null) Files.createDirectories(dest.getParentFile().toPath());
					if (pass == 0 && moveAside.contains(path)) {
						Log.debug("Displacing "+path);
						Files.move(destPath, destPath.resolveSibling(destPath.getFileName().toString()+".orig"), StandardCopyOption.REPLACE_EXISTING);
					}
					if (to.size == 0) {
						if (to.hash == null) {
							if (pass == 0 && Files.exists(destPath)) {
								Log.info("Deleting "+path);
								Files.delete(destPath);
							}
						} else if (dest.exists()) {
							if (pass == 1) {
								Log.debug("Blanking "+path);
								try (FileOutputStream fos = new FileOutputStream(dest)) {
									// open and then immediately close the file to overwrite it with nothing
								}
							}
						} else {
							if (pass == 1) {
								Log.debug("Touching "+path);
								dest.createNewFile();
							}
						}
					} else if (pass == 1) {
						Log.debug("Applying "+path);
						assert df != null;
						Files.move(df.file.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
					}
			}
			}
			state = plan.newState;
			state.put("current_version", res.theirVersion.toJson());
			try {
				updatedComponents = MMCUpdater.apply(res.componentVersions);
			} catch (Throwable t) {
				Log.warn("Failed to apply component updates", t);
			}
		}
		Log.info("Update successful!");
		updated = true;
	}

	// helper methods, called more than once

	private static String ponder(FileState state) {
		if (state == null) return "[MISSING. STATE DATA IS INCOMPLETE OR CORRUPT]";
		if (state.hash == null) {
			return "[deleted]";
		}
		return state.toString();
	}

	private static DownloadedFile downloadAndCheckHash(File tmp, AtomicLong progress, Runnable updateProgress, String path, FilePlan f, URI url, FileState to, long[] contributedProgress) throws IOException {
		return RequestHelper.withRetries(3, () -> {
			DownloadedFile df = RequestHelper.downloadToFile(url, tmp, to.size, to.size == -1 ? l -> {} : l -> {contributedProgress[0]+=l;progress.addAndGet(l);},
					updateProgress, to.func, f.hostile);
			if (!df.hash.equals(to.hash)) {
				throw new Retry("Hash mismatch on downloaded file for "+path+" from "+url+" - expected "+to.hash+", got "+df.hash,
						IOException::new);
			}
			return df;
		});
	}

	private static final Pattern domainPattern = Pattern.compile("(^|\\.)([^\\.]+\\.[^\\.]+)$");
	
	private static String describe(URI url) {
		if (url == null) return "(null)";
		if (SysProps.DEBUG) return url.toString();
		String host = url.getHost();
		if (host == null || host.isEmpty()) return url.toString();
		Matcher m = domainPattern.matcher(host);
		String domain;
		if (m.find()) {
			domain = m.group(2);
		} else {
			domain = host;
		}
		switch (domain) {
			case "modrinth.com":
				return "Modrinth";
			
			case "forgecdn.net":
			case "curseforge.com":
				return "CurseForge";
			
			case "github.com":
			case "githubusercontent.com":
			case "github.io":
				return "GitHub";
				
			case "codeberg.org":
				return "Codeberg";

			case "planetminecraft.com":
				return "Planet Minecraft";
				
			case "mcarchive.net":
				return "MCArchive";
			
			case "archive.org":
				return "Internet Archive";
				
			case "prismlauncher.org":
				return "PrismLauncher";
					
			case "maven.org":
				return "Maven Central";
				
			default: return host;
		}
	}
	
	private static QDIni mergePreset(QDIni config, String presetName, boolean mustExist) {
		URL u = Agent.class.getClassLoader().getResource("com/unascribed/sup/presets/"+presetName+".ini");
		if (u == null) {
			if (!mustExist) {
				Log.debug("Optional preset "+presetName+" not found");
				return config;
			}
			Log.error("Config file error: Preset "+presetName+" not found at "+config.getBlame("preset")+"! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return null;
		}
		try (InputStream in = u.openStream()) {
			QDIni preset = QDIni.load("<preset "+presetName+">", in);
			config = preset.merge(config);
		} catch (IOException e) {
			Log.error("Failed to load preset "+presetName+"! Exiting.", e);
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
		if (okhttp != null) {
			okhttp.dispatcher().executorService().shutdown();
			okhttp.connectionPool().evictAll();
		}
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
	
	public static void exit(int code) {
		cleanup();
		System.exit(code);
	}

	/* (non-Javadoc)
	 * used in the agent to suspend the update flow at a safe point if we're waiting for a
	 * System.exit due to the user closing the puppet dialog (the puppet handling is multithreaded,
	 * and a mutex is used to ensure we don't kill the updater during a sensitive period that could
	 * corrupt the directory state)
	 */
	public static void blockForever() {
		while (true) {
			try {
				Thread.sleep(Integer.MAX_VALUE);
			} catch (InterruptedException e) {}
		}
	}
	
}
