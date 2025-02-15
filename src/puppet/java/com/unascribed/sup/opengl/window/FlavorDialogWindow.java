package com.unascribed.sup.opengl.window;


import com.unascribed.sup.ColorChoice;
import com.unascribed.sup.Puppet;
import com.unascribed.sup.Translate;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.FlavorGroup.FlavorChoice;
import com.unascribed.sup.opengl.pieces.FontManager.Face;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static com.unascribed.sup.opengl.util.GL.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class FlavorDialogWindow extends Window {

	private final String name;
	private final List<FlavorGroup> flavors;
	private final Map<String, Document> descriptionDocuments = new HashMap<>();
	private final Set<String> selectedFlavors = new HashSet<>();
	
	private int hovered = -1;
	private int subhovered = -1;
	private int highlighted = 0;
	
	private boolean needsLeftRedraw = true;
	private boolean needsRightRedraw = true;
	
	private boolean enterPressed = false;
	private boolean ctrlEnterPressed = false;
	private boolean leftPressed = false;
	private boolean rightPressed = false;
	private boolean didKeyboardNav = false;
	private boolean preferKeyboard = false;
	
	private boolean clickCursorActive = false;
	private float scroll = 0;
	private float lastScroll = 0;
	private float scrollVel = 0;
	private float maxScroll = 480;
	
	private long lastTick = System.nanoTime();
	
	public FlavorDialogWindow(String name, List<FlavorGroup> flavors) {
		this.name = name;
		this.flavors = new ArrayList<>(flavors);
		this.flavors.sort((a, b) -> Boolean.compare(a.isBoolean(), b.isBoolean()));
		for (FlavorGroup grp : flavors) {
			boolean anyDefault = false;
			for (FlavorChoice choice : grp.choices) {
				if (choice.def) {
					anyDefault = true;
					selectedFlavors.add(choice.id);
					break;
				}
			}
			if (!anyDefault) {
				selectedFlavors.add(grp.choices.get(0).id);
			}
		}
		
		updateDpiScaleByFramebuffer = false;
	}
	
	@Override
	public void create(Window parent, String title, int width, int height, double dpiScale) {
		glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		super.create(parent, title, width, height, dpiScale);
		
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
					needsFullRedraw = true;
					didKeyboardNav = true;
					preferKeyboard = true;
				}
			} else if (key == GLFW_KEY_ENTER && (mods & GLFW_MOD_CONTROL) != 0) {
				synchronized (this) {
					ctrlEnterPressed = true;
					needsRightRedraw = true;
					preferKeyboard = true;
				}
			} else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_SPACE || key == GLFW_KEY_KP_ENTER) {
				synchronized (this) {
					enterPressed = true;
					needsFullRedraw = true;
					preferKeyboard = true;
				}
			} else if (key == GLFW_KEY_LEFT) {
				synchronized (this) {
					leftPressed = true;
					needsFullRedraw = true;
					preferKeyboard = true;
				}
			} else if (key == GLFW_KEY_RIGHT) {
				synchronized (this) {
					rightPressed = true;
					needsFullRedraw = true;
					preferKeyboard = true;
				}
			}
		});
		glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
			synchronized (this) {
				scrollVel -= yoffset*6;
				preferKeyboard = false;
			}
		});
		glfwSetWindowCloseCallback(handle, unused -> {
			Puppet.reportCloseRequest();
			close();
		});
		glfwSetWindowSizeCallback(handle, (window, newWidth, newHeight) -> {
			synchronized (this) {
				this.width = (int) (newWidth/dpiScaleX);
				this.height = (int) (newHeight/dpiScaleY);
				needsLeftRedraw = true;
				needsRightRedraw = true;
			}
		});
		glfwSetWindowSizeLimits(handle, (int)(400*dpiScale), (int)(200*dpiScale), GLFW_DONT_CARE, GLFW_DONT_CARE);
		glfwSetWindowFocusCallback(handle, (window, focus) -> {
			synchronized (this) {
				needsFullRedraw = true;
			}
		});
	}
	
	public void create(Window parent, double dpiScale) {
		create(parent, Translate.format("dialog.flavors.title"), 600, 400, dpiScale);
	}
	
	@Override
	protected synchronized void onMouseMove(double x, double y) {
		needsFullRedraw = true;
		preferKeyboard = false;
	}
	
	@Override
	protected synchronized void onMouseClick() {
		synchronized (this) {
			needsFullRedraw = true;
			preferKeyboard = false;
		}
	}

	@Override
	protected void setupGL() {
		
	}
	
	@Override
	protected synchronized boolean needsRerender() {
		return Math.abs(scrollVel) > 1e-5 || needsFullRedraw || needsLeftRedraw || needsRightRedraw;
	}

	@Override
	protected synchronized void renderInner() {
		// Hell is real and I am its creator
		
		boolean nfr = needsFullRedraw;
		boolean nlr = nfr || needsLeftRedraw;
		boolean nrr = nfr || needsRightRedraw;
		
		long nsPerTick = TimeUnit.MILLISECONDS.toNanos(25);
		
		long time = System.nanoTime();
		
		if (maxScroll < 0) {
			scroll = 0;
			scrollVel = 0;
		}
		
		if (Math.abs(scrollVel) > 1e-5) {
			nlr = true;
		} else {
			scrollVel = 0;
			lastTick = time;
		}
		
		while (lastTick < time) {
			lastTick += nsPerTick;
			lastScroll = scroll;
			scroll += scrollVel;
			
			if (scroll < 0) {
				scrollVel = (-scroll)/4;
			} else if (scroll > maxScroll) {
				scrollVel = -(scroll-maxScroll)/4;
			}
			
			scrollVel *= 0.6f;
		}
		
		float tickProgress = 1-((lastTick-time)/(float)nsPerTick);
		float scroll = (1 - tickProgress) * lastScroll + tickProgress * this.scroll;
		
		float x = 16;
		float y = 20-scroll;
		float startY = y;
		
		float leftArea = width*0.45f;
		float rightArea = (width-leftArea)-12;
		
		boolean focused = glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE;
		
		float scrollKnobX1 = leftArea;
		float scrollKnobX2 = width-rightArea;
		
		if (maxScroll < 0) {
			glColor(ColorChoice.BACKGROUND);
			drawRectXY(scrollKnobX1, 0, scrollKnobX2, height);
		} else {
			float scrollKnobHeight = (height/(height+maxScroll))*(height-4);
			float scrollKnobOfs = (scroll/maxScroll)*(height-4-scrollKnobHeight);
			int scrollKnobInset = 3;
			float scrollKnobWidth = scrollKnobX2-scrollKnobX1;
			
			glBegin(GL_QUADS);
				glColor(ColorChoice.PROGRESSTRACK);
				buildRectXY(scrollKnobX1, 0, scrollKnobX2, height);
				
				glColor(ColorChoice.PROGRESS);
				buildRectWHI(scrollKnobX1, scrollKnobOfs+4,
						scrollKnobWidth, scrollKnobHeight,
						scrollKnobInset);
			glEnd();
			
			drawCircle(scrollKnobX1+(scrollKnobWidth/2), scrollKnobOfs+4, scrollKnobWidth-(scrollKnobInset*2));
			drawCircle(scrollKnobX1+(scrollKnobWidth/2), scrollKnobOfs+scrollKnobHeight, scrollKnobWidth-(scrollKnobInset*2));
		}
		
		if (nlr) {
			glColor(ColorChoice.BACKGROUND);
			drawRectXY(0, 0, leftArea, height);
			boolean firstBoolean = true;
			hovered = -1;
			subhovered = -1;
			for (int i = 0; i < flavors.size(); i++) {
				if (didKeyboardNav && i == highlighted) {
					if (y < 0) {
						scrollVel = y/1.5f;
					} else if (y > height) {
						scrollVel = ((y+64)-height)/1.5f;
					}
				}
				FlavorGroup grp = flavors.get(i);
				if (grp.isBoolean()) {
					if (firstBoolean) {
						y += 8;
						firstBoolean = false;
					}
					
					if (y > -16 && y < height+12) {
						glColor(ColorChoice.DIALOG);
						float w = font.drawString(Face.REGULAR, x+20, y+6, 18, grp.name);
						
						boolean toggle = false;
						boolean hover = false;
						
						if (i == highlighted) {
							if (focused) {
								glColor(ColorChoice.DIALOG, 0.75f);
								glBegin(GL_QUADS);
									glVertex2f(x-8, y+14);
									glVertex2f(x+w+20, y+14);
									glVertex2f(x+w+20, y+16);
									glVertex2f(x-8, y+16);
								glEnd();
							}
							
							toggle = enterPressed || leftPressed || rightPressed;
						}
						
						if (mouseX >= x-9 && mouseX <= x+20+w &&
								mouseY >= y-12 && mouseY <= y+12) {
							hovered = i;
							toggle = mouseClicked;
							hover = true;
						}
						
						if (toggle) {
							if (selectedFlavors.contains(grp.id+"_on")) {
								selectedFlavors.remove(grp.id+"_on");
								selectedFlavors.add(grp.id+"_off");
							} else {
								selectedFlavors.remove(grp.id+"_off");
								selectedFlavors.add(grp.id+"_on");
							}
						}
						
						if (selectedFlavors.contains(grp.id+"_on")) {
							glColor(ColorChoice.BUTTON);
							drawCircle(x, y, 24);
							if (hover) {
								glColor(ColorChoice.BUTTONTEXT, 0.25f);
								drawCircle(x, y, 24);
							}
							glColor(ColorChoice.BUTTONTEXT);
							drawCheckmark(x, y);
						} else {
							glColor(ColorChoice.DIALOG);
							drawCircle(x, y, 24);
							glColor(ColorChoice.BACKGROUND, hover ? 0.75f : 1);
							drawCircle(x, y, 16);
						}
						glColor(ColorChoice.DIALOG);
					}
					y += 34;
				} else {
					if (y > -48 && y < height) {
						glColor(ColorChoice.DIALOG);
						font.drawString(Face.REGULAR, x-9, y+6, 18, grp.name);
						if (i == highlighted) {
							if (focused) {
								glColor(ColorChoice.DIALOG, 0.75f);
								drawRectXY(x-3, y+12, leftArea-6, y+14);
							}
							if (leftPressed || rightPressed || enterPressed) {
								int dir = leftPressed ? -1 : 1;
								int selI = getSelectedIndex(grp);
								for (FlavorChoice ch2 : grp.choices) {
									selectedFlavors.remove(ch2.id);
								}
								selI += dir;
								if (selI < 0) selI = grp.choices.size()+selI;
								selI %= grp.choices.size();
								selectedFlavors.add(grp.choices.get(selI).id);
							}
						}
						int segments = grp.choices.size();
						float btnH = 32;
						float subX = x-9;
						float subY = y+14;
						float btnW = (leftArea-subX)/segments;
						
						if (mouseX >= subX && mouseX <= leftArea &&
								mouseY >= y-6 && mouseY <= subY+btnH) {
							hovered = i;
						}
						
						for (int pass = 0; pass < 2; pass++) {
							subX = x-9;
							for (int j = 0; j < grp.choices.size(); j++) {
								FlavorChoice ch = grp.choices.get(j);
								float x1 = subX;
								float y1 = subY;
								float x2 = subX+btnW;
								float y2 = subY+btnH;
	
								boolean hover = false;
								if (mouseX >= x1 && mouseX <= x2 &&
										mouseY >= y1 && mouseY <= y2) {
									subhovered = j;
									hover = true;
									if (pass == 0 && mouseClicked) {
										for (FlavorChoice ch2 : grp.choices) {
											selectedFlavors.remove(ch2.id);
										}
										selectedFlavors.add(ch.id);
									}
								}
								
								if (pass == 1) {
									boolean sel = selectedFlavors.contains(ch.id);
									String name = ch.name;
									String ellipsis = "";
									float w;
									do {
										w = font.measureString(Face.REGULAR, 14, name+ellipsis);
										if (w > btnW-4) {
											ellipsis = "…";
											name = name.substring(0, name.length()-1);
										}
									} while (w > btnW);
									glBegin(GL_QUADS);
										glColor(sel ? ColorChoice.BUTTON : ColorChoice.DIALOG);
										buildRectXY(x1, y1, x2, y2);
										
										if (!sel || hover) {
											int inset = 2;
											if (sel) {
												glColor(ColorChoice.BUTTONTEXT, 0.25f);
												inset = 0;
											} else {
												glColor(ColorChoice.BACKGROUND, hover ? 0.75f : 1);
											}
											buildRectXYI(x1, y1, x2, y2, inset);
										}
									glEnd();
									glColor(sel ? ColorChoice.BUTTONTEXT : ColorChoice.DIALOG);
									font.drawString(sel ? Face.BOLD : Face.REGULAR, subX+(btnW-w)/2, subY+21, 14, name+ellipsis);
								}
								subX += btnW;
							}
						}
					}
					y += 64;
				}
			}

			if (hovered == -1) {
				if (clickCursorActive) {
					clickCursorActive = false;
					Puppet.runOnMainThread(() -> {
						if (!run) return;
						glfwSetCursor(handle, NULL);
					});
				}
			} else {
				if (!clickCursorActive) {
					clickCursorActive = true;
					Puppet.runOnMainThread(() -> {
						if (!run) return;
						glfwSetCursor(handle, clickCursor);
					});
				}
			}
		}
		

		if (nrr) {
			glColor(ColorChoice.BACKGROUND);
			drawRectXY(width-rightArea, 0, width, height);
			
			int target = preferKeyboard ? highlighted : hovered;
			
			if (target == -1) {
				glColor(ColorChoice.SUBTITLE);
				float textX = width-rightArea+8;
				font.drawWrapped(Face.ITALIC, textX, textX, 24, 16, rightArea-16, Translate.format("flavor.hover_notice"));
			} else {
				FlavorGroup grp = flavors.get(target);
				int subtarget = grp.isBoolean() ? -1 : preferKeyboard ? getSelectedIndex(grp) : subhovered;
				String html;
				if (subtarget == -1) {
					html = makeMoreXMLy("<h1>"+grp.name+"</h1>"+grp.description);
				} else {
					FlavorChoice choice = grp.choices.get(subtarget);
					html = makeMoreXMLy("<h1>"+grp.name+"</h1>"+grp.description+"<h2>"+choice.name+"</h2><br/>"+choice.description);
				}
				
				NodeDrawState state = new NodeDrawState();
				state.x = state.baseX = width-rightArea+8;
				state.y = 20;
				state.size = 14;
				state.maxWidth = rightArea-16;
				state.face = Face.REGULAR;
				glColor(ColorChoice.DIALOG);
				drawNode(state, descriptionDocuments.computeIfAbsent(html, FlavorDialogWindow::parseXHTML));
			}
			
			String done = Translate.format("option.done");
			float doneW = font.measureString(Face.REGULAR, 14, done);
			float btnW = doneW+24;
			float btnX = width-(btnW+8);
			float btnY = height-27-8;
			float btnH = 27;
			boolean hover = mouseX >= btnX && mouseX <= btnX+btnW &&
					mouseY >= btnY && mouseY <= btnY+btnH;
			glColor(ColorChoice.BUTTON);
			drawRectWH(btnX, btnY, btnW, btnH);
			if (hover) {
				glColor(ColorChoice.BUTTONTEXT, 0.25f);
				drawRectWH(btnX, btnY, btnW, btnH);
			}
			glColor(ColorChoice.BUTTONTEXT);
			font.drawString(Face.REGULAR, btnX+(btnW-doneW)/2, btnY+18, 14, done);
			
			
			if (ctrlEnterPressed || (mouseClicked && hover)) {
				StringJoiner sj = new StringJoiner("\u001C");
				for (String s : selectedFlavors) {
					sj.add(s);
				}
				Puppet.reportChoice(name, sj.toString());
				close();
			}
		}
		
		maxScroll = y-startY-height;
		
		needsFullRedraw = false;
		needsLeftRedraw = false;
		needsRightRedraw = false;
		
		enterPressed = false;
		leftPressed = false;
		rightPressed = false;
		
		didKeyboardNav = false;
	}

	private static class NodeDrawState implements Cloneable {
		public float x, y, baseX, maxWidth;
		public Face face;
		public float size;
		
		@Override
		public NodeDrawState clone() {
			try {
				return (NodeDrawState)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError(e);
			}
		}
	}
	
	private static final Set<String> breakElements = new HashSet<>(Arrays.asList("ul", "li", "h1", "h2", "h3", "br"));
	
	private void drawNode(NodeDrawState state, Node node) {
		if (node instanceof Text) {
			Text t = (Text)node;
			float[] res = font.drawWrapped(state.face, state.baseX, state.x, state.y, state.size, state.maxWidth, t.getData());
			state.x = res[0];
			state.y = res[1];
		} else {
			String tagName = node.getNodeName().toLowerCase(Locale.ROOT);
			if (breakElements.contains(tagName)) {
				if (state.x > state.baseX) {
					state.x = state.baseX;
					state.y += (int)(state.size*1.4f);
				}
			}
			switch (tagName) {
				case "h1": {
					NodeDrawState nw = state.clone();
					nw.y += 8;
					nw.size = 28;
					nw.face = approach(nw.face, Face.BOLD);
					drawChildren(nw, node);
					state.y = nw.y+32;
					state.x = state.baseX;
					break;
				}
				case "h2": {
					NodeDrawState nw = state.clone();
					nw.y += 6;
					nw.size = 20;
					nw.face = approach(nw.face, Face.BOLD);
					drawChildren(nw, node);
					state.y = nw.y+32;
					state.x = state.baseX;
					break;
				}
				case "h3": {
					NodeDrawState nw = state.clone();
					nw.y += 4;
					nw.size = 18;
					nw.face = approach(nw.face, Face.BOLD);
					drawChildren(nw, node);
					state.y = nw.y+32;
					state.x = state.baseX;
					break;
				}
				case "ul": {
					NodeDrawState nw = state.clone();
					nw.y += 16;
					nw.baseX += 24;
					nw.x = nw.baseX;
					drawChildren(nw, node);
					state.y = nw.y+32;
					state.x = state.baseX;
					break;
				}
				case "li": {
					drawCircle(state.x-8, state.y-(state.size/3), 6);
					drawChildren(state, node);
					break;
				}
				case "b": case "strong": {
					NodeDrawState nw = state.clone();
					nw.face = approach(nw.face, Face.BOLD);
					drawChildren(nw, node);
					state.y = nw.y;
					state.x = nw.x;
					break;
				}
				case "i": case "em": {
					NodeDrawState nw = state.clone();
					nw.face = approach(nw.face, Face.ITALIC);
					drawChildren(nw, node);
					state.y = nw.y;
					state.x = nw.x;
					break;
				}
				default: {
					drawChildren(state, node);
					break;
				}
			}
		}
	}

	private Face approach(Face cur, Face next) {
		switch (cur) {
			case BOLDITALIC: return cur;
			case BOLD: {
				switch (next) {
					case ITALIC: return Face.BOLDITALIC;
					default: return Face.BOLD;
				}
			}
			case ITALIC: {
				switch (next) {
					case BOLD: return Face.BOLDITALIC;
					default: return Face.ITALIC;
				}
			}
			case REGULAR: {
				return next;
			}
			default: {
				return cur;
			}
		}
	}

	private void drawChildren(NodeDrawState state, Node node) {
		for (int i = 0; i < node.getChildNodes().getLength(); i++) {
			drawNode(state, node.getChildNodes().item(i));
		}
	}

	private static final Pattern NEWLINEY = Pattern.compile("(\n|<br\\s+>)");
	
	private String makeMoreXMLy(String str) {
		return "<body>"+NEWLINEY.matcher(str).replaceAll("<br/>")+"</body>";
	}
	
	private static final DocumentBuilder docbldr; static {
		try {
			DocumentBuilderFactory fct = DocumentBuilderFactory.newInstance();
			fct.setCoalescing(true);
			fct.setIgnoringComments(true);
			docbldr = fct.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new AssertionError(e);
		}
	}
	
	private static Document parseXHTML(String html) {
		try {
			return docbldr.parse(new InputSource(new StringReader(html)));
		} catch (SAXException e) {
			return docbldr.newDocument();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private int getSelectedIndex(FlavorGroup grp) {
		int selI = 0;
		for (int j = 0; j < grp.choices.size(); j++) {
			FlavorChoice ch = grp.choices.get(j);
			if (selectedFlavors.contains(ch.id)) {
				selI = j;
				break;
			}
		}
		return selI;
	}

}
