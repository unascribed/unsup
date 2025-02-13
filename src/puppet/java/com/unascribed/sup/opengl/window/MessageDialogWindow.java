package com.unascribed.sup.opengl.window;


import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.MessageType;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.opengl.GLPuppet;
import com.unascribed.sup.opengl.pieces.FontManager.Face;

import static com.unascribed.sup.opengl.util.GL.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

public class MessageDialogWindow extends Window {

	private final String name;
	private final String title;
	private final String[] bodyLines;
	private final MessageType messageType;
	private final String[] options;
	private final String[] optionsRaw;
	
	private final float[] bodyMeasurements;
	private final float[] optionMeasurements;
	private float totalOptionWidth;
	
	public int highlighted = 0;
	private int hovered = -1;
	
	private boolean needsRedraw = true;
	private boolean needsFullRedraw = true;
	
	private double mouseX, mouseY;
	private boolean mouseClicked = false;
	private boolean enterPressed = false;
	
	private boolean clickCursorActive = false;
	
	private long clickCursor;
	
	public MessageDialogWindow(String name, String title, String body, MessageType messageType, String[] options) {
		this.name = name;
		this.title = Puppet.format(title);
		this.bodyLines = Puppet.format(body).split("\n");
		this.messageType = messageType;
		this.optionsRaw = options;
		this.options = Puppet.format(options);
		
		this.bodyMeasurements = new float[bodyLines.length];
		this.optionMeasurements = new float[options.length];
	}
	
	@Override
	public void create(String title, int width, int height, float dpiScale) {
		glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);
		super.create(title, width, height, dpiScale);
		
		clickCursor = glfwCreateStandardCursor(GLFW_POINTING_HAND_CURSOR);
		
		glfwSetWindowRefreshCallback(handle, window -> {
			needsRedraw = true;
			needsFullRedraw = true;
		});
		glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
			if (action == GLFW_RELEASE) return;
			if (key == GLFW_KEY_TAB) {
				int dir = 1;
				if ((mods & GLFW_MOD_SHIFT) != 0) {
					dir = -1;
				}
				int h = highlighted + dir;
				if (h < 0) h = options.length+h;
				h %= options.length;
				highlighted = h;
				needsRedraw = true;
			} else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_SPACE || key == GLFW_KEY_KP_ENTER) {
				enterPressed = true;
				needsRedraw = true;
			}
		});
		glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
			mouseX = xpos/dpiScale;
			mouseY = ypos/dpiScale;
			needsRedraw = true;
		});
		glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
			if (action == GLFW_RELEASE) return;
			if (button == GLFW_MOUSE_BUTTON_LEFT) {
				mouseClicked = true;
				needsRedraw = true;
			}
		});
		glfwSetWindowCloseCallback(handle, unused -> {
			Puppet.reportChoice(name, "closed");
			close();
		});
	}
	
	public void create(float dpiScale) {
		float measSum = 0;
		for (int i = 0; i < options.length; i++) {
			float meas = optionMeasurements[i] = font.measureString(Face.REGULAR, 12, options[i]);
			measSum += Math.max(meas+14, 64);
		}
		totalOptionWidth = (6*(options.length-1))+measSum;
		float maxBodyWidth = 0;
		float height = (bodyLines.length*16)+10+27+12;
		for (int i = 0; i < bodyLines.length; i++) {
			String line = bodyLines[i];
			Face f = i == 0 ? Face.BOLD : Face.REGULAR;
			maxBodyWidth = Math.max(maxBodyWidth, bodyMeasurements[i] = font.measureString(f, 12, line));
		}
		float width = Math.max(totalOptionWidth, maxBodyWidth);
		create(title, (int)(width+32), (int)(height), dpiScale);
	}

	@Override
	protected void setupGL() {
		
	}
	
	@Override
	protected boolean needsRerender() {
		return needsRedraw || needsFullRedraw;
	}

	@Override
	protected void renderInner() {
		if (needsFullRedraw) {
			glClear(GL_COLOR_BUFFER_BIT);
			glColorPacked3i(GLPuppet.getColor(ColorChoice.DIALOG));
		}
		
		float y = 20;
		for (int i = 0; i < bodyLines.length; i++) {
			String line = bodyLines[i];
			Face f = i == 0 ? Face.BOLD : Face.REGULAR;
			float w = bodyMeasurements[i];
			if (needsFullRedraw) {
				glPushMatrix();
					font.drawString(f, (int)((width-w)/2), y, 12, line);
				glPopMatrix();
			}
			y += 16;
		}
		

		y -= 4;
		
		glDisable(GL_TEXTURE_2D);
		glBegin(GL_QUADS);
		int tmpHovered = -1;
		float x = (width-totalOptionWidth)/2;
		for (int i = 0; i < options.length; i++) {
			float w = optionMeasurements[i]+14;
			if (w < 64) w = 64;
			
			float x2 = x+w;
			float y2 = y+27;
			
			if (mouseX >= x && mouseX <= x2 &&
					mouseY >= y && mouseY <= y2) {
				tmpHovered = i;
				Puppet.runOnMainThread(() -> glfwSetCursor(handle, clickCursor));
				clickCursorActive = true;
				
				if (mouseClicked) {
					Puppet.reportChoice(name, optionsRaw[i]);
					close();
				}
			}
			
			for (int j = 0; j < (i == tmpHovered ? 2 : 1); j++) {
				if (j == 0) {
					glColorPacked3i(GLPuppet.getColor(ColorChoice.BUTTON));
				} else {
					glColor4f(1, 1, 1, 0.25f);
				}
				glVertex2f(x, y);
				glVertex2f(x2, y);
				glVertex2f(x2, y2);
				glVertex2f(x, y2);
			}
			
			if (i == highlighted) {
				glColor4f(1, 1, 1, 0.5f);
				glVertex2f(x+6, y+20);
				glVertex2f(x+w-6, y+20);
				glVertex2f(x+w-6, y+22);
				glVertex2f(x+6, y+22);
				
				if (enterPressed) {
					Puppet.reportChoice(name, optionsRaw[i]);
					close();
				}
			}
			
			x += w+6;
		}
		hovered = tmpHovered;
		if (tmpHovered == -1 && clickCursorActive) {
			Puppet.runOnMainThread(() -> glfwSetCursor(handle, NULL));
		}
		glEnd();
		x = (width-totalOptionWidth)/2;
		glColorPacked3i(GLPuppet.getColor(ColorChoice.BUTTONTEXT));
		for (int i = 0; i < options.length; i++) {
			float w = optionMeasurements[i];
			float ofs = 7;
			if ((w+14) < 64) {
				ofs = (64-w)/2;
				w = 64;
			} else {
				w += 14;
			}
			glPushMatrix();
				font.drawString(Face.REGULAR, (int)(x+ofs), y+17, 12, options[i]);
			glPopMatrix();
			x += w+6;
		}
		
		needsRedraw = false;
		needsFullRedraw = false;
		if (mouseClicked) mouseClicked = false;
		if (enterPressed) enterPressed = false;
	}

}
