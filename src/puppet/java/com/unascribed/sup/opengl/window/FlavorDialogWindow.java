package com.unascribed.sup.opengl.window;


import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.Translate;
import com.unascribed.sup.data.FlavorGroup;

import static org.lwjgl.glfw.GLFW.*;
import static com.unascribed.sup.opengl.util.GL.*;

import java.util.List;

public class FlavorDialogWindow extends Window {

	private final String name;
	private final List<FlavorGroup> flavors;
	
	private int hovered = -1;
	private int highlighted = 0;
	
	private boolean needsLeftRedraw = true;
	private boolean needsRightRedraw = true;
	
	private double mouseX, mouseY;
	private boolean mouseClicked = false;
	private boolean enterPressed = false;
	private boolean leftPressed = false;
	private boolean rightPressed = false;
	
	private boolean clickCursorActive = false;
	private float scroll = 0;
	private float scrollVel = 0;
	
	private long clickCursor;
	
	public FlavorDialogWindow(String name, List<FlavorGroup> flavors) {
		this.name = name;
		this.flavors = flavors;
	}
	
	@Override
	public void create(Window parent, String title, int width, int height, double dpiScale) {
		glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		super.create(parent, title, width, height, dpiScale);
		
		clickCursor = glfwCreateStandardCursor(GLFW_POINTING_HAND_CURSOR);
		
		glfwSetWindowRefreshCallback(handle, window -> {
			synchronized (this) {
				needsLeftRedraw = true;
				needsRightRedraw = true;
			}
		});
		glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
			if (action == GLFW_RELEASE) return;
			if (key == GLFW_KEY_TAB || key == GLFW_KEY_UP || key == GLFW_KEY_DOWN) {
				int dir = 1;
				if (key == GLFW_KEY_UP || (key == GLFW_KEY_TAB && (mods & GLFW_MOD_SHIFT) != 0)) {
					dir = -1;
				}
				synchronized (this) {
					int h = highlighted + dir;
					if (h < 0) h = flavors.size()+h;
					h %= flavors.size();
					highlighted = h;
					needsLeftRedraw = true;
					needsRightRedraw = true;
				}
			} else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_SPACE || key == GLFW_KEY_KP_ENTER) {
				synchronized (this) {
					enterPressed = true;
					needsLeftRedraw = true;
				}
			} else if (key == GLFW_KEY_LEFT) {
				synchronized (this) {
					leftPressed = true;
					needsLeftRedraw = true;
				}
			} else if (key == GLFW_KEY_RIGHT) {
				synchronized (this) {
					rightPressed = true;
					needsLeftRedraw = true;
				}
			}
		});
		glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
			synchronized (this) {
				mouseX = xpos/dpiScale;
				mouseY = ypos/dpiScale;
			}
		});
		glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
			if (action == GLFW_RELEASE) return;
			if (button == GLFW_MOUSE_BUTTON_LEFT) {
				synchronized (this) {
					mouseClicked = true;
					needsLeftRedraw = true;
				}
			}
		});
		glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
			synchronized (this) {
				scrollVel += yoffset;
			}
		});
		glfwSetWindowCloseCallback(handle, unused -> {
			Puppet.reportCloseRequest();
			close();
		});
		glfwSetWindowSizeCallback(handle, (window, newWidth, newHeight) -> {
			System.out.println("win "+newWidth+"x"+newHeight);
			synchronized (this) {
				this.width = (int) (newWidth/dpiScale);
				this.height = (int) (newHeight/dpiScale);
				needsLeftRedraw = true;
				needsRightRedraw = true;
			}
		});
		glfwSetWindowSizeLimits(handle, (int)(400*dpiScale), (int)(200*dpiScale), GLFW_DONT_CARE, GLFW_DONT_CARE);
	}
	
	public void create(Window parent, double dpiScale) {
		create(parent, Translate.format("dialog.flavors.title"), 600, 400, dpiScale);
	}

	@Override
	protected void setupGL() {
		
	}
	
	@Override
	protected synchronized boolean needsRerender() {
		return true;//needsLeftRedraw || needsRightRedraw;
	}

	@Override
	protected synchronized void renderInner() {
		boolean nlr = needsLeftRedraw;
		boolean nrr = needsRightRedraw;
		glClear(GL_COLOR_BUFFER_BIT);
		
		glColor(ColorChoice.DIALOG);
		drawCircle(20, 20, 24);
		glColor(ColorChoice.BACKGROUND);
		drawCircle(20, 20, 16);
		
		
		glColor(ColorChoice.BUTTON);
		drawCircle(20, 80, 24);
		glColor(ColorChoice.BUTTONTEXT);
		glTranslatef(20, 80, 0);
		glBegin(GL_QUAD_STRIP);
			glVertex2f(-8.422f, 1.406f);
			glVertex2f(-5.592f, -1.422f);
			glVertex2f(-1.918f, 7.895f);
			glVertex2f(-2.084f, 2.086f);
			
			glVertex2f(8.482f, -3.674f);
			glVertex2f(5.508f, -6.350f);
		glEnd();
		
		needsLeftRedraw = false;
		needsRightRedraw = false;
	}

}
