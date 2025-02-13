package com.unascribed.sup.opengl.window;

import static org.lwjgl.glfw.GLFW.*;

import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.MessageType;
import com.unascribed.sup.opengl.GLPuppet;
import com.unascribed.sup.opengl.pieces.FontManager.Face;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

import static com.unascribed.sup.opengl.util.GL.*;

public class MessageDialogWindow extends Window {

	private final String name;
	private final String title;
	private final String[] bodyLines;
	private final MessageType messageType;
	private final String[] options;
	
	private final float[] bodyMeasurements;
	private final float[] optionMeasurements;
	private float totalOptionWidth;
	
	public int highlighted = 0;
	private int hovered = -1;
	
	private boolean needsRedraw = true;
	
	private double mouseX, mouseY;
	
	public MessageDialogWindow(String name, String title, String body, MessageType messageType, String[] options) {
		this.name = name;
		this.title = title;
		this.bodyLines = body.split("\n");
		this.messageType = messageType;
		this.options = options;
		
		this.bodyMeasurements = new float[bodyLines.length];
		this.optionMeasurements = new float[options.length];
	}
	
	@Override
	public void create(String title, int width, int height, float dpiScale) {
		glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);
		super.create(title, width, height, dpiScale);
		
		glfwSetWindowRefreshCallback(handle, window -> {
			needsRedraw = true;
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
			}
		});
		glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
			mouseX = xpos/dpiScale;
			mouseY = ypos/dpiScale;
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
		return needsRedraw;
	}

	@Override
	protected void renderInner() {
		glClear(GL_COLOR_BUFFER_BIT);
		
		float y = 20;
		glColorPacked3i(GLPuppet.getColor(ColorChoice.DIALOG));
		for (int i = 0; i < bodyLines.length; i++) {
			String line = bodyLines[i];
			Face f = i == 0 ? Face.BOLD : Face.REGULAR;
			float w = bodyMeasurements[i];
			glPushMatrix();
				font.drawString(f, (int)((width-w)/2), y, 12, line);
			glPopMatrix();
			y += 16;
		}

		y -= 4;
		
		glDisable(GL_TEXTURE_2D);
		glBegin(GL_QUADS);
		float x = (width-totalOptionWidth)/2;
		for (int i = 0; i < options.length; i++) {
			float w = optionMeasurements[i]+14;
			if (w < 64) w = 64;
			glColorPacked3i(GLPuppet.getColor(ColorChoice.BUTTON));
			glVertex2f(x, y);
			glVertex2f(x+w, y);
			glVertex2f(x+w, y+27);
			glVertex2f(x, y+27);
			
			if (i == highlighted) {
				glColor4f(1, 1, 1, 0.5f);
				glVertex2f(x+6, y+20);
				glVertex2f(x+w-6, y+20);
				glVertex2f(x+w-6, y+22);
				glVertex2f(x+6, y+22);
			}
			
			x += w+6;
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
	}

}
