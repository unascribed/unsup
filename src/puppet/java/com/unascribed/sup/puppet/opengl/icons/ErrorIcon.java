package com.unascribed.sup.puppet.opengl.icons;

import static com.unascribed.sup.puppet.opengl.util.GL.*;

import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;

// https://pictogrammers.com/library/mdi/icon/close-circle/
class ErrorIcon {

	public static void draw(int bg, int fg) {
		glColorPacked3i(fg);
		drawCircle(0, 0, 24);
		glColorPacked3i(bg);
		drawCircle(0, 0, 20);
		glColorPacked3i(fg);
		glBegin(GL_QUADS);
			glVertex2f(-3.590f, -5.000f);
			glVertex2f(5.000f, 3.590f);
			glVertex2f(3.590f, 5.000f);
			glVertex2f(-5.000f, -3.590f);
			
			glVertex2f(3.590f, -5.000f);
			glVertex2f(-5.000f, 3.590f);
			glVertex2f(-3.590f, 5.000f);
			glVertex2f(5.000f, -3.590f);
		glEnd();
	}
	
}
