package com.unascribed.sup.opengl.window;

import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.AlertMessageType;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.Translate;
import com.unascribed.sup.opengl.pieces.GLThrobber;
import com.unascribed.sup.opengl.pieces.FontManager.Face;

import static com.unascribed.sup.opengl.util.GL.*;
import static org.lwjgl.glfw.GLFW.*;

public class MainWindow extends Window {

	public GLThrobber throbber = new GLThrobber();
	
	public String title = Translate.format("title.default");
	public String subtitle = "";
	public float prog = 0.5f;
	
	public boolean needsFullRedraw = true;
	public boolean closeRequested = false;
	
	@Override
	public void create(Window parent, String title, int width, int height, double dpiScale) {
		glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
		super.create(parent, title, width, height, dpiScale);

		glfwSetWindowRefreshCallback(handle, window -> {
			synchronized (this) {
				needsFullRedraw = true;
			}
		});
		
		glfwSetWindowCloseCallback(handle, unused -> {
			if (closeRequested) {
				MessageDialogWindow diag = new MessageDialogWindow("puppet_busy_notice", "dialog.busy.title",
						Translate.format("dialog.busy"), AlertMessageType.WARN, new String[] {"option.ok"}, "option.ok");
				Puppet.runOnMainThread(() -> {
					diag.create(this, dpiScale);
					diag.setVisible(true);
				});
			} else {
				Puppet.reportCloseRequest();
				closeRequested = true;
			}
		});
	}
	
	@Override
	protected void setupGL() {
		Puppet.log("DEBUG", "OpenGL Version: "+glGetString(GL_VERSION));
		Puppet.log("DEBUG", "OpenGL Renderer: "+glGetString(GL_RENDERER));
		Puppet.log("DEBUG", "OpenGL Vendor: "+glGetString(GL_VENDOR));
	}
	
	@Override
	protected synchronized void renderInner() {
		boolean nfr = needsFullRedraw;
		if (nfr) {
			glClear(GL_COLOR_BUFFER_BIT);
		}
		throbber.render(40, 32, 40);
		if (nfr) {
			glPushMatrix();
				glColor(ColorChoice.TITLE);
				font.drawString(Face.BOLD, 64, 31, 24, title);
			glPopMatrix();
			glPushMatrix();
				glColor(ColorChoice.SUBTITLE);
				font.drawString(Face.REGULAR, 64, 52, 14, subtitle);
			glPopMatrix();
			needsFullRedraw = false;
		}
		
		if (prog >= 0) {
			glDisable(GL_TEXTURE_2D);
			glBegin(GL_QUADS);
				glColor(ColorChoice.PROGRESSTRACK);
				glVertex2f(64, 70);
				glVertex2f(476, 70);
				glVertex2f(476, 76);
				glVertex2f(64, 76);
				
				glColor(ColorChoice.PROGRESS);
				glVertex2f(65, 71);
				glVertex2f(65+(prog*412), 71);
				glVertex2f(65+(prog*412), 75);
				glVertex2f(65, 75);
			glEnd();
		}
	}

}
