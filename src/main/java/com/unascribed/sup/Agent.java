package com.unascribed.sup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.unascribed.sup.QDIni.QDIniException;

class Agent {

	public static final String VERSION = "0.0.1";
	
	// this mutex must be held while doing sensitive operations that shouldn't be interrupted
	private static final Object dangerMutex = new Object();
	
	private static PrintStream logStream;
	
	private static boolean standalone;
	public static boolean filesystemIsCaseSensitive;
	
	// read by Unsup when it loads
	// be careful not to load that class until this is all initialized
	public static String sourceVersion;
	public static boolean updated;
	
	public static void main(String[] args) throws UnsupportedEncodingException {
		standalone = true;
		premain(args.length >= 1 ? args[0] : null);
	}
	
	public static void premain(String arg) throws UnsupportedEncodingException {
		List<ExceptableRunnable> cleanup = new ArrayList<>();
		try {
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
			log("INFO", (standalone ? "Starting in standalone mode" : "Launch hijack successful")+". unsup v"+VERSION);
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
						log("INFO", "Filesystem is NOT case sensitive.");
					} else {
						filesystemIsCaseSensitive = true;
						log("INFO", "Filesystem is case sensitive.");
					}
				}
				if (tmp.exists()) {
					tmp.delete();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
				log("INFO", "Failed to test if filesystem is case sensitive. Assuming it is.");
				filesystemIsCaseSensitive = true;
			}
			QDIni config;
			File configFile = new File("unsup.ini");
			if (configFile.exists()) {
				try {
					config = QDIni.load(configFile);
					log("INFO", "Found and loaded unsup.ini. What secrets does it hold?");
					
				} catch (Exception e) {
					e.printStackTrace();
					log("ERROR", "Found unsup.ini, but couldn't parse it! Exiting.");
					System.exit(1);
					return;
				}
			} else {
				log("WARN", "No config file found? Doing nothing.");
				return;
			}
			String[] requiredKeys = {
					"version", "source_format", "source"
			};
			for (String req : requiredKeys) {
				if (!config.containsKey(req)) {
					log("ERROR", "Config file error: "+req+" is required, but was not defined! Exiting.");
					System.exit(1);
					return;
				}
			}
			int version = config.getInt("version", -1);
			if (version != 1) {
				log("ERROR", "Config file error: Unknown version "+version+" at "+config.getBlame("version")+"! Exiting.");
				System.exit(1);
				return;
			}
			config = mergePreset(config, "__global__");
			if (config.containsKey("preset")) {
				config = mergePreset(config, config.get("preset"));
			}
			if (config.getBoolean("use_envs", false)) {
				if (standalone && arg == null) {
					log("ERROR", "Cannot sync an env-based config in standalone mode unless an argument is given specifying the env! Exiting.");
					System.exit(1);
					return;
				}
				Set<String> foundEnvs = new HashSet<>();
				List<String> checkedMarkers = new ArrayList<>();
				String ourEnv = standalone ? arg : null;
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
					System.exit(1);
					return;
				}
				if (ourEnv == null) {
					log("ERROR", "use_envs is true, and we found no env markers! Checked for the following markers:");
					for (String s : checkedMarkers) {
						log("ERROR", "- "+s);
					}
					log("ERROR", "Exiting.");
					System.exit(1);
					return;
				}
				if (!foundEnvs.contains(ourEnv)) {
					log("ERROR", "Invalid env specified: \""+ourEnv+"\"! Valid envs:");
					for (String s : foundEnvs) {
						log("ERROR", "- "+s);
					}
					log("ERROR", "Exiting.");
					System.exit(1);
					return;
				}
				if (standalone) {
					log("INFO", "Declared env is "+ourEnv);
				} else {
					log("INFO", "Detected env is "+ourEnv);
				}
			}
			
			boolean nogui = false;
			
			if (standalone) {
				nogui = !Boolean.getBoolean("unsup.guiInStandalone");
			} else if (config.containsKey("no_gui")) {
				nogui = config.getBoolean("no_gui", false);
			} else if (config.getBoolean("recognize_nogui", false)) {
				String cmd = System.getProperty("sun.java.command");
				if (cmd != null) {
					nogui = cmd.endsWith(" nogui") || cmd.startsWith("nogui ") || cmd.contains(" nogui ") ||
							cmd.endsWith(" --nogui") || cmd.startsWith("--nogui ") || cmd.contains(" --nogui ");
				}
			}
			
			SourceFormat fmt = config.getEnum("source_format", SourceFormat.class, null);
			URL source;
			try {
				source = new URL(config.get("source"));
			} catch (MalformedURLException e) {
				log("ERROR", "Config error: source URL is malformed! "+e.getMessage()+". Exiting.");
				System.exit(1);
				return;
			}
			
			final Process puppet;
			final boolean puppetWorks;
			
			if (!nogui) {
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
					puppetWorks = false;
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
						log("WARN", "Got an error trying to check if the puppet is alive. Continuing without a GUI.");
						puppetWorks = false;
					} else if (!"unsup puppet ready".equals(firstLine)) {
						log("WARN", "Puppet sent unexpected hello line \""+firstLine+"\". (Expected \"unsup puppet ready\") Continuing without a GUI.");
						puppet.destroy();
						puppetWorks = false;
					} else {
						log("INFO", "Puppet is alive! Continuing.");
						puppetWorks = true;
						cleanup.add(puppet::destroy);
						Thread puppetOut = new Thread(() -> {
							try {
								while (true) {
									String line = br.readLine();
									if (line == null) return;
									if (line.equals("closeRequested")) {
										log("INFO", "User closed puppet window! Exiting...");
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
											System.exit(0);
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
			} else {
				puppet = null;
				puppetWorks = false;
			}
			
			PrintStream puppetOut;
			if (puppetWorks && puppet != null) {
				puppetOut = new PrintStream(puppet.getOutputStream(), true, "UTF-8");
			} else {
				puppetOut = NullPrintStream.INSTANCE;
			}
			
			puppetOut.println(":colorBackground="+config.get("colors.background", "000000"));
			puppetOut.println(":colorTitle="+config.get("colors.title", "FFFFFF"));
			puppetOut.println(":colorSubtitle="+config.get("colors.subtitle", "AAAAAA"));
			
			puppetOut.println(":colorProgress="+config.get("colors.progress", "FF0000"));
			puppetOut.println(":colorProgressTrack="+config.get("colors.progress_track", "AAAAAA"));
			
			puppetOut.println(":colorDialog="+config.get("colors.dialog", "FFFFFF"));
			puppetOut.println(":colorButton="+config.get("colors.button", "FFFFFF"));
			puppetOut.println(":colorButtonText="+config.get("colors.button_text", "FFFFFF"));
			
			puppetOut.println(":build");
			
			puppetOut.println(":mode=det");
			puppetOut.println(":subtitle="+config.get("subtitle", ""));
			// we don't want to flash a window on the screen if things aren't going slow, so we tell
			// the puppet to wait 250ms before actually making the window visible, and assign an
			// identifier to our order so we can belay it later if we finished before the timer
			// expired
			puppetOut.println("[openTimeout]250:visible=true");

			
			updateTitle(puppetOut, "Checking for updates...");
			int jump = 0;
			for (int i = 0; i < 1000; i++) {
				updateProgress(puppetOut, i);
				if (jump > 0) {
					jump--;
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
					}
				} else {
					if (Math.random() < 0.05) {
						jump = 50+(int)(Math.random()*50);
					}
					try {
						Thread.sleep((int)(Math.random()*80));
					} catch (InterruptedException e) {
					}
				}
			}
			log("INFO", "Checking for updates... 100%");
			
			sourceVersion = "?";
			updated = false;
			
			puppetOut.println(":mode=ind");
			puppetOut.println(":title=Waiting...");
			synchronized (dangerMutex) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) {
				}
			}
			
			puppetOut.println(":belay=openTimeout");
			puppetOut.println(":exit");
			if (puppetWorks && puppet != null) {
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
			System.exit(1);
			return;
		} finally {
			for (ExceptableRunnable er : cleanup) {
				try {
					er.run();
				} catch (Throwable t) {}
			}
		}
	}
	
	private static String title;
	private static int lastReportedProgress = 0;
	private static long lastReportedProgressTime = 0;

	private static void updateTitle(PrintStream puppetOut, String title) {
		Agent.title = title;
		puppetOut.println(":title="+title);
		log("INFO", title);
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
			System.exit(1);
			return null;
		}
		try (InputStream in = u.openStream()) {
			QDIni preset = QDIni.load("<preset "+presetName+">", in);
			config = preset.merge(config);
		} catch (IOException e) {
			e.printStackTrace();
			log("ERROR", "Failed to load preset "+presetName+"! Exiting.");
			System.exit(1);
			return null;
		}
		return config;
	}
	
	private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

	private static synchronized void log(String flavor, String msg) {
		log(standalone ? "sync" : "agent", flavor, msg);
	}
	
	private static synchronized void log(String tag, String flavor, String msg) {
		String line = "["+sdf.format(new Date())+"] [unsup "+tag+"/"+flavor+"]: "+msg;
		System.out.println(line);
		logStream.println(line);
	}
	
}
