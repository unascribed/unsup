package com.unascribed.sup.opengl.window;

import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.AlertMessageType;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.Translate;
import com.unascribed.sup.opengl.pieces.GLThrobber;
import com.unascribed.sup.opengl.pieces.FontManager.Face;

import static com.unascribed.sup.opengl.util.GL.*;
import static org.lwjgl.glfw.GLFW.*;

import java.util.concurrent.TimeUnit;

public class MainWindow extends Window {

	private static final long OFFER_DELAY = TimeUnit.SECONDS.toNanos(10);
	private static final float OFFER_DELAYf = OFFER_DELAY;
	
	private static final long TRANS_DELAY = TimeUnit.MILLISECONDS.toNanos(500);
	private static final float TRANS_DELAYf = TRANS_DELAY;
	
	public GLThrobber throbber = new GLThrobber();
	
	public String title = Translate.format("title.default");
	public String subtitle = "";
	public float prog = 0.5f;
	
	public boolean closeRequested = false;
	public String offerChangeFlavorsName;
	public long offerChangeFlavors;
	
	private long throbberTransition;
	
	private boolean enterPressed;
	
	@Override
	public void create(Window parent, String title, int width, int height, double dpiScale) {
		glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
		super.create(parent, title, width, height, dpiScale);

		glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
			if (action == GLFW_RELEASE) return;
			if (key == GLFW_KEY_ENTER || key == GLFW_KEY_SPACE || key == GLFW_KEY_KP_ENTER) {
				synchronized (this) {
					enterPressed = true;
				}
			}
		});
		
		glfwSetWindowCloseCallback(handle, unused -> {
			if (offerChangeFlavors != 0) {
				offerChangeFlavors = System.nanoTime()-OFFER_DELAY;
			} else if (closeRequested) {
				MessageDialogWindow diag = new MessageDialogWindow("puppet_busy_notice", "dialog.busy.title",
						Translate.format("dialog.busy"), AlertMessageType.WARN, new String[] {"option.ok"}, "option.ok");
				Puppet.runOnMainThread(() -> {
					if (!run) return;
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
	protected synchronized void onMouseMove(double x, double y) {}
	
	@Override
	protected synchronized void onMouseClick() {}
	
	@Override
	protected synchronized void renderInner() {
		boolean nfr = needsFullRedraw;
		if (nfr) {
			glClear(GL_COLOR_BUFFER_BIT);
		}
		throbber.render(40, 32, 40);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		if (throbberTransition != 0) {
			float x = (System.nanoTime()-throbberTransition)/TRANS_DELAYf;
			if (x > 1) {
				throbberTransition = 0;
			} else {
				if (x <= 0.5f) {
					x *= 2;
				} else if (x > 0.5f) {
					x = 1-((x-0.5f)*2);
					if (throbber.isDone()) {
						throbber = new GLThrobber();
						Puppet.exitOnDone = true;
					}
				}
				// https://easings.net/#easeInOutCubic
				x = (float)(x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
				glColor(ColorChoice.BACKGROUND, x);
				drawCircle(32, 40, 46);
			}
		}
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
		
		float prog = this.prog;
		if (offerChangeFlavors != 0) {
			prog = (System.nanoTime()-offerChangeFlavors)/OFFER_DELAYf;
			
			if (prog > 1) {
				Puppet.reportChoice(offerChangeFlavorsName, "option.no");
				offerChangeFlavors = 0;
				subtitle = "";
				this.prog = -1;
				needsFullRedraw = true;
			}
			

			
			String text = Translate.format("option.change_flavors");
			float textW = font.measureString(Face.REGULAR, 14, text);
			float btnW = textW+24;
			float btnX = width-(btnW+8);
			float btnY = 8;
			float btnH = 27;
			boolean hover = mouseX >= btnX && mouseX <= btnX+btnW &&
					mouseY >= btnY && mouseY <= btnY+btnH;
			glColor(ColorChoice.BUTTON);
			drawRectWH(btnX, btnY, btnW, btnH);
			
			glColor(ColorChoice.BUTTONTEXT, 0.5f);
			drawRectWHII(btnX, btnY+20,
					btnW, 2,
					6, 0);
			
			if (hover) {
				glColor(ColorChoice.BUTTONTEXT, 0.25f);
				drawRectWH(btnX, btnY, btnW, btnH);
			}
			glColor(ColorChoice.BUTTONTEXT);
			font.drawString(Face.REGULAR, btnX+(btnW-textW)/2, btnY+18, 14, text);
			
			
			if (enterPressed || (mouseClicked && hover)) {
				Puppet.reportChoice(offerChangeFlavorsName, "option.yes");
				offerChangeFlavors = 0;
				throbberTransition = System.nanoTime();
			}
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
		
		enterPressed = false;
	}

}
