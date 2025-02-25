package com.unascribed.sup.puppet.opengl.pieces;

import static com.unascribed.sup.puppet.opengl.util.GL.*;

import com.unascribed.sup.puppet.ColorChoice;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.ThrobberAnim;

public class GLThrobber {

	private final ThrobberAnim anim = new ThrobberAnim();
	
	public boolean isDone() {
		return anim.animateDone;
	}
	
	public void render(int dia, float x, float y) {
		if (anim.update()) {
			glColor(ColorChoice.PROGRESS);
			drawCircle(x, y, dia);
			long time = 4000-anim.animateDoneTime;
			glColor(ColorChoice.BACKGROUND);
			if (time > 0) {
				double a = 1-Math.sin((time/4000D)*(Math.PI/2));
				int inset = (int)(8+((dia-8)*a));
				drawCircle(x, y, dia-inset);
			} else if (time < -1000) {
				Puppet.reportDone();
			}
			glPushMatrix();
				glTranslatef(x, y, 0);
				glScalef(40f/dia, 40f/dia, 1);
				glTranslatef(-20, -20, 0);
				glBegin(GL_QUADS);
					glVertex2f(8.805f, 19.160f);
					glVertex2f(19.816f, 28.781f);
					glVertex2f(17.184f, 31.795f);
					glVertex2f(6.172f, 22.172f);

					glVertex2f(16.616f, 26.366f);
					glVertex2f(29.796f, 11.197f);
					glVertex2f(32.816f, 13.821f);
					glVertex2f(19.636f, 28.990f);
				glEnd();
			glPopMatrix();
		} else {
			float from = anim.progress;
			float length = 360-(anim.barMinLength + anim.barExtraLength);
			
			glColor(ColorChoice.PROGRESS);
			drawCircle(x, y, dia);
			glColor(ColorChoice.BACKGROUND);
			drawCircle(x, y, dia-8);
			glPushMatrix();
				glTranslatef(x, y, 0);
				glScalef(-1, 1, 1);
				glRotatef(from, 0, 0, 1);
				drawCircleArc(0, 0, dia+2, 0, Math.toRadians(length));
			glPopMatrix();
		}
	}
	
	public void animateDone() {
		anim.animateDone();
	}
	
}
