package com.unascribed.sup.opengl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;

import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.MessageType;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.PuppetDelegate;
import com.unascribed.sup.Util;
import com.unascribed.sup.WindowIcons;
import com.unascribed.sup.data.FlavorGroup;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.unascribed.sup.WindowIcons.*;
import static com.unascribed.sup.opengl.GL.*;

import static org.lwjgl.system.MemoryUtil.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.util.freetype.FreeType.*;

public class GLPuppet {

	public static void main(String[] args) {
		ColorChoice.usePrettyDefaults = true;
		PuppetDelegate del = start();
		del.build();
		del.setVisible(true);
	}
	
	private static int[] colors;
	private static long mainWindow;
	private static FT_Face firaSans, firaSansBold, firaSansItalic, firaSansBoldItalic;
	private static FT_Bitmap scratchBitmap;
	private static long ftLibrary;
	private static int scratchTex;
	private static GLThrobber throbber = new GLThrobber();
	
	private static String title = "Reticulating splines...";
	private static String subtitle = "";
	private static float prog = 0.5f;
	
	private static volatile boolean run = true;
	
	public static int getColor(ColorChoice choice) {
		return colors[choice.ordinal()];
	}
	
	public static PuppetDelegate start() {
		colors = ColorChoice.createLookup();
		
		glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
		
		if (!glfwInit()) {
			Puppet.log("ERROR", "Failed to initialize GLFW: "+getError());
			return null;
		}
		
		glfwSetErrorCallback((error, description) -> {
			Puppet.log("WARN", "GLFW error: "+memASCII(description));
		});
		
		{
			PointerBuffer libraryBuf = memAllocPointer(1);
			int ftError = FT_Init_FreeType(libraryBuf);
			if (ftError != 0) {
				glfwTerminate();
				Puppet.log("ERROR", "Failed to initialize FreeType: "+FT_Error_String(ftError));
				return null;
			}
			ftLibrary = libraryBuf.get(0);
			memFree(libraryBuf);
		}
		
		{
			PointerBuffer face1 = memAllocPointer(1);
			PointerBuffer face2 = memAllocPointer(1);
			PointerBuffer face3 = memAllocPointer(1);
			PointerBuffer face4 = memAllocPointer(1);
			try {
				boolean success = true;
				success &= loadFont("FiraSans-Regular.ttf", ftLibrary, face1);
				if (success) success &= loadFont("FiraSans-Bold.ttf", ftLibrary, face2);
				if (success) success &= loadFont("FiraSans-Italic.ttf", ftLibrary, face3);
				if (success) success &= loadFont("FiraSans-BoldItalic.ttf", ftLibrary, face4);
				
				if (!success) {
					glfwTerminate();
					FT_Done_FreeType(ftLibrary);
					return null;
				}
				
				firaSans = FT_Face.create(face1.get(0));
				firaSansBold = FT_Face.create(face2.get(0));
				firaSansItalic = FT_Face.create(face3.get(0));
				firaSansBoldItalic = FT_Face.create(face4.get(0));
			} finally {
				memFree(face1);
				memFree(face2);
				memFree(face3);
				memFree(face4);
			}
		}
		
		int ftError = FT_Set_Char_Size(firaSans, 0, 16*64, 150, 150);
		if (ftError != 0) {
			glfwTerminate();
			Puppet.log("ERROR", "Failed to configure font size: "+FT_Error_String(ftError));
			return null;
		}
		ftError = FT_Set_Char_Size(firaSansBold, 0, 16*64, 150, 150);
		if (ftError != 0) {
			glfwTerminate();
			Puppet.log("ERROR", "Failed to configure font size: "+FT_Error_String(ftError));
			return null;
		}
		ftError = FT_Set_Char_Size(firaSansItalic, 0, 16*64, 150, 150);
		if (ftError != 0) {
			glfwTerminate();
			Puppet.log("ERROR", "Failed to configure font size: "+FT_Error_String(ftError));
			return null;
		}
		ftError = FT_Set_Char_Size(firaSansBoldItalic, 0, 16*64, 150, 150);
		if (ftError != 0) {
			glfwTerminate();
			Puppet.log("ERROR", "Failed to configure font size: "+FT_Error_String(ftError));
			return null;
		}
		
		scratchBitmap = FT_Bitmap.malloc();
		PointerBuffer buf = memPointerBuffer(scratchBitmap.address()+FT_Bitmap.BUFFER, 1);
		buf.put(memAlloc(1));

		glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 4);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_FALSE);
		glfwWindowHint(GLFW_DECORATED, GL_TRUE);
		glfwWindowHint(GLFW_VISIBLE, GL_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GL_FALSE);
		glfwWindowHint(GLFW_FLOATING, GL_TRUE);
		glfwWindowHint(GLFW_SAMPLES, 16);
		glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW_TRUE);
		glfwWindowHintString(GLFW_WAYLAND_APP_ID, "com.unascribed.sup");
		glfwWindowHintString(GLFW_X11_CLASS_NAME, "com.unascribed.sup");
		glfwWindowHintString(GLFW_X11_INSTANCE_NAME, "com.unascribed.sup");
		
		mainWindow = glfwCreateWindow(480, 80, "unsup v"+Util.VERSION, NULL, NULL);
		if (mainWindow == 0) {
			Puppet.log("ERROR", "Failed to create GLFW window: "+getError());
			glfwTerminate();
			return null;
		}
		
		if (glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) {
			try {
				new File(".unsup-tmp").mkdirs();
				File icon = new File(".unsup-tmp/icon.png");
				try (FileOutputStream fos = new FileOutputStream(icon)) {
					fos.write(QDPNG.write(WindowIcons.highres));
				}
				File desktop = new File(getApplicationsDir(), "com.unascribed.sup.desktop");
				try (FileOutputStream fos = new FileOutputStream(desktop);
						InputStream is = GLPuppet.class.getClassLoader().getResourceAsStream("com/unascribed/sup/unsup.desktop")) {
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
				glfwMakeContextCurrent(mainWindow);
				if (glfwGetPlatform() != GLFW_PLATFORM_WAYLAND) {
					ByteBuffer lowresPx = memAlloc(lowres.getPixelData().length);
					ByteBuffer highresPx = memAlloc(highres.getPixelData().length);
					lowresPx.put(lowres.getPixelData());
					highresPx.put(highres.getPixelData());
					lowresPx.flip();
					highresPx.flip();
					
					GLFWImage.Buffer buffer = GLFWImage.malloc(2);
					buffer.get(0)
						.width(lowres.getWidth()).height(lowres.getHeight())
						.pixels(lowresPx);
					buffer.get(1)
						.width(highres.getWidth()).height(highres.getHeight())
						.pixels(highresPx);
					glfwSetWindowIcon(mainWindow, buffer);
					memFree(buffer);
					memFree(lowresPx);
					memFree(highresPx);
					
					long monitor = glfwGetPrimaryMonitor();
					int[] x = new int[1];
					int[] y = new int[1];
					int[] w = new int[1];
					int[] h = new int[1];
					glfwGetMonitorWorkarea(monitor, x, y, w, h);
					glfwSetWindowPos(mainWindow, x[0]+(w[0]-480)/2, y[0]+(h[0]-40)/2);
				}
				
				if (glfwExtensionSupported("GLX_EXT_swap_control_tear") || glfwExtensionSupported("WGL_EXT_swap_control_tear")) {
					glfwSwapInterval(-1);
				} else {
					glfwSwapInterval(1);
				}
				
				glfwSetWindowCloseCallback(mainWindow, unused -> {
					Puppet.reportCloseRequest();
				});
				
				GL.createCapabilities();
				
				Puppet.log("DEBUG", "OpenGL Version: "+glGetString(GL_VERSION));
				Puppet.log("DEBUG", "OpenGL Renderer: "+glGetString(GL_RENDERER));
				Puppet.log("DEBUG", "OpenGL Vendor: "+glGetString(GL_VENDOR));
				
				glfwMakeContextCurrent(NULL);
			}
			
			@Override
			public void setVisible(boolean visible) {
				if (visible) {
					glfwShowWindow(mainWindow);
				} else {
					glfwHideWindow(mainWindow);
				}
				if (scratchTex == 0) {
					new Thread(() -> {
						glfwMakeContextCurrent(mainWindow);
						GL.createCapabilities();
						
						scratchTex = glGenTextures();
						glBindTexture(GL_TEXTURE_2D, scratchTex);
						glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
						glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
						
						OpenGLDebug.install();
						
						while (run) {
							render();
						}
					}, "GL thread").start();
				}
			}
			
			@Override
			public void setTitle(String title) {
				GLPuppet.title = title;
			}
			
			@Override
			public void setSubtitle(String subtitle) {
				GLPuppet.subtitle = subtitle;
			}
			
			@Override
			public void setProgressIndeterminate() {
				prog = -1;
			}
			
			@Override
			public void setProgressDeterminate() {
				prog = 0;
			}
			
			@Override
			public void setProgress(int permil) {
				prog = permil/1000f;
			}
			
			@Override
			public void setDone() {
				throbber.animateDone();
			}
			
			@Override
			public void setColor(ColorChoice choice, int color) {
				colors[choice.ordinal()] = color;
			}
			
			@Override
			public void openMessageDialog(String name, String title, String body, MessageType messageType, String[] options) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public void openFlavorDialog(String name, List<FlavorGroup> groups) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public void openChoiceDialog(String name, String title, String body, String def, String[] options) {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	private static void render() {
		glfwPollEvents();
		
		int bg = getColor(ColorChoice.BACKGROUND);
		glClearColor(((bg >> 16)&0xFF)/255f, ((bg >> 8)&0xFF)/255f, ((bg >> 0)&0xFF)/255f, 1);
		glClear(GL_COLOR_BUFFER_BIT);
		
		glMatrixMode(GL_PROJECTION);
		glViewport(0, 0, 480, 80);
		glLoadIdentity();
		glOrtho(0, 480, 80, 0, 100, 1000);
		glMatrixMode(GL_MODELVIEW);
		glLoadIdentity();
		glTranslatef(0, 0, -200);
		
		glShadeModel(GL_SMOOTH);
		glDisable(GL_CULL_FACE);
		glDisable(GL_LIGHTING);
		glDisable(GL_TEXTURE_2D);
		
		glColor3f(1, 1, 1);

		throbber.render(40, 32, 40);
		glPushMatrix();
			glColorPacked3i(getColor(ColorChoice.TITLE));
			drawString(firaSansBold, 64, 31, 24, title);
		glPopMatrix();
		glPushMatrix();
			glColorPacked3i(getColor(ColorChoice.SUBTITLE));
			drawString(firaSans, 64, 52, 14, subtitle);
		glPopMatrix();
		
		if (prog >= 0) {
			glBegin(GL_QUADS);
				glColorPacked3i(getColor(ColorChoice.PROGRESSTRACK));
				glVertex2f(64, 70);
				glVertex2f(476, 70);
				glVertex2f(476, 76);
				glVertex2f(64, 76);
				
				glColorPacked3i(getColor(ColorChoice.PROGRESS));
				glVertex2f(65, 71);
				glVertex2f(65+(prog*412), 71);
				glVertex2f(65+(prog*412), 75);
				glVertex2f(65, 75);
			glEnd();
		}
		
		glfwSwapBuffers(mainWindow);
	}
	
	private static void drawString(FT_Face f, float x, float y, float size, String str) {
		if (FT_Set_Char_Size(f, 0, (int)(size*64), 72, 72) != 0) return;
		glEnable(GL_TEXTURE_2D);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glEnable(GL_BLEND);
		for (int i = 0; i < str.length(); i++) {
			if (FT_Load_Char(f, str.charAt(i), FT_LOAD_RENDER) != 0) {
				if (FT_Load_Char(f, '\uFFFD', FT_LOAD_RENDER) != 0) {
					continue;
				}
			}
			if (FT_Bitmap_Convert(ftLibrary, f.glyph().bitmap(), scratchBitmap, 4) != 0) continue;
			int w = scratchBitmap.width();
			int h = scratchBitmap.rows();
			if (w != 0 && h != 0) {
				float xo = x+f.glyph().bitmap_left();
				float yo = y-f.glyph().bitmap_top();
				glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA8, w, h, 0, GL_ALPHA, GL_UNSIGNED_BYTE, scratchBitmap.buffer(w*h));
				glEnable(GL_TEXTURE_2D);
				glBegin(GL_QUADS);
					glTexCoord2i(0, 0);
					glVertex2f(xo, yo);
					glTexCoord2i(1, 0);
					glVertex2f(xo+(w), yo);
					glTexCoord2i(1, 1);
					glVertex2f(xo+(w), yo+(h));
					glTexCoord2i(0, 1);
					glVertex2f(xo, yo+(h));
				glEnd();
				glDisable(GL_TEXTURE_2D);
			}
			
			glTranslatef(f.glyph().advance().x()/64f, f.glyph().advance().y()/64f, 0);
		}
	}

	private static boolean loadFont(String name, long ftLibrary, PointerBuffer ptr) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (InputStream in = GLPuppet.class.getClassLoader().getResourceAsStream("com/unascribed/sup/"+name)) {
			Util.copy(in, baos);
		} catch (IOException e) {
			Puppet.log("WARN", "Failed to load font "+name, e);
			return false;
		}
		ByteBuffer buf = memAlloc(baos.size());
		buf.put(baos.toByteArray());
		buf.flip();
		int error = FT_New_Memory_Face(ftLibrary, buf, 0, ptr);
		if (error != 0) {
			Puppet.log("WARN", "Failed to load font "+name+": "+FT_Error_String(error));
			return false;
		}
		return true;
	}

	private static String getError() {
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
