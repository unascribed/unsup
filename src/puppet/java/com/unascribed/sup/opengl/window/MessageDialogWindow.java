package com.unascribed.sup.opengl.window;


import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.AlertMessageType;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.Translate;
import com.unascribed.sup.opengl.icons.Icon;
import com.unascribed.sup.opengl.pieces.FontManager.Face;

import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glEnd;
import static com.unascribed.sup.opengl.util.GL.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageDialogWindow extends Window {
	
	private static final boolean REVISED_CONFLICT_UI = true;

	private static final String[] toAllDialog = {"option.yes_to_all", "option.yes", "option.no_to_all", "option.no", "option.cancel"};
	private static final String[] conflictOptions = {"option.yes", "option.no", null, "option.cancel"};
	
	private final String name;
	private final String title;
	private final String[] bodyLines;
	private final AlertMessageType messageType;
	private final String[] options;
	private final String[] optionsRaw;
	
	private final float[] bodyMeasurements;
	private final float[] optionMeasurements;
	private float totalOptionWidth;
	
	public int highlighted = 0;
	
	private boolean needsRedraw = true;
	
	private boolean enterPressed = false;
	
	private boolean clickCursorActive = false;
	private boolean toAll = false;
	private boolean conflictDialog = false;
	
	private static final Pattern LEADIN_PATTERN = Pattern.compile("dialog.conflict.leadin.([^¤.]+)");
	
	public MessageDialogWindow(String name, String title, String body, AlertMessageType messageType, String[] options, String def) {
		if (REVISED_CONFLICT_UI && Arrays.equals(toAllDialog, options)) {
			options = new String[] {"option.yes", "option.no", "option.to_all", "option.cancel"};
		}
		this.name = name;
		this.title = Translate.format(title);
		this.messageType = messageType;
		Matcher m = LEADIN_PATTERN.matcher(body);
		if (REVISED_CONFLICT_UI && "dialog.conflict.title".equals(title) && m.find()) {
			conflictDialog = true;
			String type = m.group(1);
			body = body.replace("dialog.conflict.body", "dialog.conflict.revised.body").replace("dialog.conflict.aside_trailer", "dialog.conflict.revised.aside_trailer");
			String pfx = "dialog.conflict.revised."+type+".";
			this.options = Translate.format(new String[] {pfx+"option.yes", pfx+"option.no", "option.to_all", "option.abort"});
			highlighted = 0;
		} else {
			this.options = Translate.format(options);
			for (int i = 0; i < options.length; i++) {
				if (options[i].equals(def)) {
					highlighted = i;
					break;
				}
			}
		}
		this.optionsRaw = options;
		this.bodyLines = Translate.format(body).split("\n");
		
		this.bodyMeasurements = new float[bodyLines.length];
		this.optionMeasurements = new float[options.length];
	}
	
	@Override
	public void create(Window parent, String title, int width, int height, double dpiScale) {
		glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
		super.create(parent, title, width, height, dpiScale);
		
		glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
			if (action == GLFW_RELEASE) return;
			if (key == GLFW_KEY_TAB) {
				int dir = 1;
				if ((mods & GLFW_MOD_SHIFT) != 0) {
					dir = -1;
				}
				synchronized (this) {
					int h = highlighted + dir;
					if (h < 0) h = options.length+h;
					h %= options.length;
					highlighted = h;
					needsRedraw = true;
				}
			} else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_SPACE || key == GLFW_KEY_KP_ENTER) {
				synchronized (this) {
					enterPressed = true;
					needsRedraw = true;
				}
			}
		});
		glfwSetWindowFocusCallback(handle, (window, focus) -> {
			synchronized (this) {
				needsRedraw = true;
			}
		});
		glfwSetWindowCloseCallback(handle, unused -> {
			Puppet.reportChoice(name, "closed");
			close();
		});
	}
	
	public void create(Window parent, double dpiScale) {
		float measSum = 0;
		for (int i = 0; i < options.length; i++) {
			float meas = optionMeasurements[i] = font.measureString(Face.REGULAR, 12, options[i]);
			if (optionsRaw[i].equals("option.to_all")) {
				measSum += meas+48;
			} else {
				measSum += Math.max(meas+14, 64);
			}
		}
		totalOptionWidth = (6*(options.length-1))+measSum;
		float maxBodyWidth = 0;
		float height = (bodyLines.length*16)+10+27+12;
		for (int i = 0; i < bodyLines.length; i++) {
			String line = bodyLines[i];
			Face f = i == 0 ? Face.BOLD : Face.REGULAR;
			maxBodyWidth = Math.max(maxBodyWidth, bodyMeasurements[i] = font.measureString(f, 12, line));
		}
		if (messageType != AlertMessageType.NONE) {
			maxBodyWidth += 64;
			height = Math.max(height, 96);
		}
		float width = Math.max(totalOptionWidth, maxBodyWidth);
		create(parent, title, (int)(width+32), (int)(height), dpiScale);
	}

	@Override
	protected void setupGL() {}
	
	@Override
	protected synchronized void onMouseMove(double x, double y) {
		needsRedraw = true;
	}
	
	@Override
	protected synchronized void onMouseClick() {
		needsRedraw = true;
	}
	
	@Override
	protected synchronized boolean needsRerender() {
		needsFullRedraw = true;
		return needsRedraw || needsFullRedraw;
	}

	@Override
	protected synchronized void renderInner() {
		boolean nfr = needsFullRedraw;
		if (nfr) {
			glClear(GL_COLOR_BUFFER_BIT);
			
			if (messageType != AlertMessageType.NONE) {
				glPushMatrix();
					glTranslatef(32, Math.max(32, bodyLines.length*8), 0);
					glScalef(1.75f, 1.75f, 1);
					Icon i = Icon.ERROR;
					ColorChoice color = ColorChoice.PROGRESS;
					if (conflictDialog) {
						i = Icon.FRAGILE;
						color = ColorChoice.SUBTITLE;
					} else {
						switch (messageType) {
							case QUESTION:
								i = Icon.QUESTION;
								color = ColorChoice.QUESTION;
								break;
							case INFO:
								i = Icon.INFO;
								color = ColorChoice.INFO;
								break;
							case WARN:
								i = Icon.ALERT;
								color = ColorChoice.WARNING;
								break;
							case ERROR:
								i = Icon.ERROR;
								color = ColorChoice.ERROR;
								break;
							case NONE:
								throw new AssertionError();
						}
					}
					i.draw(ColorChoice.BACKGROUND, color);
				glPopMatrix();
			}
			
			glColor(ColorChoice.DIALOG);
		}
		
		int bodyAreaOffset = 0;
		int bodyAreaWidth = width;
		if (messageType != AlertMessageType.NONE) {
			bodyAreaWidth -= 64;
			bodyAreaOffset = 64;
		}
		
		float y = 20;
		for (int i = 0; i < bodyLines.length; i++) {
			String line = bodyLines[i];
			Face f = i == 0 ? Face.BOLD : Face.REGULAR;
			float w = bodyMeasurements[i];
			if (nfr) {
				glPushMatrix();
					font.drawString(f, messageType != AlertMessageType.NONE ? bodyAreaOffset : (int)((bodyAreaWidth-w)/2)+bodyAreaOffset, y, 12, line);
				glPopMatrix();
			}
			y += 16;
		}
		

		y = height-33;
		
		glDisable(GL_TEXTURE_2D);
		glBegin(GL_QUADS);
		int hovered = -1;
		float baseX = (width-totalOptionWidth)/2;
		float x = baseX;
		boolean toAll = this.toAll;
		for (int i = 0; i < options.length; i++) {
			float w = optionMeasurements[i]+14;
			if (w < 64) w = 64;
			
			float x1 = x;
			float y1 = y;
			float x2 = x+w;
			float y2 = y+27;
			
			float x2Mouse = x2;
			
			int highlightInset = 6;
			
			boolean isToAll = optionsRaw[i].equals("option.to_all");
			
			if (isToAll) {
				x2 = x2Mouse = x+optionMeasurements[i]+32;
				glColor(ColorChoice.BACKGROUND);
				buildRectXY(x1, y1, x2, y2);
				
				w = optionMeasurements[i]+48;
				
				y1 = y+2;
				x2 = x1+20;
				y2 = y1+20;
			}
			
			if (mouseX >= x1 && mouseX <= x2Mouse &&
					mouseY >= y1 && mouseY <= y2) {
				hovered = i;
				Puppet.runOnMainThread(() -> {
					if (!run) return;
					glfwSetCursor(handle, clickCursor);
				});
				clickCursorActive = true;
				
				if (mouseClicked) {
					if (isToAll) {
						toAll = !toAll;
					} else {
						Puppet.reportChoice(name, getReport(i));
						close();
					}
				}
			}
			
			if (i == highlighted && isToAll && enterPressed) {
				toAll = !toAll;
			}
			
			for (int j = 0; j < ((isToAll && !toAll) || i == hovered ? 2 : 1); j++) {
				int inset = 0;
				if (j == 0) {
					glColor(isToAll && !toAll ? ColorChoice.BUTTONTEXT : ColorChoice.BUTTON);
				} else {
					if (isToAll && !toAll) {
						inset = 2;
						if (i == hovered) {
							glColor(ColorChoice.BACKGROUND, 0.75f);
						} else {
							glColor(ColorChoice.BACKGROUND);
						}
					} else {
						glColor(isToAll ? ColorChoice.DIALOG : ColorChoice.BUTTONTEXT, 0.25f);
					}
				}
				buildRectXYI(x1, y1, x2, y2, inset);
			}
			
			if (i == highlighted) {
				if (glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE) {
					glColor(isToAll ? ColorChoice.DIALOG : ColorChoice.BUTTONTEXT, 0.5f);
					buildRectXYII(x1, y1+20,
							x2Mouse, y1+22,
							highlightInset, 0);
				}
				
				if (enterPressed && !isToAll) {
					Puppet.reportChoice(name, getReport(i));
					close();
				}
			}
			
			if (isToAll && toAll) {
				glColor(ColorChoice.BUTTONTEXT);
				float cx = x1+((x2-x1)/2);
				float cy = y1+((y2-y1)/2);
				
				buildCheckmark(cx, cy);
			}
			
			x += w+6;
		}
		this.toAll = toAll;
		if (hovered == -1 && clickCursorActive) {
			clickCursorActive = false;
			Puppet.runOnMainThread(() -> {
				if (!run) return;
				glfwSetCursor(handle, NULL);
			});
		}
		glEnd();
		x = baseX;
		glColor(ColorChoice.BUTTONTEXT);
		for (int i = 0; i < options.length; i++) {
			float w = optionMeasurements[i];
			float ofs = 7;
			if (optionsRaw[i].equals("option.to_all")) {
				w += 48;
				ofs = 24;
			} else if ((w+14) < 64) {
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
		enterPressed = false;
	}

	private String getReport(int i) {
		String opt;
		if (conflictDialog) {
			opt = conflictOptions[i];
		} else {
			opt = optionsRaw[i];
		}
		if (!toAll) return opt;
		if ("option.yes".equals(opt)) return "option.yes_to_all";
		if ("option.no".equals(opt)) return "option.no_to_all";
		return opt;
	}

}
