package com.unascribed.sup;

import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class GLPuppet {

	public static void main(String[] args) throws InterruptedException {
		start();
	}
	
	public static PuppetDelegate start() throws InterruptedException {
		if (!glfwInit()) return null;
		
		glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_FALSE);
		glfwWindowHint(GLFW_DECORATED, GL_TRUE);
		glfwWindowHint(GLFW_VISIBLE, GL_TRUE);
		long window = glfwCreateWindow(512, 128, "unsup v"+Util.VERSION, NULL, NULL);
		if (window == 0) {
			glfwTerminate();
			return null;
		}
		glfwMakeContextCurrent(window);
		
		if (glfwExtensionSupported("GLX_EXT_swap_control_tear") || glfwExtensionSupported("WGL_EXT_swap_control_tear")) {
			glfwSwapInterval(-1);
		} else {
			glfwSwapInterval(1);
		}
		
		GL.createCapabilities();
		while (window > 0) {
			glClearColor(0, 1, 0, 1);
			glClear(GL_COLOR_BUFFER_BIT);
			
		}
		glfwTerminate();
		return null; // TODO
	}
	
}
