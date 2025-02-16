package com.unascribed.sup.puppet.opengl;

import org.lwjgl.PointerBuffer;

import com.unascribed.sup.AlertMessageType;
import com.unascribed.sup.SysProps;
import com.unascribed.sup.Util;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.pieces.Latch;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.PuppetDelegate;
import com.unascribed.sup.puppet.Translate;
import com.unascribed.sup.puppet.WindowIcons;
import com.unascribed.sup.puppet.opengl.util.QDPNG;
import com.unascribed.sup.puppet.opengl.window.FlavorDialogWindow;
import com.unascribed.sup.puppet.opengl.window.ProgressWindow;
import com.unascribed.sup.puppet.opengl.window.MessageDialogWindow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.glfw.GLFW.*;

public class GLPuppet {
	
	private static ProgressWindow mainWindow;
	
	public static boolean scaleOverridden = false;
	
	private static final Latch buildLatch = new Latch();
	
	public static PuppetDelegate start() {
		// just a transliteration of https://wiki.archlinux.org/title/HiDPI plus some unsup-specific extras
		OptionalDouble oDpiScale = scanScale("unsup.scale", "sun.java2d.uiScale", "glass.gtk.uiScale",
				"UNSUP_SCALE", "QT_SCALE_FACTOR", "GDK_DPI_SCALE×GDK_SCALE", "ELM_SCALE");
		scaleOverridden = oDpiScale.isPresent();
		double dpiScale = oDpiScale.orElse(1);
		
		switch (SysProps.PUPPET_PLATFORM) {
			case COCOA:
				glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_COCOA);
				break;
			case NULL:
				glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
				break;
			case WAYLAND:
				glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
				break;
			case WIN32:
				glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WIN32);
				break;
			case X11:
				glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
				break;
			case AUTO:
				break;
		}
		
		if (!glfwInit()) {
			Puppet.log("ERROR", "Failed to initialize GLFW: "+getGLFWErrorDescription());
			return null;
		}
		
		glfwSetErrorCallback((error, description) -> {
			Puppet.log("WARN", "GLFW error: "+memASCII(description));
		});
		
		mainWindow = new ProgressWindow();
		
		if (glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) {
			try {
				new File(".unsup-tmp").mkdirs();
				File icon = new File(".unsup-tmp/icon.png");
				try (FileOutputStream fos = new FileOutputStream(icon)) {
					fos.write(QDPNG.write(WindowIcons.highres));
				}
				File desktop = new File(getApplicationsDir(), "com.unascribed.sup.desktop");
				try (FileOutputStream fos = new FileOutputStream(desktop);
						InputStream is = GLPuppet.class.getClassLoader().getResourceAsStream("com/unascribed/sup/assets/unsup.desktop")) {
					Util.copy(is, fos);
					// if your linux system isn't configured to use UTF-8 then i can't help you
					fos.write(icon.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
					fos.write('\n');
				}
				desktop.deleteOnExit();
				icon.deleteOnExit();
			} catch (Throwable t) {}
		}
		
		Puppet.sched.scheduleWithFixedDelay(() -> {
			Puppet.runOnMainThread(() -> glfwPollEvents());
		}, 0, 30, TimeUnit.MILLISECONDS);
		
		return new PuppetDelegate() {
			
			@Override
			public void build() {
				Puppet.runOnMainThread(() -> {
					mainWindow.create(null, "unsup v"+Util.VERSION, 480, 80, dpiScale);
					buildLatch.release();
				});
			}
			
			@Override
			public void setVisible(boolean visible) {
				buildLatch.awaitUninterruptibly();
				mainWindow.setVisible(visible);
			}
			
			@Override
			public void setTitle(String title) {
				synchronized (mainWindow) {
					mainWindow.title = Translate.format(title);
					mainWindow.needsFullRedraw = true;
				}
			}
			
			@Override
			public void setSubtitle(String subtitle) {
				synchronized (mainWindow) {
					mainWindow.downloading = null;
					mainWindow.subtitle = Translate.format(subtitle);
					mainWindow.needsFullRedraw = true;
				}
			}
			
			@Override
			public void setDownloading(String[] files) {
				synchronized (mainWindow) {
					mainWindow.downloading = files;
					mainWindow.needsFullRedraw = true;
				}
			}
			
			@Override
			public void setProgressIndeterminate() {
				synchronized (mainWindow) {
					mainWindow.prog = -1;
				}
			}
			
			@Override
			public void setProgressDeterminate() {
				synchronized (mainWindow) {
					mainWindow.prog = 0;
				}
			}
			
			@Override
			public void setProgress(int permil) {
				synchronized (mainWindow) {
					mainWindow.prog = permil/1000f;
				}
			}
			
			@Override
			public void setDone() {
				synchronized (mainWindow) {
					mainWindow.throbber.animateDone();
				}
			}
			
			@Override
			public void offerChangeFlavors(String name) {
				Puppet.exitOnDone = false;
				synchronized (mainWindow) {
					mainWindow.offerChangeFlavorsName = name;
					mainWindow.offerChangeFlavors = System.nanoTime();
					setTitle(Translate.format("title.done"));
					setSubtitle(Translate.format("subtitle.waiting_for_flavors"));
					setDone();
				}
			}
			
			@Override
			public void openMessageDialog(String name, String title, String body, AlertMessageType messageType, String[] options, String def) {
				MessageDialogWindow diag = new MessageDialogWindow(name, title, body, messageType, options, def);
				Puppet.runOnMainThread(() -> {
					diag.create(mainWindow, dpiScale);
					diag.setVisible(true);
				});
			}
			
			@Override
			public void openFlavorDialog(String name, List<FlavorGroup> groups) {
				FlavorDialogWindow diag = new FlavorDialogWindow(name, groups);
				Puppet.runOnMainThread(() -> {
					diag.create(mainWindow, dpiScale);
					diag.setVisible(true);
				});
			}
			
			@Override
			public void openChoiceDialog(String name, String title, String body, String[] options, String def) {
				openMessageDialog(name, title, body, AlertMessageType.NONE, options, def);
			}
		};
	}

	private static double parseScale(String uiscale) throws NumberFormatException {
		if (uiscale.endsWith("dpi")) {
			return Double.parseDouble(uiscale.substring(0, uiscale.length()-3))/96;
		} else if (uiscale.endsWith("%")) {
			return Double.parseDouble(uiscale.substring(0, uiscale.length()-1))/100;
		} else {
			return Double.parseDouble(uiscale);
		}
	}
	
	private static OptionalDouble scanScale(String... keys) {
		for (String key : keys) {
			if (key.contains("×")) {
				String[] split = key.split("×");
				OptionalDouble left = scanScale(split[0]);
				OptionalDouble right = scanScale(split[1]);
				if (left.isPresent() && right.isPresent()) {
					return OptionalDouble.of(left.getAsDouble()*right.getAsDouble());
				} else if (left.isPresent()) {
					return left;
				} else if (right.isPresent()) {
					return right;
				}
			} else {
				String prop = System.getProperty(key);
				if (prop != null) {
					try {
						return OptionalDouble.of(parseScale(prop));
					} catch (NumberFormatException e) {}
				}
				String env = System.getenv(key);
				if (env != null) {
					try {
						return OptionalDouble.of(parseScale(env));
					} catch (NumberFormatException e) {}
				}
			}
		}
		return OptionalDouble.empty();
	}

	public static String getGLFWErrorDescription() {
		PointerBuffer buf = memAllocPointer(1);
		glfwGetError(buf);
		String s = buf.getStringASCII();
		memFree(buf);
		return s;
	}

	private static File getApplicationsDir() {
		String home = System.getenv("HOME");
		if (home == null || home.trim().isEmpty()) {
			home = System.getProperty("user.home");
		}
		String dir = System.getenv("XDG_DATA_HOME");
		if (dir == null || dir.trim().isEmpty()) {
			dir = home+"/.local/share";
		}
		return new File(dir, "applications");
	}
	
}
