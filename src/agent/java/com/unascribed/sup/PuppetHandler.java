package com.unascribed.sup;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.unascribed.sup.SysProps.PuppetMode;
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
	
	public enum AlertMessageType { QUESTION, INFO, WARN, ERROR, NONE }
	public enum AlertOptionType { OK, OK_CANCEL, YES_NO, YES_NO_CANCEL, YES_NO_TO_ALL_CANCEL }
	public enum AlertOption { CLOSED, OK, YES, NO, CANCEL, YESTOALL, NOTOALL }

	private static final String[] cpJars = {
			"lwjgl-3.3.6-natives-freebsd.jar",
			"lwjgl-3.3.6-natives-linux-arm32.jar",
			"lwjgl-3.3.6-natives-linux-arm64.jar",
			"lwjgl-3.3.6-natives-linux-ppc64le.jar",
			"lwjgl-3.3.6-natives-linux-riscv64.jar",
			"lwjgl-3.3.6-natives-linux.jar",
			"lwjgl-3.3.6-natives-macos-arm64.jar",
			"lwjgl-3.3.6-natives-macos.jar",
			"lwjgl-3.3.6-natives-windows-arm64.jar",
			"lwjgl-3.3.6-natives-windows-x86.jar",
			"lwjgl-3.3.6-natives-windows.jar",
			"lwjgl-3.3.6.jar",
			"lwjgl-glfw-3.3.6-natives-freebsd.jar",
			"lwjgl-glfw-3.3.6-natives-linux-arm32.jar",
			"lwjgl-glfw-3.3.6-natives-linux-arm64.jar",
			"lwjgl-glfw-3.3.6-natives-linux-ppc64le.jar",
			"lwjgl-glfw-3.3.6-natives-linux-riscv64.jar",
			"lwjgl-glfw-3.3.6-natives-linux.jar",
			"lwjgl-glfw-3.3.6-natives-macos-arm64.jar",
			"lwjgl-glfw-3.3.6-natives-macos.jar",
			"lwjgl-glfw-3.3.6-natives-windows-arm64.jar",
			"lwjgl-glfw-3.3.6-natives-windows-x86.jar",
			"lwjgl-glfw-3.3.6-natives-windows.jar",
			"lwjgl-glfw-3.3.6.jar",
			"lwjgl-opengl-3.3.6-natives-freebsd.jar",
			"lwjgl-opengl-3.3.6-natives-linux-arm32.jar",
			"lwjgl-opengl-3.3.6-natives-linux-arm64.jar",
			"lwjgl-opengl-3.3.6-natives-linux-ppc64le.jar",
			"lwjgl-opengl-3.3.6-natives-linux-riscv64.jar",
			"lwjgl-opengl-3.3.6-natives-linux.jar",
			"lwjgl-opengl-3.3.6-natives-macos-arm64.jar",
			"lwjgl-opengl-3.3.6-natives-macos.jar",
			"lwjgl-opengl-3.3.6-natives-windows-arm64.jar",
			"lwjgl-opengl-3.3.6-natives-windows-x86.jar",
			"lwjgl-opengl-3.3.6-natives-windows.jar",
			"lwjgl-opengl-3.3.6.jar",
			"lwjgl-freetype-3.3.6-natives-freebsd.jar",
			"lwjgl-freetype-3.3.6-natives-linux-arm32.jar",
			"lwjgl-freetype-3.3.6-natives-linux-arm64.jar",
			"lwjgl-freetype-3.3.6-natives-linux-ppc64le.jar",
			"lwjgl-freetype-3.3.6-natives-linux-riscv64.jar",
			"lwjgl-freetype-3.3.6-natives-linux.jar",
			"lwjgl-freetype-3.3.6-natives-macos-arm64.jar",
			"lwjgl-freetype-3.3.6-natives-macos.jar",
			"lwjgl-freetype-3.3.6-natives-windows-arm64.jar",
			"lwjgl-freetype-3.3.6-natives-windows-x86.jar",
			"lwjgl-freetype-3.3.6-natives-windows.jar",
			"lwjgl-freetype-3.3.6.jar",
	};
	
	private static final String[] copyableProps = {
		"javax.accessibility.assistive_technologies",
		"assistive_technologies",
		"unsup.puppetMode"
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
					args.add(java);
					if (System.getProperty("os.name").contains("OS X")) {
						args.add("-XstartOnFirstThread");
					}
					for (String prop : copyableProps) {
						String v = System.getProperty(prop);
						if (v != null) {
							args.add("-D"+prop+"="+v);
						}
					}
					args.add("-cp");
					List<String> cp = new ArrayList<>();
					cp.add(ourPath.getAbsolutePath());
					if (SysProps.PUPPET_MODE != PuppetMode.SWING) {
						File tmp = new File(".unsup-tmp/natives");
						tmp.mkdirs();
						for (String s : cpJars) {
							URL url = PuppetHandler.class.getClassLoader().getResource("com/unascribed/sup/jars/"+s);
							if (url != null) {
								File out = new File(tmp, s);
								try (FileOutputStream fos = new FileOutputStream(out);
										InputStream is = url.openStream()) {
									Util.copy(is, fos);
								}
								out.deleteOnExit();
								cp.add(out.getAbsolutePath());
							}
						}
					}
					StringBuilder cpStr = new StringBuilder();
					for (String s : cp) {
						cpStr.append(s);
						cpStr.append(File.pathSeparatorChar);
					}
					cpStr.setLength(cpStr.length()-1);
					args.add(cpStr.toString());
					args.add("com.unascribed.sup.Puppet");
					Log.debug("unsup location detected as "+ourPath);
					Log.debug("Java location detected as "+java);
					Log.debug("Puppet command: "+args);
					ProcessBuilder bldr = new ProcessBuilder(args);
					bldr.environment().put("_JAVA_AWT_WM_NONREPARENTING", "1");
					bldr.environment().put("NO_AWT_MITSHM", "1");
					p = bldr.start();
				} catch (Throwable t) {
					Log.warn("Failed to summon a puppet.");
					t.printStackTrace();
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
			String firstLine = null;
			try {
				firstLine = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (firstLine == null) {
				Log.warn("Puppet failed to come alive. Continuing without a GUI.");
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

	public static void setPuppetColorsFromConfig() {
		tellPuppet(":colorBackground="+Agent.config.get("colors.background", "000000"));
		tellPuppet(":colorTitle="+Agent.config.get("colors.title", "FFFFFF"));
		tellPuppet(":colorSubtitle="+Agent.config.get("colors.subtitle", "AAAAAA"));
		
		tellPuppet(":colorProgress="+Agent.config.get("colors.progress", "FF0000"));
		tellPuppet(":colorProgressTrack="+Agent.config.get("colors.progress_track", "AAAAAA"));
		
		tellPuppet(":colorDialog="+Agent.config.get("colors.dialog", "FFFFFF"));
		tellPuppet(":colorButton="+Agent.config.get("colors.button", "FFFFFF"));
		tellPuppet(":colorButtonText="+Agent.config.get("colors.button_text", "FFFFFF"));
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
					e.printStackTrace();
					Log.warn("IO error while talking to puppet. Killing and continuing without GUI.");
				}
				puppet.destroyForcibly();
				puppet = null;
				puppetOut = null;
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

	public static void updateProgress(int prog) {
		tellPuppet(":prog="+prog);
		if (Math.abs(lastReportedProgress-prog) >= 100 || System.nanoTime()-lastReportedProgressTime > TimeUnit.SECONDS.toNanos(3)) {
			lastReportedProgress = prog;
			lastReportedProgressTime = System.nanoTime();
			Log.info(title+" "+(prog/10)+"%");
		}
	}

	public static AlertOption openAlert(String title, String body, AlertMessageType messageType, AlertOptionType optionType, AlertOption def) {
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

	public static String openChoiceAlert(String title, String body, Collection<String> choices, String def) {
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
	
	public static List<String> openFlavorSelectDialog(String title, String body, List<FlavorGroup> groups) {
		if (puppetOut == null) {
			return new ArrayList<>();
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			alertWaiters.put(name, latch);
			StringJoiner groupJoiner = new StringJoiner("\u001D");
			for (FlavorGroup group : groups) {
				StringJoiner joiner = new StringJoiner("\u001C");
				joiner.add(group.id);
				joiner.add(group.name);
				joiner.add(group.description);
				for (FlavorGroup.FlavorChoice choice : group.choices) {
					joiner.add(choice.id).add(choice.name).add(choice.description).add(Boolean.toString(choice.def));
				}
				groupJoiner.add(joiner.toString());
			}
			tellPuppet("["+name+"]:pickFlavor="+groupJoiner.toString().replace(':', '\u001B'));
			latch.awaitUninterruptibly();
			return Arrays.asList(alertResults.remove(name).split("\u001C"));
		}
	}

}
