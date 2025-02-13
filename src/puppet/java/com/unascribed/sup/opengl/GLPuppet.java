package com.unascribed.sup.opengl;

import org.lwjgl.PointerBuffer;
import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.MessageType;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.PuppetDelegate;
import com.unascribed.sup.Util;
import com.unascribed.sup.WindowIcons;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.opengl.util.QDPNG;
import com.unascribed.sup.opengl.window.MainWindow;
import com.unascribed.sup.opengl.window.MessageDialogWindow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.glfw.GLFW.*;

public class GLPuppet {

	public static void main(String[] args) {
		ColorChoice.usePrettyDefaults = true;
		PuppetDelegate del = start();
		del.build();
		del.setVisible(true);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		del.openMessageDialog("xx", "File conflict",
							"潮水冲淡了他们留在沙滩上的脚印",
							MessageType.QUESTION, new String[] {"Yes to All", "Yes", "No to All", "No", "Cancel"}, "Yes");
	}
	
	private static int[] colors;
	private static MainWindow mainWindow;
	
	public static int getColor(ColorChoice choice) {
		return colors[choice.ordinal()];
	}
	
	public static PuppetDelegate start() {
		String uiscale = System.getProperty("sun.java2d.uiScale");
		float dpiScale = uiscale != null ? Float.parseFloat(uiscale) : 1;
		colors = ColorChoice.createLookup();
		
		if (!glfwInit()) {
			Puppet.log("ERROR", "Failed to initialize GLFW: "+getGLFWErrorDescription());
			return null;
		}
		
		glfwSetErrorCallback((error, description) -> {
			Puppet.log("WARN", "GLFW error: "+memASCII(description));
		});
		
		mainWindow = new MainWindow();
		
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
		
		return new PuppetDelegate() {
			
			@Override
			public void build() {
				mainWindow.create("unsup v"+Util.VERSION, 480, 80, dpiScale);
			}
			
			@Override
			public void setVisible(boolean visible) {
				mainWindow.setVisible(visible);
			}
			
			@Override
			public void setTitle(String title) {
				mainWindow.title = title;
				mainWindow.needsFullRedraw = true;
			}
			
			@Override
			public void setSubtitle(String subtitle) {
				mainWindow.subtitle = subtitle;
				mainWindow.needsFullRedraw = true;
			}
			
			@Override
			public void setProgressIndeterminate() {
				mainWindow.prog = -1;
			}
			
			@Override
			public void setProgressDeterminate() {
				mainWindow.prog = 0;
			}
			
			@Override
			public void setProgress(int permil) {
				mainWindow.prog = permil/1000f;
			}
			
			@Override
			public void setDone() {
				mainWindow.throbber.animateDone();
			}
			
			@Override
			public void setColor(ColorChoice choice, int color) {
				colors[choice.ordinal()] = color;
			}
			
			@Override
			public void openMessageDialog(String name, String title, String body, MessageType messageType, String[] options, String def) {
				MessageDialogWindow diag = new MessageDialogWindow(name, title, body, messageType, options);
				for (int i = 0; i < options.length; i++) {
					if (options[i].equals(def)) {
						diag.highlighted = i;
						break;
					}
				}
				diag.create(dpiScale);
				diag.setVisible(true);
			}
			
			@Override
			public void openFlavorDialog(String name, List<FlavorGroup> groups) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public void openChoiceDialog(String name, String title, String body, String[] options, String def) {
				openMessageDialog(name, title, body, MessageType.NONE, options, def);
			}
		};
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
