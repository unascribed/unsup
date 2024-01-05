package com.unascribed.sup;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.FlavorGroup.FlavorChoice;
import com.unascribed.sup.util.SwingHelper;

/**
 * LWJGL3's GLFW is incompatible with AWT. If we initialize AWT pre-launch with our GUI tidbits,
 * versions of Minecraft that use LWJGL3 won't work correctly especially on macOS. This Puppet
 * process's sole purpose of existence is to allow us to interact with AWT at an arm's length and
 * exit once we're done using it.
 */
public class Puppet {
	
	public static final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
	private static JFrame frame;
	private static JLabel title, subtitle;
	private static JProgressBar prog;
	private static JThrobber throbber;
	private static Image logo;
	
	private static Color colorBackground = Color.BLACK;
	private static Color colorTitle = Color.WHITE;
	private static Color colorSubtitle = Color.GRAY;
	
	private static Color colorProgress = Color.RED;
	private static Color colorProgressTrack = Color.GRAY;
	
	private static Color colorDialog = Color.WHITE;
	private static Color colorButton = Color.YELLOW;
	private static Color colorButtonText = Color.BLACK;
	
	
	public static void main(String[] args) {
		log("INFO", "Iä! Iä! Cthulhu fhtagn!");
		
		SwingHelper.fixSwing();
		
		MetalLookAndFeel.setCurrentTheme(new OceanTheme());
		try {
			UIManager.setLookAndFeel(new MetalLookAndFeel());
		} catch (UnsupportedLookAndFeelException e1) {
			log("ERROR", "Failed to set LaF");
			return;
		}
		
		logo = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup.png"));
		
		System.out.println("unsup puppet ready");
		
		Map<String, ScheduledFuture<?>> orders = new HashMap<>();
		Map<String, Runnable> orderRunnables = new HashMap<>();
		
		BufferedInputStream in = new BufferedInputStream(System.in, 512);
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try {
			while (true) {
				int by = in.read();
				if (by == -1) break;
				String line;
				if (by == 0) {
					line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
					buffer.reset();
				} else {
					buffer.write(by);
					continue;
				}
				String name;
				if (line.startsWith("[")) {
					int close = line.indexOf(']');
					name = line.substring(1, close);
					line = line.substring(close+1);
				} else {
					name = null;
				}
				String timing = line.substring(0, line.indexOf(':'));
				int delay;
				if (timing.isEmpty()) {
					delay = 0;
				} else {
					delay = Integer.parseInt(timing);
				}
				int eq = line.indexOf('=');
				String order = line.substring(line.indexOf(':')+1, eq == -1 ? line.length() : eq);
				String arg = eq == -1 ? "" : line.substring(eq+1);
				Runnable r;
				switch (order) {
					case "build": {
						r = invokeLater(() -> {
							buildUi();
						});
						break;
					}
					case "colorBackground": colorBackground = new Color(Integer.parseInt(arg, 16)); continue;
					case "colorTitle": colorTitle = new Color(Integer.parseInt(arg, 16)); continue;
					case "colorSubtitle": colorSubtitle = new Color(Integer.parseInt(arg, 16)); continue;
					case "colorProgress": colorProgress = new Color(Integer.parseInt(arg, 16)); continue;
					case "colorProgressTrack": colorProgressTrack = new Color(Integer.parseInt(arg, 16)); continue;
					case "colorDialog": colorDialog = new Color(Integer.parseInt(arg, 16)); continue;
					case "colorButton": colorButton = new Color(Integer.parseInt(arg, 16)); continue;
					case "colorButtonText": colorButtonText = new Color(Integer.parseInt(arg, 16)); continue;
					case "belay": {
						r = () -> {
							synchronized (orders) {
								if (orders.containsKey(arg)) {
									orders.remove(arg).cancel(false);
									orderRunnables.remove(arg);
								}
							}
						};
						break;
					}
					case "expedite": {
						r = () -> {
							Runnable inner = null;
							synchronized (orders) {
								if (orders.containsKey(arg)) {
									if (orders.remove(arg).cancel(false)) {
										// we don't want to be holding the orders mutex while we run
										// the original order
										inner = orderRunnables.remove(arg);
									} else {
										orderRunnables.remove(arg);
									}
								}
							}
							if (inner != null) inner.run();
						};
						break;
					}
					case "visible": {
						boolean b = Boolean.parseBoolean(arg);
						r = invokeLater(() -> {
							frame.setVisible(b);
						});
						break;
					}
					case "exit": {
						r = () -> {
							System.exit(0);
						};
						break;
					}
					case "mode": {
						if ("ind".equals(arg)) {
							r = invokeLater(() -> {
								prog.setVisible(false);
								prog.invalidate();
							});
						} else if ("det".equals(arg)) {
							r = invokeLater(() -> {
								prog.setVisible(true);
								prog.setValue(0);
								prog.invalidate();
							});
						} else if ("done".equals(arg)) {
							r = () -> {
								if (!frame.isVisible()) {
									System.out.println("doneAnimating");
									return;
								}
								SwingUtilities.invokeLater(() -> {
									prog.setVisible(false);
									throbber.animateDone();
									prog.invalidate();
								});
							};
						} else {
							log("WARN", "Unknown mode "+arg+", expected ind or det");
							continue;
						}
						break;
					}
					case "prog": {
						int i = Integer.parseInt(arg);
						r = invokeLater(() -> {
							prog.setValue(i);
							prog.repaint();
						});
						break;
					}
					case "title": {
						r = invokeLater(() -> {
							title.setText("<html><nobr>"+arg+"</nobr></html>");
						});
						break;
					}
					case "subtitle": {
						r = invokeLater(() -> {
							subtitle.setText("<html><nobr>"+arg+"</nobr></html>");
						});
						break;
					}
					case "alert": {
						r = () -> {
							String[] split = arg.split(":");
							String title = split[0];
							String body = "<html><center>"+split[1]+"</center></html>";
							String messageTypeStr = split[2];
							String optionTypeStr = split[3];
							if (messageTypeStr.startsWith("choice=")) {
								String[] options = messageTypeStr.substring(7).split("\u001C");
								for (int i = 0; i < options.length; i++) {
									options[i] = options[i].replace('\u001B', ':');
								}
								SwingUtilities.invokeLater(() -> {
									openChoiceDialog(name, title, body, optionTypeStr, options);
								});
							} else {
								int messageType;
								switch (messageTypeStr) {
									case "question":
										messageType = JOptionPane.QUESTION_MESSAGE;
										break;
									case "info":
										messageType = JOptionPane.INFORMATION_MESSAGE;
										break;
									case "warn":
										messageType = JOptionPane.WARNING_MESSAGE;
										break;
									case "error":
										messageType = JOptionPane.ERROR_MESSAGE;
										break;
									default:
										log("WARN", "Unknown dialog type "+messageTypeStr+", defaulting to none");
										// fallthru
									case "none":
										messageType = JOptionPane.PLAIN_MESSAGE;
										break;
								}
								String[] options;
								switch (optionTypeStr) {
									case "yesno":
										options = new String[]{"Yes", "No"};
										break;
									case "yesnocancel":
										options = new String[]{"Yes", "No", "Cancel"};
										break;
									case "okcancel":
										options = new String[]{"OK", "Cancel"};
										break;
									case "yesnotoallcancel":
										options = new String[]{"Yes to All", "Yes", "No to All", "No", "Cancel"};
										break;
									default:
										log("WARN", "Unknown dialog option type "+optionTypeStr+", defaulting to ok");
										// fallthru
									case "ok":
										options = new String[]{"Ok"};
										break;
								}
								SwingUtilities.invokeLater(() -> {
									openMessageDialog(name, title, body, messageType, options);
								});
							}
						};
						break;
					}
					case "pickFlavor": {
						r = () -> {
							String[] split = arg.split(":");
							List<FlavorGroup> groups = new ArrayList<>();
							for (String s : split[0].replace('\u001B', ':').split("\u001D")) {
								String[] fields = s.split("\u001C");
								FlavorGroup grp = new FlavorGroup();
								grp.id = fields[0];
								grp.name = fields[1];
								grp.description = fields[2];
								for (int i = 3; i < fields.length; i += 4) {
									FlavorChoice c = new FlavorChoice();
									c.id = fields[i];
									c.name = fields[i+1];
									c.description = fields[i+2];
									c.def = Boolean.parseBoolean(fields[i+3]);
									grp.choices.add(c);
								}
								groups.add(grp);
							}
							SwingUtilities.invokeLater(() -> {
								openFlavorDialog(name, groups);
							});
						};
						break;
					}
					default: {
						log("WARN", "Unknown order "+order);
						continue;
					}
				}
				Runnable fr = r;
				if (name != null) {
					fr = () -> {
						r.run();
						synchronized (orders) {
							orders.remove(name);
							orderRunnables.remove(name);
						}
					};
				}
				if (delay > 0) {
					ScheduledFuture<?> future = sched.schedule(fr, delay, TimeUnit.MILLISECONDS);
					if (name != null) {
						synchronized (orders) {
							orders.put(name, future);
							orderRunnables.put(name, fr);
						}
					}
				} else {
					sched.execute(fr);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	private static Runnable invokeLater(Runnable r) {
		return () -> {
			SwingUtilities.invokeLater(r);
		};
	}

	private static void log(String flavor, String msg) {
		System.err.println(flavor+"|"+msg);
	}
	
	private static void buildUi() {
		frame = new JFrame("unsup v"+Util.VERSION);
		frame.setIconImage(logo);
		frame.setSize(512, 128);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			private boolean closeAlreadyAttempted = false;
			
			@Override
			public void windowClosing(WindowEvent e) {
				if (closeAlreadyAttempted) {
					openMessageDialog(null, "unsup is busy",
							"<html><center><b>The updater is busy and can't exit right now.</b><br/>Please wait a moment.</center></html>",
							JOptionPane.WARNING_MESSAGE, "OK");
				} else {
					closeAlreadyAttempted = true;
					System.out.println("closeRequested");
				}
			}
		});
		Box outer = Box.createHorizontalBox();
		outer.setOpaque(true);
		outer.setBackground(colorBackground);
		
		throbber = new JThrobber(sched);
		throbber.setForeground(colorProgress);
		throbber.setMinimumSize(new Dimension(64, 64));
		throbber.setPreferredSize(throbber.getMinimumSize());
		outer.add(throbber);
		
		Box inner = Box.createVerticalBox();
		inner.setBorder(new EmptyBorder(8, 0, 0, 0));
		inner.add(Box.createHorizontalGlue());
		title = new JLabel("<html>Reticulating splines...</html>");
		title.setForeground(colorTitle);
		title.setFont(title.getFont().deriveFont(24f).deriveFont(Font.BOLD));
		inner.add(title);
		subtitle = new JLabel("");
		subtitle.setForeground(colorSubtitle);
		subtitle.setFont(subtitle.getFont().deriveFont(14f).deriveFont(Font.PLAIN));
		subtitle.setPreferredSize(new Dimension(448, 18));
		subtitle.setMaximumSize(new Dimension(448, 18));
		inner.add(subtitle);
		inner.add(Box.createVerticalGlue());
		Box inset = Box.createVerticalBox();
		prog = new JProgressBar(JProgressBar.HORIZONTAL) {
			@Override
			public void paint(Graphics g) {
				Graphics2D g2d = (Graphics2D)g;
				fix(g2d);
				super.paint(g);
			}
		};
		prog.setForeground(colorProgress);
		prog.setBackground(colorProgressTrack);
		prog.setBorderPainted(false);
		prog.setMinimumSize(new Dimension(0, 6));
		prog.setMaximumSize(new Dimension(32767, 6));
		prog.setMaximum(1000);
		prog.setValue(500);
		inset.add(prog);
		inset.setBorder(new EmptyBorder(4, 0, 4, 4));
		
		inner.add(inset);
		
		outer.add(inner);
		
		frame.setContentPane(outer);
	}

	protected static void fix(Graphics2D g2d) {
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
	}

	private static void configureOptionPane(JOptionPane pane) {
		List<JComponent> queue = new ArrayList<>();
		queue.add(pane);
		List<JComponent> queueQueue = new ArrayList<>();
		while (!queue.isEmpty()) {
			for (JComponent jc : queue) {
				if (jc instanceof JLabel) {
					((JLabel)jc).setIcon(null);
				}
				if (jc.getFont() != null) {
					jc.setFont(jc.getFont().deriveFont(Font.PLAIN).deriveFont(14f));
				}
				if (jc instanceof JButton) {
					jc.setBackground(colorButton);
					jc.setForeground(colorButtonText);
					jc.setBorder(new EmptyBorder(4, 12, 4, 12));
				} else {
					jc.setBackground(colorBackground);
					jc.setForeground(colorDialog);
				}
				for (Component child : jc.getComponents()) {
					if (child instanceof JComponent) {
						queueQueue.add((JComponent)child);
					}
				}
			}
			queue.clear();
			queue.addAll(queueQueue);
			queueQueue.clear();
		}
		pane.setBackground(colorBackground);
		pane.setForeground(colorDialog);
	}
	
	private static void configureOptionDialog(JOptionPane pane, JDialog dialog) {
		dialog.setIconImage(logo);
		dialog.setModal(true);
		dialog.setBackground(colorBackground);
		dialog.setForeground(colorDialog);
		dialog.setContentPane(pane);
		dialog.pack();
		dialog.setLocationRelativeTo(frame);
	}
	
	private static void openMessageDialog(String name, String title, String body, int messageType, String... options) {
		JOptionPane pane = new JOptionPane(body, messageType, JOptionPane.DEFAULT_OPTION, null, options);
		configureOptionPane(pane);
		JDialog dialog = pane.createDialog(frame != null && frame.isVisible() ? frame : null, title);
		configureOptionDialog(pane, dialog);
		dialog.setVisible(true);
		if (name != null) {
			Object sel = pane.getValue();
			String opt;
			if (sel == null || sel == JOptionPane.UNINITIALIZED_VALUE || !(sel instanceof String)) {
				opt = "closed";
			} else {
				opt = ((String)sel).toLowerCase(Locale.ROOT).replace(" ", "");
			}
			System.out.println("alert:"+name+":"+opt);
		}
	}

	private static void openChoiceDialog(String name, String title, String body, String def, String... options) {
		JOptionPane pane = new JOptionPane(body, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{"OK"});
		pane.setWantsInput(true);
		pane.setSelectionValues(options);
		pane.setInitialSelectionValue(def);
		configureOptionPane(pane);
		JDialog dialog = pane.createDialog(frame != null && frame.isVisible() ? frame : null, title);
		configureOptionDialog(pane, dialog);
		dialog.setVisible(true);
		if (name != null) {
			Object sel = pane.getInputValue();
			String opt;
			if (sel == null || !(sel instanceof String)) {
				opt = "closed";
			} else {
				opt = (String)sel;
			}
			System.out.println("alert:"+name+":"+opt);
		}
	}
	
	private static void openFlavorDialog(String name, List<FlavorGroup> groups) {
		JDialog dialog = new JDialog(frame != null && frame.isVisible() ? frame : null, "Select flavors");
		dialog.setIconImage(logo);
		dialog.setModal(true);
		dialog.setBackground(colorBackground);
		dialog.setForeground(colorDialog);
		dialog.setSize(854, 480);
		dialog.setLocationRelativeTo(frame);
		String descPfx = "<style>body { font-family: Dialog; color: #"+Integer.toHexString(colorDialog.getRGB()|0xFF000000).substring(2)+"; }</style>";
		String noDesc = "<font size=\"4\" face=\"Dialog\" color=\"#"+Integer.toHexString(colorSubtitle.getRGB()|0xFF000000).substring(2)+"\"><i>Hover an option to the left to see an explanation</i></font>";
		JEditorPane desc = new JEditorPane("text/html", noDesc);
		Set<String> results = new HashSet<>();
		Set<String> descriptions = new HashSet<>();
		Runnable updateDescription = () -> {
			String html;
			if (descriptions.isEmpty()) {
				html = noDesc;
			} else {
				html = descPfx+(descriptions.iterator().next().replace("\n", "<br/>"));
			}
			desc.setText(html);
		};
		Box options = Box.createVerticalBox();
		for (FlavorGroup grp : groups) {
			if (!isBoolean(grp)) {
				Box box = Box.createVerticalBox();
				JLabel title = new JLabel(grp.name);
				title.setBorder(new EmptyBorder(8,8,8,8));
				title.setFont(title.getFont().deriveFont(18f));
				title.setMinimumSize(new Dimension(0, 24));
				title.setForeground(colorDialog);
				String titleDescHtml = "<h1>"+grp.name+"</h1>"+grp.description;
				title.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						descriptions.add(titleDescHtml);
						updateDescription.run();
					}
					@Override
					public void mouseExited(MouseEvent e) {
						descriptions.remove(titleDescHtml);
						updateDescription.run();
					}
				});
				box.add(title);
				ButtonGroup btng = new ButtonGroup();
				Box btns = Box.createHorizontalBox();
				boolean selectedAny = false;
				for (FlavorChoice c : grp.choices) {
					JToggleButton btn = new JToggleButton(c.name);
					btn.setUI(new BasicButtonUI());
					btn.setFont(btn.getFont().deriveFont(14f));
					btn.setSelected(c.def);
					if (c.def) selectedAny = true;
					btn.setMinimumSize(new Dimension(0, 32));
					btn.setPreferredSize(new Dimension(64, 32));
					btn.setMaximumSize(new Dimension(32767, 32));
					Runnable updateLook = () -> {
						if (btn.isSelected()) {
							btn.setBorder(new EmptyBorder(2,2,2,2));
							btn.setBackground(colorButton);
							btn.setForeground(colorButtonText);
							btn.setFont(btn.getFont().deriveFont(Font.BOLD));
							results.add(c.id);
						} else {
							btn.setBorder(new LineBorder(colorDialog, 2));
							btn.setBackground(colorBackground);
							btn.setForeground(colorDialog);
							btn.setFont(btn.getFont().deriveFont(0));
							results.remove(c.id);
						}
					};
					updateLook.run();
					String descHtml = "<h1>"+grp.name+"</h1><h2>"+c.name+"</h2>"+c.description;
					btn.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseEntered(MouseEvent e) {
							descriptions.add(descHtml);
							updateDescription.run();
						}
						@Override
						public void mouseExited(MouseEvent e) {
							descriptions.remove(descHtml);
							updateDescription.run();
						}
					});
					btn.addChangeListener(e -> {
						updateLook.run();
					});
					btns.add(btn);
					btng.add(btn);
				}
				if (!selectedAny && !grp.choices.isEmpty()) {
					btng.getElements().nextElement().setSelected(true);
				}
				btns.setAlignmentX(0);
				box.add(btns);
				options.add(box);
			}
		}
		options.add(Box.createVerticalStrut(8));
		for (FlavorGroup grp : groups) {
			if (isBoolean(grp)) {
				JCheckBox cb = new JCheckBox(grp.name);
				cb.setUI(new BasicCheckBoxUI());
				cb.setIcon(new Icon() {
					
					@Override
					public void paintIcon(Component c, Graphics g, int x, int y) {
						Graphics2D g2d = (Graphics2D)g;
						fix(g2d);
						g2d.setColor(colorDialog);
						g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
						g2d.drawOval(6, 6, 20, 20);
					}
					
					@Override
					public int getIconWidth() {
						return 24;
					}
					
					@Override
					public int getIconHeight() {
						return 24;
					}
				});
				cb.setSelectedIcon(new Icon() {
					
					@Override
					public void paintIcon(Component c, Graphics g, int x, int y) {
						Graphics2D g2d = (Graphics2D)g;
						fix(g2d);
						g2d.translate(4, 4);
						g2d.setColor(colorButton);
						g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
						g2d.fillOval(0, 0, 24, 24);
						g2d.setColor(colorButtonText);
						g2d.drawPolyline(new int[] {
							5, 10, 19
						}, new int[] {
							12, 17, 7
						}, 3);
						g2d.translate(-4, -4);
					}
					
					@Override
					public int getIconWidth() {
						return 24;
					}
					
					@Override
					public int getIconHeight() {
						return 24;
					}
				});
				cb.setIconTextGap(8);
				cb.setFont(title.getFont().deriveFont(18f).deriveFont(0));
				cb.setMinimumSize(new Dimension(0, 24));
				cb.setForeground(colorDialog);
				cb.setBackground(colorBackground);
				cb.setSelected(grp.choices.stream().filter(c -> c.id.endsWith("_on")).findFirst().map(c -> c.def).orElse(false));
				if (cb.isSelected()) {
					results.add(grp.id+"_on");
				} else {
					results.add(grp.id+"_off");
				}
				String titleDescHtml = "<h1>"+grp.name+"</h1>"+grp.description;
				cb.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						descriptions.add(titleDescHtml);
						updateDescription.run();
					}
					@Override
					public void mouseExited(MouseEvent e) {
						descriptions.remove(titleDescHtml);
						updateDescription.run();
					}
				});
				cb.addChangeListener((e) -> {
					if (cb.isSelected()) {
						results.add(grp.id+"_on");
						results.remove(grp.id+"_off");
					} else {
						results.add(grp.id+"_off");
						results.remove(grp.id+"_on");
					}
				});
				options.add(cb);
			}
		}
		options.add(Box.createVerticalGlue());
		options.setOpaque(true);
		options.setBackground(colorBackground);
		desc.setBackground(colorBackground);
		desc.setForeground(colorDialog);
		desc.setBorder(new EmptyBorder(8,8,8,8));
		JScrollPane scroll = new JScrollPane(options, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBackground(colorBackground);
		scroll.setForeground(colorDialog);
		JScrollBar vsb = scroll.getVerticalScrollBar();
		vsb.setSize(4, 480);
		vsb.setUnitIncrement(8);
		vsb.putClientProperty("JComponent.sizeVariant", "small");
		vsb.setUI(new BasicScrollBarUI() {
			@Override
			protected void configureScrollBarColors() {
				this.trackColor = colorBackground;
				this.thumbColor = this.thumbDarkShadowColor = this.thumbHighlightColor = this.thumbLightShadowColor = colorButton;
			}
			@Override
			protected void installDefaults() {
				super.installDefaults();
				this.incrGap = 4;
				this.decrGap = 4;
			}
			@Override
			protected JButton createDecreaseButton(int orientation) {
				return createIncreaseButton(orientation);
			}
			@Override
			protected JButton createIncreaseButton(int orientation) {
				JButton btn = new BasicArrowButton(orientation,
						colorButton,
						colorButton,
						colorButton,
						colorButton) {

					@Override
					public void paintTriangle(Graphics g, int x, int y, int size,
							int direction, boolean isEnabled) {
						Color oldColor = g.getColor();
						int mid, i, j;

						j = 0;
						size = Math.max(size, 2)+2;
						mid = (size / 2) - 1;

						g.translate(x+1, y);
						g.setColor(colorButtonText);

						switch(direction)       {
							case NORTH:
								for(i = 0; i < size; i++)      {
									g.drawLine(mid-i, i, mid+i, i);
								}
								break;
							case SOUTH:
								j = 0;
								for(i = size-1; i >= 0; i--)   {
									g.drawLine(mid-i, j, mid+i, j);
									j++;
								}
								break;
							case WEST:
								for(i = 0; i < size; i++)      {
									g.drawLine(i, mid-i, i, mid+i);
								}
								break;
							case EAST:
								j = 0;
								for(i = size-1; i >= 0; i--)   {
									g.drawLine(j, mid-i, j, mid+i);
									j++;
								}
								break;
						}
						g.translate(-x, -y);
						g.setColor(oldColor);
					}
				};
				btn.setBorder(null);
				return btn;
			}
		});
		vsb.setBackground(colorBackground);
		scroll.setBorder(null);
		Box outer = Box.createVerticalBox();
		outer.add(desc);
		outer.add(Box.createVerticalStrut(8));
		desc.setAlignmentX(0);
		Box buttons = Box.createHorizontalBox();
		buttons.setAlignmentX(0);
		buttons.setMaximumSize(new Dimension(32767, 48));
		buttons.add(Box.createHorizontalGlue());
		JButton done = new JButton("Done");
		done.setBackground(colorButton);
		done.setForeground(colorButtonText);
		buttons.add(done);
		done.addActionListener((e) -> {
			dialog.dispose();
		});
		buttons.add(Box.createHorizontalStrut(8));
		outer.add(buttons);
		outer.add(Box.createVerticalStrut(8));
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true,
				scroll,
				outer);
		split.setUI(new BasicSplitPaneUI() {
			@Override
			public BasicSplitPaneDivider createDefaultDivider() {
				BasicSplitPaneDivider d = new BasicSplitPaneDivider(this);
				d.setBackground(colorBackground);
				d.setForeground(colorDialog);
				d.setBorder(null);
				return d;
			}
		});
		split.setDividerSize(4);
		split.setSize(854, 480);
		split.setDividerLocation(0.4);
		split.setBackground(colorBackground);
		split.setForeground(colorDialog);
		split.setBorder(null);
		dialog.setContentPane(split);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		boolean[] closed = {false};
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				closed[0] = true;
				dialog.dispose();
				frame.dispose();
				System.out.println("closeRequested");
			}
		});
		dialog.setVisible(true);
		if (closed[0]) return;
		StringJoiner sj = new StringJoiner("\u001C");
		for (String s : results) {
			sj.add(s);
		}
		System.out.println("alert:"+name+":"+sj);
	}

	private static boolean isBoolean(FlavorGroup grp) {
		if (grp.choices.size() == 2) {
			String a = grp.choices.get(0).id;
			String b = grp.choices.get(1).id;
			String on = grp.id+"_on";
			String off = grp.id+"_off";
			return (a.equals(on) && b.equals(off))
					|| (a.equals(off) && b.equals(on));
		}
		return false;
	}
	
}
