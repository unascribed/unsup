package com.unascribed.sup.puppet.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

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

import org.brotli.dec.BrotliInputStream;

import com.unascribed.sup.AlertMessageType;
import com.unascribed.sup.Util;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.FlavorGroup.FlavorChoice;
import com.unascribed.sup.puppet.ColorChoice;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.PuppetDelegate;
import com.unascribed.sup.puppet.Translate;
import com.unascribed.sup.puppet.WindowIcons;
import com.unascribed.sup.util.SwingHelper;

import me.saharnooby.qoi.QOIColorSpace;
import me.saharnooby.qoi.QOIImage;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * LWJGL3's GLFW is incompatible with AWT. If we initialize AWT pre-launch with our GUI tidbits,
 * versions of Minecraft that use LWJGL3 won't work correctly especially on macOS. This Puppet
 * process's sole purpose of existence is to allow us to interact with AWT at an arm's length and
 * exit once we're done using it.
 */
public class SwingPuppet {

	private static JFrame frame;
	private static JLabel title, subtitle;
	private static JProgressBar prog;
	private static JThrobber throbber;
	private static Image logo, logoLowres;
	private static List<Image> logos;
	private static Font font, fontBold, fontItalic, fontBoldItalic;
	
	public static PuppetDelegate start() {
		SwingHelper.fixSwing();
		
		MetalLookAndFeel.setCurrentTheme(new OceanTheme());
		try {
			UIManager.setLookAndFeel(new MetalLookAndFeel());
		} catch (UnsupportedLookAndFeelException e1) {
			Puppet.log("ERROR", "Failed to set LaF", e1);
			return null;
		}
		
		logo = convertToBufferedImage(WindowIcons.highres);
		logoLowres = convertToBufferedImage(WindowIcons.lowres);
		logos = new ArrayList<>();
		logos.add(logo);
		logos.add(logoLowres);
		
		font = loadFont("NotoSans-Regular.ttf.br", Font.PLAIN);
		fontBold = loadFont("NotoSans-Bold.ttf.br", Font.BOLD);
		fontItalic = loadFont("NotoSans-Italic.ttf.br", Font.ITALIC);
		fontBoldItalic = loadFont("NotoSans-BoldItalic.ttf.br", Font.BOLD|Font.ITALIC);
		
		return new PuppetDelegate() {
			
			@Override
			public void build() {
				invokeLater(() -> {
					buildUi();
				});
			}
			
			@Override
			public void setVisible(boolean visible) {
				invokeLater(() -> {
					frame.setVisible(visible);
					Dimension max = frame.getContentPane().getSize();
					frame.getContentPane().setMaximumSize(max);
					frame.getContentPane().getComponent(0).setMaximumSize(max);
				});
			}
			
			@Override
			public void setProgressIndeterminate() {
				invokeLater(() -> {
					prog.setVisible(false);
					prog.invalidate();
				});
			}

			@Override
			public void setProgressDeterminate() {
				invokeLater(() -> {
					prog.setVisible(true);
					prog.setValue(0);
					prog.invalidate();
				});
			}
			
			@Override
			public void setDone() {
				if (!frame.isVisible()) {
					Puppet.reportDone();
					return;
				}
				invokeLater(() -> {
					prog.setVisible(false);
					throbber.animateDone();
					prog.invalidate();
				});
			}
			
			@Override
			public void setProgress(int permil) {
				invokeLater(() -> {
					prog.setValue(permil);
					prog.repaint();
				});
			}
			
			@Override
			public void setTitle(String str) {
				invokeLater(() -> {
					title.setText("<html><nobr>"+str+"</nobr></html>");
				});
			}
			
			@Override
			public void setSubtitle(String str) {
				invokeLater(() -> {
					subtitle.setText("<html><nobr>"+str+"</nobr></html>");
				});
			}

			@Override
			public void offerChangeFlavors(String name) {
				Puppet.reportChoice(name, "option.no");
			}
			
			@Override
			public void openChoiceDialog(String name, String title, String body, String[] options, String def) {
				invokeLater(() -> {
					SwingPuppet.openChoiceDialog(name, title, body, def, options);
				});
			}

			@Override
			public void openMessageDialog(String name, String title, String body, AlertMessageType messageType, String[] options, String def) {
				invokeLater(() -> {
					int swingType = JOptionPane.PLAIN_MESSAGE;
					switch (messageType) {
						case ERROR: swingType = JOptionPane.ERROR_MESSAGE; break;
						case INFO: swingType = JOptionPane.INFORMATION_MESSAGE; break;
						case NONE: swingType = JOptionPane.PLAIN_MESSAGE; break;
						case QUESTION: swingType = JOptionPane.QUESTION_MESSAGE; break;
						case WARN: swingType = JOptionPane.WARNING_MESSAGE; break;
					}
					SwingPuppet.openMessageDialog(name, title, body, swingType, options);
				});
			}
			
			@Override
			public void openFlavorDialog(String name, List<FlavorGroup> groups) {
				invokeLater(() -> {
					SwingPuppet.openFlavorDialog(name, groups);
				});
			}
			
		};
	}
	
