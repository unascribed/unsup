package com.unascribed.sup;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

/**
 * LWJGL3's GLFW is incompatible with AWT. If we initialize AWT pre-launch with our GUI tidbits,
 * versions of Minecraft that use LWJGL3 won't work correctly especially on macOS. This Puppet
 * process's sole purpose of existence is to allow us to interact with AWT at an arm's length and
 * exit once we're done using it.
 */
class Puppet {
	
	static final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
	private static JFrame frame;
	private static JLabel title, subtitle;
	private static JProgressBar prog;
	
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
		
		// enable a bunch of nice things that are off by default for legacy compat
		// use OpenGL or Direct3D where supported
		System.setProperty("sun.java2d.opengl", "true");
		System.setProperty("sun.java2d.d3d", "true");
		// force font antialiasing
		System.setProperty("awt.useSystemAAFontSettings", "on");
		System.setProperty("swing.aatext", "true");
		System.setProperty("swing.useSystemFontSettings", "true");
		
		MetalLookAndFeel.setCurrentTheme(new OceanTheme());
		try {
			UIManager.setLookAndFeel(new MetalLookAndFeel());
		} catch (UnsupportedLookAndFeelException e1) {
			log("ERROR", "Failed to set LaF");
			return;
		}
		
		System.out.println("unsup puppet ready");
		
		Map<String, ScheduledFuture<?>> orders = new HashMap<>();
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		try {
			while (true) {
				String line = br.readLine();
				if (line == null) return;
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
						r = Puppet::buildUi;
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
								}
							}
						};
						break;
					}
					case "visible": {
						boolean b = Boolean.parseBoolean(arg);
						r = () -> {
							frame.setVisible(b);
						};
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
							r = () -> {
								SwingUtilities.invokeLater(() -> {
									prog.setVisible(false);
									prog.invalidate();
								});
							};
						} else if ("det".equals(arg)) {
							r = () -> {
								SwingUtilities.invokeLater(() -> {
									prog.setVisible(true);
									prog.setValue(0);
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
						r = () -> {
							SwingUtilities.invokeLater(() -> {
								prog.setValue(i);
								prog.repaint();
							});
						};
						break;
					}
					case "title": {
						r = () -> {
							SwingUtilities.invokeLater(() -> {
								title.setText("<html>"+arg+"</html>");
							});
						};
						break;
					}
					case "subtitle": {
						r = () -> {
							SwingUtilities.invokeLater(() -> {
								subtitle.setText("<html>"+arg+"</html>");
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
						}
					};
				}
				ScheduledFuture<?> future = sched.schedule(fr, delay, TimeUnit.MILLISECONDS);
				if (name != null) {
					synchronized (orders) {
						orders.put(name, future);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	private static void log(String flavor, String msg) {
		System.err.println(flavor+"|"+msg);
	}
	
	private static void buildUi() {
		frame = new JFrame("unsup v"+Agent.VERSION);
		Image logo = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup.png"));
		frame.setIconImage(logo);
		frame.setSize(384, 128);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			private boolean closeAlreadyAttempted = false;
			
			@Override
			public void windowClosing(WindowEvent e) {
				if (closeAlreadyAttempted) {
					JOptionPane pane = new JOptionPane("<html><center><b>The updater is busy and can't exit right now.</b><br/>Please wait a moment.</center></html>", JOptionPane.INFORMATION_MESSAGE);
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
					JDialog dialog = pane.createDialog(frame, "unsup is busy");
					dialog.setBackground(colorBackground);
					dialog.setForeground(colorDialog);
					dialog.setContentPane(pane);
					dialog.pack();
					dialog.setLocationRelativeTo(frame);
					dialog.setVisible(true);
				} else {
					closeAlreadyAttempted = true;
					System.out.println("closeRequested");
				}
			}
		});
		Box outer = Box.createHorizontalBox();
		outer.setOpaque(true);
		outer.setBackground(colorBackground);
		
		JThrobber throbber = new JThrobber();
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
		inner.add(subtitle);
		inner.add(Box.createVerticalGlue());
		Box inset = Box.createVerticalBox();
		prog = new JProgressBar(JProgressBar.HORIZONTAL) {
			@Override
			public void paint(Graphics g) {
				Graphics2D g2d = (Graphics2D)g;
				g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
				g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
				g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
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
	
}
