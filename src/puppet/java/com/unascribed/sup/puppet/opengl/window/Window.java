package com.unascribed.sup.puppet.opengl.window;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;

import com.unascribed.sup.puppet.ColorChoice;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.opengl.GLPuppet;
import com.unascribed.sup.puppet.opengl.pieces.FontManager;
import com.unascribed.sup.puppet.opengl.pieces.OpenGLDebug;

import static com.unascribed.sup.puppet.WindowIcons.*;
import static com.unascribed.sup.puppet.opengl.util.GL.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

public abstract class Window {
	
	private static final Map<Class<? extends Window>, AtomicInteger> threadNumbers = new HashMap<>();
	
	protected Window parent;
	
	protected long handle;
	protected int width, height;
	protected int fbWidth, fbHeight;
	protected double dpiScaleX, dpiScaleY;
	
	protected double mouseX, mouseY;
	protected boolean mouseClicked;
	
	protected final FontManager font = new FontManager();
	protected int scratchTex;
	
	public volatile boolean run = true;
	
	public boolean needsFullRedraw = true;
	
	private long timeShown;
	private boolean honorNeedsRender = false;
	
	private Thread renderThread;
	
	protected boolean updateDpiScaleByFramebuffer = true;
	protected long clickCursor;
	
	public void create(Window parent, String title, int width, int height, double dpiScale) {
		if (!Puppet.isMainThread()) throw new IllegalStateException("Must be on main thread");
		
		this.parent = parent;
		
		this.width = width;
		this.height = height;
		
		this.dpiScaleX = dpiScale;
		this.dpiScaleY = dpiScale;
		
		glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);
		glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_ALPHA_BITS, GLFW_DONT_CARE);
		glfwWindowHint(GLFW_DEPTH_BITS, GLFW_DONT_CARE);
		glfwWindowHint(GLFW_SAMPLES, 16);
		glfwWindowHint(GLFW_SCALE_FRAMEBUFFER, GLFW_TRUE);
		glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE);
		glfwWindowHintString(GLFW_WAYLAND_APP_ID, "com.unascribed.sup");
		glfwWindowHintString(GLFW_X11_CLASS_NAME, "com.unascribed.sup");
		glfwWindowHintString(GLFW_X11_INSTANCE_NAME, getClass().getSimpleName());
		
		int physW = (int)(width*dpiScale);
		int physH = (int)(height*dpiScale);
		handle = glfwCreateWindow(physW, physH, title, NULL, NULL);
		if (handle == 0) {
			throw new RuntimeException("Failed to create GLFW window: "+GLPuppet.getGLFWErrorDescription());
		}
		
		clickCursor = glfwCreateStandardCursor(GLFW_POINTING_HAND_CURSOR);
		
		glfwSetWindowRefreshCallback(handle, window -> {
			synchronized (this) {
				needsFullRedraw = true;
			}
		});
		
		if (!GLPuppet.scaleOverridden) {
			glfwSetWindowContentScaleCallback(handle, (window, xscale, yscale) -> {
				synchronized (this) {
					needsFullRedraw = true;
				}
			});
		}
		
		glfwSetFramebufferSizeCallback(handle, (window, newWidth, newHeight) -> {
			synchronized (this) {
				fbWidth = newWidth;
				fbHeight = newHeight;
				needsFullRedraw = true;
			}
		});
		
		glfwSetWindowSizeCallback(handle, (window, newWidth, newHeight) -> {
			synchronized (this) {
				needsFullRedraw = true;
			}
		});

		glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
			synchronized (this) {
				mouseX = xpos/dpiScale;
				mouseY = ypos/dpiScale;
				onMouseMove(mouseX, mouseY);
			}
		});
		
		glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
			if (action == GLFW_RELEASE) return;
			if (button == GLFW_MOUSE_BUTTON_LEFT) {
				synchronized (this) {
					mouseClicked = true;
					onMouseClick();
				}
			}
		});
		
		glfwMakeContextCurrent(handle);
		if (glfwGetPlatform() != GLFW_PLATFORM_WAYLAND) {
			if (glfwGetPlatform() != GLFW_PLATFORM_COCOA) {
				ByteBuffer lowresPx = memAlloc(lowres.getPixelData().length);
				ByteBuffer highresPx = memAlloc(highres.getPixelData().length);
				lowresPx.put(lowres.getPixelData());
				highresPx.put(highres.getPixelData());
				((Buffer)lowresPx).flip();
				((Buffer)highresPx).flip();
				
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
			}
			
			int[] x = new int[1];
			int[] y = new int[1];
			int[] w = new int[1];
			int[] h = new int[1];
			if (parent == null) {
				long monitor = glfwGetPrimaryMonitor();
				glfwGetMonitorWorkarea(monitor, x, y, w, h);
			} else {
				glfwGetWindowPos(parent.handle, x, y);
				glfwGetWindowSize(parent.handle, w, h);
			}
			glfwSetWindowPos(handle, x[0]+(w[0]-physW)/2, y[0]+(h[0]-physH)/2);
		}
		
		if (!GLPuppet.scaleOverridden) {
			float[] xs = new float[1];
			float[] ys = new float[1];
			glfwGetWindowContentScale(handle, xs, ys);
			
			if (xs[0] != 1 || ys[0] != 1) {
				dpiScaleX = dpiScale*xs[0];
				dpiScaleY = dpiScale*ys[0];
			}
		}
		
		if (glfwExtensionSupported("GLX_EXT_swap_control_tear") || glfwExtensionSupported("WGL_EXT_swap_control_tear")) {
			glfwSwapInterval(-1);
		} else {
			glfwSwapInterval(1);
		}
		
		GL.createCapabilities();
		
		int bg = ColorChoice.BACKGROUND.get();
		glClearColor(((bg >> 16)&0xFF)/255f, ((bg >> 8)&0xFF)/255f, ((bg >> 0)&0xFF)/255f, 1);
		
		glShadeModel(GL_SMOOTH);
		glDisable(GL_CULL_FACE);
		glDisable(GL_LIGHTING);
		
		setupGL();
		
		glfwMakeContextCurrent(NULL);
	}
	
	protected abstract void setupGL();
	
	protected abstract void onMouseMove(double x, double y);
	protected abstract void onMouseClick();
	
	protected synchronized boolean needsRerender() {
		return true;
	}
	
	public void setVisible(boolean visible) {
		Puppet.runOnMainThread(() -> {
			if (!run) return;
			if (visible) {
				glfwShowWindow(handle);
			} else {
				glfwHideWindow(handle);
			}
		});
		if (renderThread == null) {
			renderThread = new Thread(() -> {
				glfwMakeContextCurrent(handle);
				GL.createCapabilities();
				
				scratchTex = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, scratchTex);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
				
				OpenGLDebug.install();
				
				while (run) {
					if (!render()) {
						try {
							Thread.sleep(30);
						} catch (InterruptedException e) {
						}
					}
				}
				
				glfwMakeContextCurrent(NULL);
				
				Puppet.runOnMainThread(() -> {
					glfwDestroyWindow(handle);
				});
			}, getClass().getSimpleName().replace("Window", "")+"#"+threadNumbers.computeIfAbsent(getClass(), k -> new AtomicInteger(1)).getAndIncrement());
			renderThread.start();
		}
	}

	public boolean render() {
		boolean rendered;
		synchronized (this) {
			if (!honorNeedsRender) {
				if (timeShown == 0) timeShown = System.nanoTime();
				long time = System.nanoTime()-timeShown;
				if (time > TimeUnit.MILLISECONDS.toNanos(500)) {
					honorNeedsRender = true;
				} else {
					needsFullRedraw = true;
				}
			}
			if (!honorNeedsRender || needsRerender()) {
				if (fbWidth == 0) {
					int[] fbw = new int[1];
					int[] fbh = new int[1];
					glfwGetFramebufferSize(handle, fbw, fbh);
					fbWidth = fbw[0];
					fbHeight = fbh[0];
				}
				
				glMatrixMode(GL_PROJECTION);
				glViewport(0, 0, fbWidth, fbHeight);
				glLoadIdentity();
				glOrtho(0, fbWidth, fbHeight, 0, 100, 1000);
				glMatrixMode(GL_MODELVIEW);
				glLoadIdentity();
				glTranslatef(0, 0, -200);
				if (updateDpiScaleByFramebuffer) {
					dpiScaleX = (fbWidth/(double)width);
					dpiScaleY = (fbHeight/(double)height);
				}
				glScaled(dpiScaleX, dpiScaleY, 1);
				font.dpiScale = dpiScaleX;
				
				glDisable(GL_TEXTURE_2D);
				
				glColor3f(1, 1, 1);
		
				renderInner();
				
				mouseClicked = false;
				rendered = true;
			} else {
				rendered = false;
			}
		}
		if (rendered) glfwSwapBuffers(handle);
		return rendered;
	}
	
	public void close() {
		if (!run || handle == 0) return;
		run = false;
		glfwHideWindow(handle);
	}
	
	protected abstract void renderInner();

}
