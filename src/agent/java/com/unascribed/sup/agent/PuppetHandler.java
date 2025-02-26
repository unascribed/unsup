package com.unascribed.sup.agent;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.Character.UnicodeScript;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.brotli.dec.BrotliInputStream;

import com.unascribed.sup.AlertMessageType;
import com.unascribed.sup.SysProps;
import com.unascribed.sup.Util;
import com.unascribed.sup.SysProps.PuppetMode;
import com.unascribed.sup.agent.util.RequestHelper;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.pieces.Latch;

public class PuppetHandler {
	
	public static Process puppet;
	public static OutputStream puppetOut;
	
	private static String title;
	private static int lastReportedProgress = 0;
	private static long lastReportedProgressTime = 0;
	
	private static Map<String, String> alertResults = new HashMap<>();
	private static Map<String, Latch> alertWaiters = new HashMap<>();
	
	private static final int crashId = ThreadLocalRandom.current().nextInt()&Integer.MAX_VALUE;
	
	public enum AlertOptionType { OK, OK_CANCEL, YES_NO, YES_NO_CANCEL, YES_NO_TO_ALL_CANCEL }
	public enum AlertOption { CLOSED, OK, YES, NO, CANCEL, YESTOALL, NOTOALL }

	private static final String lwjglVersion = "3.3.6";
	private static final String[] lwjgls = {
			"lwjgl",
			"lwjgl-glfw",
			"lwjgl-opengl",
			"lwjgl-freetype",
	};
	
	private static final String[] copyableProps = {
		"javax.accessibility.assistive_technologies",
		"assistive_technologies",
		"unsup.puppetMode",
		"unsup.puppet.opengl.platform",
		"sun.java2d.uiScale",
		"unsup.scale"
	};
	
	public static void destroy() {
		puppet.destroy();
	}
	
