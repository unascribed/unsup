package com.unascribed.sup;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.NotNull;

import com.unascribed.sup.SysProps.PuppetMode;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.FlavorGroup.FlavorChoice;
import com.unascribed.sup.opengl.GLPuppet;
import com.unascribed.sup.swing.SwingPuppet;

public class Puppet {
	
	public static final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
	private static final Map<String, String> strings = new HashMap<>();
	
	private static final BlockingQueue<Runnable> mainThreadWorkQueue = new LinkedBlockingQueue<>();
	private static final Thread mainThread = Thread.currentThread();
	
	public static void main(String[] args) {
		PuppetMode mode = SysProps.PUPPET_MODE;
		boolean didOverride = false;
		PuppetDelegate delTmp = null;
		if (mode == PuppetMode.AUTO) {
			if (System.getProperty("javax.accessibility.assistive_technologies") != null
					|| System.getProperty("assistive_technologies") != null) {
				log("INFO", "Forcing Swing puppet as assistive technologies may be present");
				mode = PuppetMode.SWING;
				didOverride = true;
			}
		}
		if (didOverride) {
			log("INFO", "Pass -Dunsup.puppetMode=opengl to override");
		}
		if (mode == PuppetMode.AUTO || mode == PuppetMode.OPENGL) {
			Throwable error = null;
			try {
				delTmp = GLPuppet.start();
				if (delTmp != null) {
					log("DEBUG", "Initialized OpenGL puppet");
					log("DEBUG", "Pass -Dunsup.puppetMode=swing to override");
				}
			} catch (Throwable t) {
				error = t;
			}
			if (delTmp == null) {
				if (mode == PuppetMode.OPENGL) {
					log("ERROR", "Failed to initialize OpenGL puppet, which was our only option by user request!");
					System.exit(1);
					return;
				} else {
					log("WARN", "Failed to initialize OpenGL puppet, falling back to Swing", error);
				}
			}
		}
		if (delTmp == null) {
			delTmp = SwingPuppet.start();
			if (delTmp != null) {
				log("DEBUG", "Initialized Swing puppet");
			} else {
				log("ERROR", "Failed to initialize Swing puppet, giving up!");
				System.exit(1);
				return;
			}
		}
		@NotNull PuppetDelegate del = delTmp;
		
		System.out.println("unsup puppet ready");
		
		new Thread(() -> {
			Map<String, ScheduledFuture<?>> orders = new HashMap<>();
			Map<String, Runnable> orderRunnables = new HashMap<>();
			
			BufferedInputStream in = new BufferedInputStream(System.in, 512);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try {
				while (true) {
					int by = in.read();
					if (by == -1) break;
					String line;
					if (by == 0) {
						line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
						buffer.reset();
					} else {
						buffer.write(by);
						continue;
					}
					String name;
					if (line.startsWith("[")) {
						int close = line.indexOf(']');
						name = line.substring(1, close);
						line = line.substring(close+1);
					} else {
						name = null;
					}
					String timing = line.substring(0, line.indexOf(':'));
					int delay;
					if (timing.isEmpty()) {
						delay = 0;
					} else {
						delay = Integer.parseInt(timing);
					}
					int eq = line.indexOf('=');
					String order = line.substring(line.indexOf(':')+1, eq == -1 ? line.length() : eq);
					String arg = eq == -1 ? "" : line.substring(eq+1);
					Runnable r;
					switch (order) {
						case "build": {
							r = del::build;
							break;
						}
						case "colorBackground": case "colorTitle": case "colorSubtitle": case "colorProgress":
						case "colorProgressTrack": case "colorDialog": case "colorButton": case "colorButtonText":
							del.setColor(ColorChoice.valueOf(order.substring(5).toUpperCase(Locale.ROOT)), Integer.parseInt(arg, 16));
							continue;
						case "string":
							String[] spl = arg.split(":", 2);
							strings.put(spl[0], spl[1]);
							continue;
						case "belay": {
							r = () -> {
								synchronized (orders) {
									if (orders.containsKey(arg)) {
										orders.remove(arg).cancel(false);
										orderRunnables.remove(arg);
									}
								}
							};
							break;
						}
						case "expedite": {
							r = () -> {
								Runnable inner = null;
								synchronized (orders) {
									if (orders.containsKey(arg)) {
										if (orders.remove(arg).cancel(false)) {
											// we don't want to be holding the orders mutex while we run
											// the original order
											inner = orderRunnables.remove(arg);
										} else {
											orderRunnables.remove(arg);
										}
									}
								}
								if (inner != null) inner.run();
							};
							break;
						}
						case "visible": {
							boolean b = Boolean.parseBoolean(arg);
							r = () -> del.setVisible(b);
							break;
						}
						case "exit": {
							r = () -> {
								System.exit(0);
							};
							break;
						}
						case "mode": {
							if ("ind".equals(arg)) {
								r = del::setProgressIndeterminate;
							} else if ("det".equals(arg)) {
								r = del::setProgressDeterminate;
							} else if ("done".equals(arg)) {
								r = del::setDone;
							} else {
								Puppet.log("WARN", "Unknown mode "+arg+", expected ind or det");
								continue;
							}
							break;
						}
						case "prog": {
							int i = Integer.parseInt(arg);
							r = () -> del.setProgress(i);
							break;
						}
						case "title": {
							r = () -> del.setTitle(format(arg));
							break;
						}
						case "subtitle": {
							r = () -> del.setSubtitle(format(arg));
							break;
						}
						case "alert": {
							String[] split = arg.split(":");
							String title = split[0];
							String body = split[1];
							String messageTypeStr = split[2];
							String optionTypeStr = split[3];
							String def = split.length < 5 ? null : split[4];
							if (messageTypeStr.startsWith("choice=")) {
								String[] options = messageTypeStr.substring(7).split("\u001C");
								for (int i = 0; i < options.length; i++) {
									options[i] = format(options[i].replace('\u001B', ':'));
								}
								r = () -> del.openChoiceDialog(name, title, body, options, optionTypeStr);
							} else {
								MessageType messageType = MessageType.valueOf(messageTypeStr.toUpperCase(Locale.ROOT));
								String[] options;
								switch (optionTypeStr) {
									case "yesno":
										options = new String[]{"option.yes", "option.no"};
										break;
									case "yesnocancel":
										options = new String[]{"option.yes", "option.no", "option.cancel"};
										break;
									case "okcancel":
										options = new String[]{"option.ok", "option.cancel"};
										break;
									case "yesnotoallcancel":
										options = new String[]{"option.yes_to_all", "option.yes", "option.no_to_all", "option.no", "option.cancel"};
										break;
									default:
										Puppet.log("WARN", "Unknown dialog option type "+optionTypeStr+", defaulting to ok");
										// fallthru
									case "ok":
										options = new String[]{"option.ok"};
										break;
								}
								r = () -> del.openMessageDialog(name, title, body, messageType, options, format(def));
							}
							break;
						}
						case "pickFlavor": {
							String[] split = arg.split(":");
							List<FlavorGroup> groups = new ArrayList<>();
							for (String s : split[0].replace('\u001B', ':').split("\u001D")) {
								String[] fields = s.split("\u001C");
								FlavorGroup grp = new FlavorGroup();
								grp.id = fields[0];
								grp.name = fields[1];
								grp.description = fields[2];
								for (int i = 3; i < fields.length; i += 4) {
									FlavorChoice c = new FlavorChoice();
									c.id = fields[i];
									c.name = fields[i+1];
									c.description = fields[i+2];
									c.def = Boolean.parseBoolean(fields[i+3]);
									grp.choices.add(c);
								}
								groups.add(grp);
							}
							r = () -> del.openFlavorDialog(name, groups);
							break;
						}
						default: {
							Puppet.log("WARN", "Unknown order "+order);
							continue;
						}
					}
					Runnable fr = r;
					if (name != null) {
						fr = () -> {
							r.run();
							synchronized (orders) {
								orders.remove(name);
								orderRunnables.remove(name);
							}
						};
					}
					if (delay > 0) {
						ScheduledFuture<?> future = sched.schedule(fr, delay, TimeUnit.MILLISECONDS);
						if (name != null) {
							synchronized (orders) {
								orders.put(name, future);
								orderRunnables.put(name, fr);
							}
						}
					} else {
						sched.execute(fr);
					}
				}
			} catch (IOException e) {
				log("ERROR", "Failed to listen for orders", e);
			} finally {
				System.exit(0);
			}
		}, "Reader thread").start();
		
		startMainThreadRunner();
	}

