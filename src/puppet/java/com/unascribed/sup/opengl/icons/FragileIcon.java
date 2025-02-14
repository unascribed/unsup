package com.unascribed.sup.opengl.icons;

import static com.unascribed.sup.opengl.util.GL.*;

// https://pictogrammers.com/library/mdi/icon/glass-fragile/
class FragileIcon {

	public static void draw(int bg, int fg) {
		glColorPacked3i(fg);
		glPushMatrix();
			glTranslatef(0, -3, 0);
			glScalef(12, 10, 1);
			drawCircle(0, 0, 1);
		glPopMatrix();
		glBegin(GL_QUADS);
			glVertex2f(-5, -10);
			glVertex2f(5, -10);
			glVertex2f(5.931f, -3.755f);
			glVertex2f(-5.931f, -3.755f);
			
			glVertex2f(-1, 1);
			glVertex2f(1, 1);
			glVertex2f(1, 8);
			glVertex2f(-1, 8);
			
			glVertex2f(-6, 8);
			glVertex2f(6, 8);
			glVertex2f(6, 10);
			glVertex2f(-6, 10);
			
			glColorPacked3i(bg);
			glVertex2f(1.540f, -10);
			glVertex2f(3.210f, -10);
			glVertex2f(1.790f, -6.5f);
			glVertex2f(-0.210f, -6.5f);
			
			glVertex2f(2.210f, -8);
			glVertex2f(4.210f, -8);
			glVertex2f(2.101f, -2.5f);
			glVertex2f(-0.210f, -2.5f);
		glEnd();
		glBegin(GL_TRIANGLES);
			glVertex2f(2.451f, -4);
			glVertex2f(4.460f, -4);
			glVertex2f(1, 0.75f);
		glEnd();
	}
	
}
