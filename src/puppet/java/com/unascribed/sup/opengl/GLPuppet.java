package com.unascribed.sup.opengl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
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

import static org.lwjgl.system.MemoryUtil.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.util.freetype.FreeType.*;

public class GLPuppet {

	public static void main(String[] args) {
		PuppetDelegate del = start();
	}
	
	private static final float[][] colors = new float[ColorChoice.values().length][];
	private static long mainWindow;
	private static FT_Face firaSans, firaSansBold, firaSansItalic, firaSansBoldItalic;
	private static int scratchTex;
	
	public static PuppetDelegate start() {
		if (!glfwInit()) {
			Puppet.log("ERROR", "Failed to initialize GLFW: "+getError());
			return null;
		}
		long ftLibrary;
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
		
		glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_FALSE);
		glfwWindowHint(GLFW_DECORATED, GL_TRUE);
		glfwWindowHint(GLFW_VISIBLE, GL_TRUE);
		glfwWindowHint(GLFW_RESIZABLE, GL_FALSE);
		glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW_TRUE);
		glfwWindowHintString(GLFW_WAYLAND_APP_ID, "com.unascribed.sup");
		glfwWindowHintString(GLFW_X11_CLASS_NAME, "com.unascribed.sup");
		glfwWindowHintString(GLFW_X11_INSTANCE_NAME, "com.unascribed.sup");
		
		mainWindow = glfwCreateWindow(512, 128, "unsup v"+Util.VERSION, NULL, NULL);
		if (mainWindow == 0) {
			Puppet.log("ERROR", "Failed to create GLFW window: "+getError());
			glfwTerminate();
			return null;
		}
		glfwMakeContextCurrent(mainWindow);
		{
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
		
		scratchTex = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, scratchTex);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		
		OpenGLDebug.install();
		
		while (mainWindow > 0) { // temp hack
			render();
		}
		glfwTerminate();
		
		return new PuppetDelegate() {
			
			@Override
			public void setVisible(boolean visible) {
			}
			
			@Override
			public void setTitle(String title) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void setSubtitle(String subtitle) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void setProgressIndeterminate() {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void setProgressDeterminate() {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void setProgress(int permil) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void setDone() {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void setColor(ColorChoice choice, int color) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void openMessageDialog(String name, String title, String body, MessageType messageType, String[] options) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void openFlavorDialog(String name, List<FlavorGroup> groups) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void openChoiceDialog(String name, String title, String body, String def, String[] options) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void build() {
				// TODO Auto-generated method stub
				
			}
		};
	}
	
	private static void render() {
		String str = "Hello, World!\nÄËÏÖÜ";
		
		glfwPollEvents();
		
		glClearColor(0, 0.5f, 0.15f, 1);
		glClear(GL_COLOR_BUFFER_BIT);
		
		glMatrixMode(GL_PROJECTION);
		glViewport(0, 0, 512, 128);
		glLoadIdentity();
		glOrtho(0, 512, 128, 0, 100, 1000);
		glMatrixMode(GL_MODELVIEW);
		glLoadIdentity();
		glTranslatef(0, 0, -200);
		
		glShadeModel(GL_SMOOTH);
		glDisable(GL_CULL_FACE);
		glDisable(GL_LIGHTING);

		float x = 0;
		float y = 64;
		FT_Face f = firaSansBold;
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glEnable(GL_BLEND);
		for (int i = 0; i < str.length(); i++) {
			if (FT_Load_Char(f, str.charAt(i), FT_LOAD_RENDER) != 0) {
				if (FT_Load_Char(f, '�', FT_LOAD_RENDER) != 0) {
					continue;
				}
			}
			int w = f.glyph().bitmap().width();
			int h = f.glyph().bitmap().rows();
			if (w != 0 && h != 0) {
				float xo = x+f.glyph().bitmap_left();
				float yo = y-f.glyph().bitmap_top();
				glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA8, w, h, 0, GL_ALPHA, GL_UNSIGNED_BYTE, f.glyph().bitmap().buffer(w*h));
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
			
			x += f.glyph().advance().x()/64f;
			y += f.glyph().advance().y()/64f;
		}
		
		glfwSwapBuffers(mainWindow);
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