	public static void create() {
		out: {
			URI uri;
			try {
				uri = Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			} catch (URISyntaxException e) {
				Log.warn("Cannot summon Puppet: Failed to find our own JAR file or directory.");
				puppet = null;
				break out;
			}
			File ourPath;
			try {
				ourPath = new File(uri);
			} catch (IllegalArgumentException e) {
				Log.warn("Cannot summon Puppet: Failed to find our own JAR file or directory.");
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
				Log.warn("Cannot summon Puppet: Failed to find Java. Looked in "+javaBin+", but can't find a known executable.");
				puppet = null;
			} else {
				Process p;
				try {
					List<String> args = new ArrayList<>();
					if (SysProps.PUPPET_WRAPPER_COMMAND != null) {
						args.add(SysProps.PUPPET_WRAPPER_COMMAND);
					}
					args.add(java);
					args.add("-XX:+IgnoreUnrecognizedVMOptions");
					args.add("-XX:+UnlockDiagnosticVMOptions");
					args.add("-Djbr.catch.SIGABRT=true");
					for (String prop : copyableProps) {
						String v = System.getProperty(prop);
						if ("unsup.puppetMode".equals(prop) && v == null) {
							v = Agent.config.get("puppet_mode", "auto");
						}
						if (v != null) {
							args.add("-D"+prop+"="+v);
						}
					}
					List<String> cp = new ArrayList<>();
					cp.add(ourPath.getAbsolutePath());
					if (SysProps.PUPPET_MODE != PuppetMode.SWING) {
						boolean xdg = false;
						File cacheDir = new File(new File(System.getProperty("user.home")), ".unsup");
						String osName = System.getProperty("os.name");
						String ourOs = "unknown";
						String ourArch = "unknown";
						// copied from LWJGL3 Platform
						if (osName.startsWith("Windows")) {
							ourOs = "windows";
							cacheDir = new File(new File(System.getenv("APPDATA")), "Local/unsup");
						} else if (osName.startsWith("FreeBSD")) {
							ourOs = "freebsd";
							xdg = true;
						} else if (osName.startsWith("Linux") || osName.startsWith("SunOS") || osName.startsWith("Unix")) {
							ourOs = "linux";
							xdg = true;
						} else if (osName.startsWith("Mac OS X") || osName.startsWith("Darwin")) {
							ourOs = "macos";
							cacheDir = new File(new File(System.getProperty("user.home")), "Library/Caches/unsup");
						}
						if ("macos".equals(ourOs) && SysProps.PUPPET_MODE == PuppetMode.AUTO) {
							Log.warn("OpenGL puppet does not presently work on macOS: https://git.sleeping.town/unascribed/unsup/issues/22");
							Log.warn("Forcing Swing puppet. Use -Dunsup.puppetMode=opengl to override");
							args.add("-Dunsup.puppetMode=swing");
						} else {
							if (xdg) {
								String home = System.getenv("HOME");
								if (home == null || home.trim().isEmpty()) {
									home = System.getProperty("user.home");
								}
								String dir = System.getenv("XDG_DATA_HOME");
								if (dir == null || dir.trim().isEmpty()) {
									dir = home+"/.cache";
								}
								cacheDir = new File(dir+"/unsup");
							}
							if ("macos".equals(ourOs)) {
								args.add("-XstartOnFirstThread");
							}
							String osArch = System.getProperty("os.arch");
							boolean is64Bit = osArch.contains("64") || osArch.startsWith("armv8");
							if (osArch.startsWith("arm") || osArch.startsWith("aarch")) {
								if (is64Bit) {
									ourArch = "arm64";
								} else {
									ourArch = "arm32";
								}
							} else if (osArch.startsWith("ppc")) {
								if ("ppc64le".equals(osArch)) {
									ourArch = "ppc64le";
								}
							} else if (osArch.startsWith("riscv")) {
								if ("riscv64".equals(osArch)) {
									ourArch = "riscv64";
								}
							} else {
								if (is64Bit) {
									ourArch = "amd64";
								} else {
									ourArch = "x86";
								}
							}
							File fcacheDir = cacheDir;
							String fourOs = ourOs;
							String fourArch = ourArch;
							File tmp = new File(".unsup-tmp/natives");
							tmp.mkdirs();
							Log.debug("Retrieving assets for "+ourOs+"-"+ourArch+"...");
							ExecutorService svc = Executors.newFixedThreadPool(6);
							List<Future<File>> futures = new ArrayList<>();
							try {
								for (String s : lwjgls) {
									URL url = PuppetHandler.class.getClassLoader().getResource("com/unascribed/sup/jars/"+s+"-"+lwjglVersion+".jar.br");
									File out = new File(tmp, s+"-"+lwjglVersion+".jar");
									out.deleteOnExit();
									if (url != null) {
										try (FileOutputStream fos = new FileOutputStream(out);
												InputStream is = new BrotliInputStream(url.openStream())) {
											Util.copy(is, fos);
										}
										cp.add(out.getAbsolutePath());
									}
									futures.add(svc.submit(() -> {
										String module = s.replace("lwjgl-", "");
										return obtainAsset(tmp, fcacheDir, "natives/"+lwjglVersion+"/"+fourOs+"/"+fourArch+"/"+module);
									}));
								}
								boolean needCjk = false;
								for (String s : Agent.config.keySet()) {
									if (s.startsWith("strings.")) {
										String v = Agent.config.get(s);
										if (v.codePoints().anyMatch(codepoint -> {
											UnicodeScript sc = UnicodeScript.of(codepoint);
											// I think this is all of them??
											return sc == UnicodeScript.HANGUL || sc == UnicodeScript.HAN || sc == UnicodeScript.KATAKANA
													|| sc == UnicodeScript.HIRAGANA || sc == UnicodeScript.BOPOMOFO;
										})) {
											needCjk = true;
											break;
										}
									}
								}
								if (needCjk) {
									Log.debug("Retrieving CJK support...");
									futures.add(svc.submit(() -> {
										return obtainAsset(tmp, fcacheDir, "CJKSupport");
									}));
								}
								svc.shutdown();
								List<String> addnCp = new ArrayList<>();
								for (Future<File> f : futures) {
									addnCp.add(f.get().getAbsolutePath());
								}
								cp.addAll(addnCp);
							} catch (ExecutionException | IOException e) {
								Log.error("Failed to load assets for OpenGL puppet, falling back to Swing puppet (use -Dunsup.puppetMode=swing to enforce this behavior)", e);
								args.add("-Dunsup.puppetMode=swing");
							}
						}
					}
					if ("DEV".equals(Util.VERSION)) {
						for (String s : System.getProperty("java.class.path").split(File.pathSeparator)) {
							cp.add(s);
						}
					}
					StringJoiner cpJ = new StringJoiner(File.pathSeparator);
					cp.forEach(cpJ::add);
					args.add("-cp");
					args.add(cpJ.toString());
					File errorFile = new File("puppet-native-crash-"+crashId+".log");
					args.add("-XX:ErrorFile="+errorFile.getAbsolutePath());
					args.add("-XX:+ErrorLogSecondaryErrorDetails");
					args.add("-XX:+ExtensiveErrorReports");
					args.add("com.unascribed.sup.puppet.Puppet");
					
					StringJoiner printJ = new StringJoiner("' '", "'", "'");
					for (String s : args) {
						printJ.add(s.replace("'", "\\'"));
					}
					
					Log.debug("unsup location detected as "+ourPath);
					Log.debug("Java location detected as "+java);
					Log.debug("Puppet command: "+printJ);
					
					ProcessBuilder bldr = new ProcessBuilder(args);
					bldr.environment().put("_JAVA_AWT_WM_NONREPARENTING", "1");
					bldr.environment().put("NO_AWT_MITSHM", "1");
					p = bldr.start();
				} catch (Throwable t) {
					Log.warn("Failed to summon a puppet.", t);
					puppet = null;
					break out;
				}
				Log.debug("Dark spell successful. Puppet summoned.");
				puppet = p;
			}
		}
		if (puppet == null) {
			Log.warn("Failed to summon a Puppet. Continuing without a GUI.");
		} else {
			Thread puppetErr = new Thread(() -> {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(puppet.getErrorStream(), StandardCharsets.UTF_8))) {
					while (true) {
						String line = br.readLine();
						if (line == null) return;
						if (line.contains("|")) {
							int idx = line.indexOf('|');
							Log.log(line.substring(0, idx), "puppet", line.substring(idx+1));
						} else {
							System.err.println("puppet: "+line);
						}
					}
				} catch (IOException e) {}
			}, "unsup puppet error printer");
			puppetErr.setDaemon(true);
			puppetErr.start();
			Log.debug("Waiting for the Puppet to come to life...");
			BufferedReader br = new BufferedReader(new InputStreamReader(puppet.getInputStream(), StandardCharsets.UTF_8));
			Throwable t = null;
			String firstLine = null;
			try {
				firstLine = br.readLine();
			} catch (IOException e) {
				t = e;
			}
			if (firstLine == null) {
				Log.warn("Puppet failed to come alive. Continuing without a GUI.", t);
				puppet.destroy();
				puppet = null;
			} else if (!"unsup puppet ready".equals(firstLine)) {
				Log.warn("Puppet sent unexpected hello line \""+firstLine+"\". (Expected \"unsup puppet ready\") Continuing without a GUI.");
				puppet.destroy();
				puppet = null;
			} else {
				Log.debug("Puppet is alive! Continuing.");
				puppetOut = new BufferedOutputStream(puppet.getOutputStream(), 512);
				Thread puppetThread = new Thread(() -> {
					try (BufferedReader br2 = br) {
						while (true) {
							String line = br.readLine();
							if (line == null) return;
							if (line.equals("closeRequested")) {
								Log.info("User closed puppet window! Exiting...");
								Agent.awaitingExit = true;
								long start = System.nanoTime();
								synchronized (Agent.dangerMutex) {
									long diff = System.nanoTime()-start;
									if (diff > TimeUnit.MILLISECONDS.toNanos(500)) {
										Log.info("Uninterruptible operations finished, exiting!");
									}
									puppet.destroy();
									if (!puppet.waitFor(1, TimeUnit.SECONDS)) {
										puppet.destroyForcibly();
									}
									Agent.exit(Agent.EXIT_USER_REQUEST);
								}
							} else if (line.startsWith("alert:")) {
								String[] split = line.split(":", 3);
								String name = split[1];
								String opt = split.length > 2 ? split[2] : "";
								synchronized (alertResults) {
									alertResults.put(name, opt);
									Latch latch = alertWaiters.remove(name);
									if (latch != null) {
										latch.release();
									}
								}
							} else {
								Log.warn("Unknown line from puppet: "+line);
							}
						}
					} catch (IOException | InterruptedException e) {}
				}, "unsup puppet out parser");
				puppetThread.setDaemon(true);
				puppetThread.start();
			}
		}
	}

	private static File obtainAsset(File tmp, File cacheDir, String url) throws IOException {
		final int M = 1024*1024;
		
		String fname = url.replace("/", "-");
		File out = new File(tmp, fname+".jar");
		File cacheFile = new File(cacheDir, fname+".jar.br");
		File cacheFileSig = new File(cacheDir, fname+".sig");
		boolean needsDownload = true;
		if (cacheFile.exists()) {
			try {
				byte[] data = RequestHelper.loadAndVerify(cacheFile.toURI(), 64*M, cacheFileSig.toURI(), Agent.unsupSig);
				try (FileOutputStream fos = new FileOutputStream(out);
						InputStream is = new BrotliInputStream(new ByteArrayInputStream(data))) {
					Util.copy(is, fos);
				}
				needsDownload = false;
				Log.debug("Got "+fname+" from cache");
			} catch (IOException e) {
				Log.warn("Failed to load cached natives jar, redownloading it", e);
			}
		}
		if (needsDownload) {
			Log.debug("Downloading "+fname+" from unsup.y2k.diy...");
			String dlBase = "https://unsup.y2k.diy/assets/v1/"+url;
			URI dl = URI.create(dlBase+".jar.br");
			URI sig = URI.create(dlBase+".sig");
			byte[] sigData = RequestHelper.downloadToMemory(sig, 512);
			cacheDir.mkdirs();
			try (FileOutputStream fos = new FileOutputStream(cacheFileSig)) {
				fos.write(sigData);
			}
			byte[] data = RequestHelper.loadAndVerify(dl, 64*M, cacheFileSig.toURI(), Agent.unsupSig);
			try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
				fos.write(data);
			}
			try (FileOutputStream fos = new FileOutputStream(out);
					InputStream is = new BrotliInputStream(new ByteArrayInputStream(data))) {
				Util.copy(is, fos);
			}
			Log.debug(fname+" downloaded and saved to cache");
		}
		return out;
	}

	public static void sendConfig() {
		tellPuppet(":colorBackground="+Agent.config.get("colors.background", "000000"));
		tellPuppet(":colorTitle="+Agent.config.get("colors.title", "FFFFFF"));
		tellPuppet(":colorSubtitle="+Agent.config.get("colors.subtitle", "AAAAAA"));
		
		tellPuppet(":colorProgress="+Agent.config.get("colors.progress", "FF0000"));
		tellPuppet(":colorProgressTrack="+Agent.config.get("colors.progress_track", "AAAAAA"));
		
		tellPuppet(":colorDialog="+Agent.config.get("colors.dialog", "FFFFFF"));
		tellPuppet(":colorButton="+Agent.config.get("colors.button", "FFFFFF"));
		tellPuppet(":colorButtonText="+Agent.config.get("colors.button_text", "FFFFFF"));
		
		tellPuppet(":colorQuestion="+Agent.config.get("colors.info", "FF00FF"));
		tellPuppet(":colorInfo="+Agent.config.get("colors.info", "00FFFF"));
		tellPuppet(":colorWarning="+Agent.config.get("colors.info", "FFFF00"));
		tellPuppet(":colorError="+Agent.config.get("colors.info", "FF0000"));
		
		for (String k : Agent.config.keySet()) {
			if (k.startsWith("strings.")) {
				tellPuppet(":string="+k.substring(8)+":"+Agent.config.get(k));
			}
		}
	}

	public static void tellPuppet(String order) {
		if (puppetOut == null) return;
		synchronized (puppetOut) {
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
				if (!Agent.awaitingExit) {
					Log.warn("IO error while talking to puppet. Killing and continuing without GUI.", e);
					File errorFile = new File("puppet-native-crash-"+crashId+".log");
					if (errorFile.exists()) {
						Log.warn("It looks like the Puppet crashed in native code. Please report this issue, including the full unsup.log and "+errorFile.getName());
					}
				}
				puppet.destroyForcibly();
				puppet = null;
				puppetOut = null;
				for (Latch l : alertWaiters.values()) {
					l.release();
				}
			}
		}
	}

	public static void updateTitle(String title, boolean determinate) {
		PuppetHandler.title = title;
		tellPuppet(":prog=0");
		tellPuppet(":mode="+(determinate ? "det" : "ind"));
		tellPuppet(":title="+title);
	}

	public static void updateSubtitle(String subtitle) {
		tellPuppet(":subtitle="+subtitle);
	}

	public static void updateSubtitleDownloading(String... files) {
		StringJoiner joiner = new StringJoiner("\u001C");
		for (String s : files) joiner.add(s);
		tellPuppet(":downloading="+joiner);
	}

	public static void updateProgress(int prog) {
		tellPuppet(":prog="+prog);
		if (Math.abs(lastReportedProgress-prog) >= 100 || System.nanoTime()-lastReportedProgressTime > TimeUnit.SECONDS.toNanos(3)) {
			lastReportedProgress = prog;
			lastReportedProgressTime = System.nanoTime();
			Log.info(Agent.config.get("strings."+title)+" "+(prog/10)+"%");
		}
	}

	public static AlertOption openAlert(String title, String body, AlertMessageType messageType, AlertOptionType optionType, AlertOption def) {
		if (puppetOut == null) {
			return def;
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			alertResults.put(name, def.name().toLowerCase(Locale.ROOT));
			alertWaiters.put(name, latch);
			tellPuppet("["+name+"]:alert="+title+":"+body+":"+messageType.name().toLowerCase(Locale.ROOT)+":"+optionType.name().toLowerCase(Locale.ROOT).replace("_", "")+":"+def.name().toLowerCase(Locale.ROOT).replace("_", ""));
			latch.awaitUninterruptibly();
			return AlertOption.valueOf(alertResults.remove(name).replace("option.", "").replace("_", "").toUpperCase(Locale.ROOT));
		}
	}

	public static String openChoiceAlert(String title, String body, Collection<String> choices, String def) {
		if (puppetOut == null) {
			return def;
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			alertResults.put(name, def);
			alertWaiters.put(name, latch);
			StringJoiner joiner = new StringJoiner("\u001C");
			choices.forEach(c -> joiner.add(c.replace(':', '\u001B')));
			tellPuppet("["+name+"]:alert="+title+":"+body+":choice="+joiner+":"+def);
			latch.awaitUninterruptibly();
			return alertResults.remove(name);
		}
	}
	
	public static List<String> openFlavorSelectDialog(String title, String body, List<FlavorGroup> groups) {
		if (puppetOut == null) {
			return new ArrayList<>();
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			StringJoiner defaultJoiner = new StringJoiner("\u001C");
			StringJoiner groupJoiner = new StringJoiner("\u001D");
			for (FlavorGroup group : groups) {
				StringJoiner joiner = new StringJoiner("\u001C");
				joiner.add(group.id);
				joiner.add(group.name);
				joiner.add(group.description);
				for (FlavorGroup.FlavorChoice choice : group.choices) {
					joiner.add(choice.id).add(choice.name).add(choice.description).add(Boolean.toString(choice.def));
					if (choice.def) defaultJoiner.add(choice.id);
				}
				groupJoiner.add(joiner.toString());
			}
			alertResults.put(name, defaultJoiner.toString());
			alertWaiters.put(name, latch);
			tellPuppet("["+name+"]:pickFlavor="+groupJoiner.toString().replace(':', '\u001B'));
			latch.awaitUninterruptibly();
			return Arrays.asList(alertResults.remove(name).split("\u001C"));
		}
	}

}
