package com.unascribed.sup.opengl.window;

import static com.unascribed.sup.opengl.util.GL.glColorPacked3i;
import static org.lwjgl.glfw.GLFW.glfwSetWindowRefreshCallback;

import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.opengl.GLPuppet;
import com.unascribed.sup.opengl.pieces.GLThrobber;
import com.unascribed.sup.opengl.pieces.FontManager.Face;

import static org.lwjgl.opengl.GL11.*;

public class MainWindow extends Window {

	public GLThrobber throbber = new GLThrobber();
	
	public String title = "Reticulating splines...";
	public String subtitle = "";
	public float prog = 0.5f;
	
	public boolean needsFullRedraw = true;
	
	@Override
	public void create(String title, int width, int height, float dpiScale) {
		super.create(title, width, height, dpiScale);

		glfwSetWindowRefreshCallback(handle, window -> {
			needsFullRedraw = true;
		});
	}
	
	@Override
	protected void setupGL() {
		Puppet.log("DEBUG", "OpenGL Version: "+glGetString(GL_VERSION));
		Puppet.log("DEBUG", "OpenGL Renderer: "+glGetString(GL_RENDERER));
		Puppet.log("DEBUG", "OpenGL Vendor: "+glGetString(GL_VENDOR));
	}
	
	@Override
	protected void renderInner() {
		if (needsFullRedraw) {
			glClear(GL_COLOR_BUFFER_BIT);
		}
		throbber.render(40, 32, 40);
		if (needsFullRedraw) {
			glPushMatrix();
				glColorPacked3i(GLPuppet.getColor(ColorChoice.TITLE));
				font.drawString(Face.BOLD, 64, 31, 24, title);
			glPopMatrix();
			glPushMatrix();
				glColorPacked3i(GLPuppet.getColor(ColorChoice.SUBTITLE));
				font.drawString(Face.REGULAR, 64, 52, 14, subtitle);
			glPopMatrix();
			needsFullRedraw = false;
		}
		
		if (prog >= 0) {
			glDisable(GL_TEXTURE_2D);
			glBegin(GL_QUADS);
				glColorPacked3i(GLPuppet.getColor(ColorChoice.PROGRESSTRACK));
				glVertex2f(64, 70);
				glVertex2f(476, 70);
				glVertex2f(476, 76);
				glVertex2f(64, 76);
				
				glColorPacked3i(GLPuppet.getColor(ColorChoice.PROGRESS));
				glVertex2f(65, 71);
				glVertex2f(65+(prog*412), 71);
				glVertex2f(65+(prog*412), 75);
				glVertex2f(65, 75);
			glEnd();
		}
	}

}
