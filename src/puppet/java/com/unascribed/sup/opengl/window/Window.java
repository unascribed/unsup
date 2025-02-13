package com.unascribed.sup.opengl.window;

import static com.unascribed.sup.WindowIcons.highres;
import static com.unascribed.sup.WindowIcons.lowres;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import java.nio.ByteBuffer;

import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.opengl.GLPuppet;
import com.unascribed.sup.opengl.pieces.FontManager;
import com.unascribed.sup.opengl.pieces.OpenGLDebug;

import static org.lwjgl.opengl.GL11.*;

public abstract class Window {
	
	protected long handle;
	protected int width, height;
	protected float dpiScaleX, dpiScaleY;
	
	protected final FontManager font = new FontManager();
	protected int scratchTex;
	
	public volatile boolean run = true;
	
	public void create(String title, int width, int height, float dpiScale) {
		this.width = width;
		this.height = height;
		
		this.dpiScaleX = dpiScale;
		this.dpiScaleY = dpiScale;
		
		glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 4);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);
		glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
		glfwWindowHint(GLFW_FLOATING, GLFW_TRUE);
		glfwWindowHint(GLFW_SAMPLES, 16);
		glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW_TRUE);
		glfwWindowHintString(GLFW_WAYLAND_APP_ID, "com.unascribed.sup");
		glfwWindowHintString(GLFW_X11_CLASS_NAME, "com.unascribed.sup");
		glfwWindowHintString(GLFW_X11_INSTANCE_NAME, getClass().getName());
		
		int physW = (int)(width*dpiScaleX);
		int physH = (int)(height*dpiScaleY);
		handle = glfwCreateWindow(physW, physH, title, NULL, NULL);
		if (handle == 0) {
			Puppet.log("ERROR", "Failed to create GLFW window: "+GLPuppet.getGLFWErrorDescription());
		};
		
		glfwMakeContextCurrent(handle);
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
			glfwSetWindowIcon(handle, buffer);
			memFree(buffer);
			memFree(lowresPx);
			memFree(highresPx);
			
			long monitor = glfwGetPrimaryMonitor();
			int[] x = new int[1];
			int[] y = new int[1];
			int[] w = new int[1];
			int[] h = new int[1];
			glfwGetMonitorWorkarea(monitor, x, y, w, h);
			glfwSetWindowPos(handle, x[0]+(w[0]-physW)/2, y[0]+(h[0]-physH)/2);
		}
		
		if (glfwExtensionSupported("GLX_EXT_swap_control_tear") || glfwExtensionSupported("WGL_EXT_swap_control_tear")) {
			glfwSwapInterval(-1);
		} else {
			glfwSwapInterval(1);
		}
		
		glfwSetWindowCloseCallback(handle, unused -> {
			Puppet.reportCloseRequest();
		});
		
		GL.createCapabilities();
		
		int bg = GLPuppet.getColor(ColorChoice.BACKGROUND);
		glClearColor(((bg >> 16)&0xFF)/255f, ((bg >> 8)&0xFF)/255f, ((bg >> 0)&0xFF)/255f, 1);
		
		glShadeModel(GL_SMOOTH);
		glDisable(GL_CULL_FACE);
		glDisable(GL_LIGHTING);
		
		setupGL();
		
		glfwMakeContextCurrent(NULL);
	}
	
	protected abstract void setupGL();
	
	protected boolean needsRerender() {
		return true;
	}
	
	public void setVisible(boolean visible) {
		if (visible) {
			glfwShowWindow(handle);
		} else {
			glfwHideWindow(handle);
		}
		if (scratchTex == 0) {
			new Thread(() -> {
				glfwMakeContextCurrent(handle);
				GL.createCapabilities();
				
				scratchTex = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, scratchTex);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
				
				OpenGLDebug.install();
				
				while (run) {
					render();
				}
			}, getClass().getSimpleName()+" GL thread").start();
		}
	}

	public void render() {
		glfwPollEvents();
		
		if (needsRerender()) {
			int[] fbw = new int[1];
			int[] fbh = new int[1];
			glfwGetFramebufferSize(handle, fbw, fbh);
			
			glMatrixMode(GL_PROJECTION);
			glViewport(0, 0, fbw[0], fbh[0]);
			glLoadIdentity();
			glOrtho(0, fbw[0], fbh[0], 0, 100, 1000);
			glMatrixMode(GL_MODELVIEW);
			glLoadIdentity();
			glTranslatef(0, 0, -200);
			glScalef(dpiScaleX = (fbw[0]/(float)width), dpiScaleY = (fbh[0]/(float)height), 1);
			font.dpiScale = dpiScaleX;
			
			glDisable(GL_TEXTURE_2D);
			
			glColor3f(1, 1, 1);
	
			renderInner();
			
			glfwSwapBuffers(handle);
		}
	}
	
	protected abstract void renderInner();

}