	// https://github.com/saharNooby/qoi-java-awt/blob/d3acab3d88e6437624a9cdce107ecfd73988adec/src/main/java/me/saharnooby/qoi/plugin/QOIImageReader.java

	public static BufferedImage convertToBufferedImage(QOIImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int channels = image.getChannels();

		boolean hasAlpha = channels == 4;

		DataBufferByte buffer = new DataBufferByte(image.getPixelData(), width * height * channels);

		WritableRaster raster = Raster.createInterleavedRaster(
				buffer,
				width,
				height,
				channels * width,
				channels,
				new int[] {0, 1, 2, 3},
				new Point(0, 0)
		);

		ColorSpace awtColorSpace = getAwtColorSpace(image.getColorSpace());

		ColorModel colorModel = new ComponentColorModel(
				awtColorSpace,
				hasAlpha,
				false,
				Transparency.TRANSLUCENT,
				DataBuffer.TYPE_BYTE
		);

		return new BufferedImage(
				colorModel,
				raster,
				false,
				new Hashtable<>()
		);
	}

	private static ColorSpace getAwtColorSpace(QOIColorSpace colorSpace) {
		switch (colorSpace) {
			case SRGB:
				return ColorSpace.getInstance(ColorSpace.CS_sRGB);
			case LINEAR:
				return ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB);
			default:
				throw new RuntimeException();
		}
	}

	private static Font loadFont(String name, int style) {
		try (InputStream in = new BrotliInputStream(SwingPuppet.class.getClassLoader().getResourceAsStream("com/unascribed/sup/assets/fonts/"+name))) {
			Font f = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(style);
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
			return f;
		} catch (IOException | FontFormatException e) {
			Puppet.log("WARN", "Failed to load font "+name, e);
			return Font.decode("Dialog").deriveFont(style);
		}
	}

	private static Color getColor(ColorChoice choice) {
		return new Color(choice.get());
	}

	private static void buildUi() {
		frame = new JFrame("unsup v"+Util.VERSION);
		frame.setIconImages(logos);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.setResizable(false);
		frame.setAlwaysOnTop(true);
		frame.addWindowListener(new WindowAdapter() {
			private boolean closeAlreadyAttempted = false;
			
			@Override
			public void windowClosing(WindowEvent e) {
				if (closeAlreadyAttempted) {
					openMessageDialog(null, Translate.format("dialog.busy.title"),
							"<html><center><b>"+Translate.format("dialog.busy")+"</b><br/>"+Translate.format("dialog.please_wait")+"</center></html>",
							JOptionPane.WARNING_MESSAGE, Translate.format("option.ok"));
				} else {
					closeAlreadyAttempted = true;
					Puppet.reportCloseRequest();
				}
			}
		});
		Box outer = Box.createHorizontalBox();
		outer.setOpaque(true);
		outer.setBackground(getColor(ColorChoice.BACKGROUND));
		
		throbber = new JThrobber(Puppet.sched);
		throbber.setForeground(getColor(ColorChoice.PROGRESS));
		throbber.setMinimumSize(new Dimension(64, 64));
		throbber.setPreferredSize(throbber.getMinimumSize());
		throbber.setMaximumSize(throbber.getMinimumSize());
		outer.add(throbber);
		
		Box inner = Box.createVerticalBox();
		inner.setBorder(new EmptyBorder(8, 0, 0, 0));
		title = new JLabel("<html>"+Translate.format("title.default")+"</html>");
		title.setForeground(getColor(ColorChoice.TITLE));
		title.setFont(fontBold.deriveFont(24f));
		title.setAlignmentX(0);
		inner.add(title);
		subtitle = new JLabel("");
		subtitle.setForeground(getColor(ColorChoice.SUBTITLE));
		subtitle.setFont(font.deriveFont(14f));
		subtitle.setPreferredSize(new Dimension(448, 18));
		subtitle.setMaximumSize(new Dimension(448, 18));
		subtitle.setAlignmentX(0);
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
		prog.setForeground(getColor(ColorChoice.PROGRESS));
		prog.setBackground(getColor(ColorChoice.PROGRESSTRACK));
		prog.setBorderPainted(false);
		prog.setAlignmentX(0);
		prog.setMinimumSize(new Dimension(412, 6));
		prog.setPreferredSize(new Dimension(412, 6));
		prog.setMaximumSize(new Dimension(412, 6));
		prog.setMaximum(1000);
		prog.setValue(500);
		inset.add(prog);
		inset.setBorder(new EmptyBorder(4, 0, 4, 4));
		
		inner.add(inset);
		
		outer.add(inner);
		
		outer.setPreferredSize(new Dimension(480, 80));
		frame.setContentPane(outer);
		frame.pack();
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
					jc.setFont(font.deriveFont(14f));
				}
				if (jc instanceof JButton) {
					jc.setBackground(getColor(ColorChoice.BUTTON));
					jc.setForeground(getColor(ColorChoice.BUTTONTEXT));
					jc.setBorder(new EmptyBorder(4, 12, 4, 12));
				} else {
					jc.setBackground(getColor(ColorChoice.BACKGROUND));
					jc.setForeground(getColor(ColorChoice.DIALOG));
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
		pane.setBackground(getColor(ColorChoice.BACKGROUND));
		pane.setForeground(getColor(ColorChoice.DIALOG));
	}
	
	private static void configureOptionDialog(JOptionPane pane, JDialog dialog) {
		dialog.setIconImages(logos);
		dialog.setModal(true);
		dialog.setBackground(getColor(ColorChoice.BACKGROUND));
		dialog.setForeground(getColor(ColorChoice.DIALOG));
		dialog.setContentPane(pane);
		dialog.pack();
		dialog.setLocationRelativeTo(frame);
	}
	
	private static void openMessageDialog(String name, String title, String body, int messageType, String... options) {
		String[] split = Translate.format(body).split("\n", 2);
		body = "<b>"+split[0]+"</b><br/>"+(split.length == 2 ? split[1].replace("\n", "<br/>") : "");
		String[] formatted = Translate.format(options);
		JOptionPane pane = new JOptionPane("<html><center>"+body+"</center></html>", messageType, JOptionPane.DEFAULT_OPTION, null, formatted);
		configureOptionPane(pane);
		JDialog dialog = pane.createDialog(frame != null && frame.isVisible() ? frame : null, title);
		configureOptionDialog(pane, dialog);
		dialog.setVisible(true);
		if (name != null) {
			Object sel = pane.getValue();
			String opt;
			/*
			 * If you close a button-based JOptionPane without choosing an option, the value becomes
			 * Integer.valueOf(JOptionPane.CLOSED_OPTION). The doc does not mention this, and in
			 * fact asserts closing without choosing an option will yield null. Sigh...
			 */
			if (sel == null || sel == JOptionPane.UNINITIALIZED_VALUE || !(sel instanceof String)) {
				opt = "closed";
			} else {
				opt = (String)sel;
				for (int i = 0; i < formatted.length; i++) {
					if (opt.equals(formatted[i])) {
						opt = options[i];
						break;
					}
				}
			}
			Puppet.reportChoice(name, opt);
		}
	}

	private static void openChoiceDialog(String name, String title, String body, String def, String... options) {
		String[] split = Translate.format(body).split("\n", 2);
		body = "<b>"+split[0]+"</b><br/>"+(split.length == 2 ? split[1].replace("\n", "<br/>") : "");
		JOptionPane pane = new JOptionPane("<html><center>"+Translate.format(body)+"</center></html>", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
				new Object[]{Translate.format("option.ok")});
		pane.setWantsInput(true);
		String[] formatted = Translate.format(options);
		pane.setSelectionValues(formatted);
		pane.setInitialSelectionValue(Translate.format(def));
		configureOptionPane(pane);
		JDialog dialog = pane.createDialog(frame != null && frame.isVisible() ? frame : null, title);
		configureOptionDialog(pane, dialog);
		dialog.setVisible(true);
		if (name != null) {
			Object sel = pane.getInputValue();
			String opt;
			if (sel == null || sel == JOptionPane.UNINITIALIZED_VALUE || !(sel instanceof String)) {
				opt = "closed";
			} else {
				opt = (String)sel;
				for (int i = 0; i < formatted.length; i++) {
					if (opt.equals(formatted[i])) {
						opt = options[i];
						break;
					}
				}
			}
			Puppet.reportChoice(name, opt);
		}
	}
	
	private static void openFlavorDialog(String name, List<FlavorGroup> groups) {
		JDialog dialog = new JDialog(frame != null && frame.isVisible() ? frame : null, Translate.format("dialog.flavors.title"));
		dialog.setIconImages(logos);
		dialog.setModal(true);
		dialog.setBackground(getColor(ColorChoice.BACKGROUND));
		dialog.setForeground(getColor(ColorChoice.DIALOG));
		dialog.setLocationRelativeTo(frame);
		String descPfx = "<style>body { font-family: \"Fira Sans\"; color: #"+Integer.toHexString(getColor(ColorChoice.DIALOG).getRGB()|0xFF000000).substring(2)+"; }</style>";
		String noDesc = "<font size=\"4\" face=\"Fira Sans\" color=\"#"+Integer.toHexString(getColor(ColorChoice.SUBTITLE).getRGB()|0xFF000000).substring(2)+"\"><i>"+Translate.format("flavor.hover_notice")+"</i></font>";
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
			if (!grp.isBoolean()) {
				Box box = Box.createVerticalBox();
				JLabel title = new JLabel(grp.name);
				title.setBorder(new EmptyBorder(8,8,8,8));
				title.setFont(fontBold.deriveFont(18f));
				title.setMinimumSize(new Dimension(0, 24));
				title.setForeground(getColor(ColorChoice.DIALOG));
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
					btn.setFont(font.deriveFont(14f));
					btn.setSelected(c.def);
					if (c.def) selectedAny = true;
					btn.setMinimumSize(new Dimension(0, 32));
					btn.setPreferredSize(new Dimension(64, 32));
					btn.setMaximumSize(new Dimension(32767, 32));
					Runnable updateLook = () -> {
						if (btn.isSelected()) {
							btn.setBorder(new EmptyBorder(2,2,2,2));
							btn.setBackground(getColor(ColorChoice.BUTTON));
							btn.setForeground(getColor(ColorChoice.BUTTONTEXT));
							btn.setFont(fontBold.deriveFont(14f));
							results.add(c.id);
						} else {
							btn.setBorder(new LineBorder(getColor(ColorChoice.DIALOG), 2));
							btn.setBackground(getColor(ColorChoice.BACKGROUND));
							btn.setForeground(getColor(ColorChoice.DIALOG));
							btn.setFont(font.deriveFont(14f));
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
			if (grp.isBoolean()) {
				JCheckBox cb = new JCheckBox(grp.name);
				cb.setUI(new BasicCheckBoxUI());
				cb.setIcon(new Icon() {
					
					@Override
					public void paintIcon(Component c, Graphics g, int x, int y) {
						Graphics2D g2d = (Graphics2D)g;
						fix(g2d);
						g2d.setColor(SwingPuppet.getColor(ColorChoice.DIALOG));
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
						g2d.setColor(SwingPuppet.getColor(ColorChoice.BUTTON));
						g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
						g2d.fillOval(0, 0, 24, 24);
						g2d.setColor(SwingPuppet.getColor(ColorChoice.BUTTONTEXT));
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
				cb.setFont(font.deriveFont(18f));
				cb.setMinimumSize(new Dimension(0, 24));
				cb.setForeground(getColor(ColorChoice.DIALOG));
				cb.setBackground(getColor(ColorChoice.BACKGROUND));
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
		options.setBackground(getColor(ColorChoice.BACKGROUND));
		desc.setBackground(getColor(ColorChoice.BACKGROUND));
		desc.setForeground(getColor(ColorChoice.DIALOG));
		desc.setBorder(new EmptyBorder(8,8,8,8));
		JScrollPane scroll = new JScrollPane(options, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBackground(getColor(ColorChoice.BACKGROUND));
		scroll.setForeground(getColor(ColorChoice.DIALOG));
		JScrollBar vsb = scroll.getVerticalScrollBar();
		vsb.setSize(4, 480);
		vsb.setUnitIncrement(8);
		vsb.putClientProperty("JComponent.sizeVariant", "small");
		vsb.setUI(new BasicScrollBarUI() {
			@Override
			protected void configureScrollBarColors() {
				this.trackColor = SwingPuppet.getColor(ColorChoice.BACKGROUND);
				this.thumbColor = this.thumbDarkShadowColor = this.thumbHighlightColor = this.thumbLightShadowColor = SwingPuppet.getColor(ColorChoice.BUTTON);
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
						SwingPuppet.getColor(ColorChoice.BUTTON),
						SwingPuppet.getColor(ColorChoice.BUTTON),
						SwingPuppet.getColor(ColorChoice.BUTTON),
						SwingPuppet.getColor(ColorChoice.BUTTON)) {

					@Override
					public void paintTriangle(Graphics g, int x, int y, int size,
							int direction, boolean isEnabled) {
						Color oldColor = g.getColor();
						int mid, i, j;

						j = 0;
						size = Math.max(size, 2)+2;
						mid = (size / 2) - 1;

						g.translate(x+1, y);
						g.setColor(SwingPuppet.getColor(ColorChoice.BUTTONTEXT));

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
		vsb.setBackground(getColor(ColorChoice.BACKGROUND));
		scroll.setBorder(null);
		Box outer = Box.createVerticalBox();
		outer.add(desc);
		outer.add(Box.createVerticalStrut(8));
		desc.setAlignmentX(0);
		Box buttons = Box.createHorizontalBox();
		buttons.setAlignmentX(0);
		buttons.setMaximumSize(new Dimension(32767, 48));
		buttons.add(Box.createHorizontalGlue());
		JButton done = new JButton(Translate.format("option.done"));
		done.setBackground(getColor(ColorChoice.BUTTON));
		done.setForeground(getColor(ColorChoice.BUTTONTEXT));
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
				d.setBackground(SwingPuppet.getColor(ColorChoice.BACKGROUND));
				d.setForeground(SwingPuppet.getColor(ColorChoice.DIALOG));
				d.setBorder(null);
				return d;
			}
		});
		split.setDividerSize(4);
		split.setSize(854, 480);
		split.setPreferredSize(new Dimension(854, 480));
		split.setDividerLocation(0.4);
		split.setBackground(getColor(ColorChoice.BACKGROUND));
		split.setForeground(getColor(ColorChoice.DIALOG));
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
				Puppet.reportCloseRequest();
			}
		});
		dialog.pack();
		dialog.setVisible(true);
		if (closed[0]) return;
		StringJoiner sj = new StringJoiner("\u001C");
		for (String s : results) {
			sj.add(s);
		}
		Puppet.reportChoice(name, sj.toString());
	}
	
}