	public static void startMainThreadRunner() {
		while (true) {
			try {
				mainThreadWorkQueue.take().run();
			} catch (InterruptedException e) {
			}
		}
	}

	public static void log(String flavor, String msg) {
		System.err.println(flavor+"|"+msg);
	}

	public static void log(String flavor, String msg, Throwable t) {
		if (t != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			t.printStackTrace(pw);
			pw.flush();
			for (String line : sw.toString().split(System.lineSeparator())) {
				log(flavor, line);
			}
		}
		log(flavor, msg);
	}
	
	public static void reportCloseRequest() {
		System.out.println("closeRequested");
	}

	public static void reportChoice(String name, String opt) {
		System.out.println("alert:"+name+":"+opt);
	}

	public static void reportDone() {
		System.exit(0);
	}
	
	public static void addTranslation(String key, String value) {
		strings.put(key, value);
	}

	public static String format(String key, Object... args) {
		String[] split = key.split("¤");
		if (split.length > 1) {
			int origLen = args.length;
			args = Arrays.copyOf(args, origLen+split.length-1);
			System.arraycopy(split, 1, args, origLen, split.length-1);
			for (int i = 1; i < args.length; i++) {
				if (strings.containsKey(args[i])) {
					args[i] = format((String)args[i], args);
				}
			}
		}
		return String.format(strings.getOrDefault(split[0], "??"+split[0]+"/"+Arrays.toString(args)).replace("%n", "\n"), args);
	}

	public static String[] format(String[] keys) {
		String[] out = new String[keys.length];
		for (int i = 0; i < keys.length; i++) {
			out[i] = format(keys[i]);
		}
		return out;
	}

	public static boolean isMainThread() {
		return Thread.currentThread() == mainThread;
	}
	
	public static void runOnMainThread(Runnable r) {
		if (isMainThread()) {
			r.run();
		} else {
			mainThreadWorkQueue.add(r);
		}
	}

}
