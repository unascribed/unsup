package com.unascribed.sup;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import com.unascribed.sup.json.ManifestVersion;
import com.unascribed.sup.json.OrderedVersion;
import com.unascribed.sup.json.manifest.BootstrapManifest;
import com.unascribed.sup.json.manifest.RootManifest;
import com.unascribed.sup.json.manifest.UpdateManifest;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonPrimitive;

public class Creator {

	public static final String VERSION = "0.0.1";
	
	private static final HashFunction DEFAULT_HASH_FUNCTION = HashFunction.SHA2_256;
	
	public static final Jankson jkson = Jankson.builder()
			.registerDeserializer(String.class, ManifestVersion.class, (s, m) -> ManifestVersion.parse(s))
			.registerSerializer(ManifestVersion.class, (v, m) -> new JsonPrimitive(v.toString()))
			.registerDeserializer(String.class, HashFunction.class, (s, m) -> HashFunction.byName(s))
			.registerSerializer(HashFunction.class, (hf, m) -> new JsonPrimitive(hf.name))
			.build();
	
	private static JFrame frame;
	private static Image logo, logoy2k, logonostroke;
	private static ImageIcon logoIcon;
	private static JCheckBoxMenuItem decoration;
	
	private static RootManifest rootManifest;
	private static BootstrapManifest bootstrapManifest;
	private static final SortedMap<Integer, UpdateManifest> updateManifests = new TreeMap<>();
	private static final Map<Integer, String> versionNames = new HashMap<>();

	private static CardLayout cardLayout;

	private static JTree files;

	private static JList<String> versions;

	private static JPanel hintOrFiles;
	
	public static void main(String[] args) {
		Util.fixSwing();
		
		FlatDarkLaf.install();
		
		logo = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup.png"));
		logoy2k = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup-y2k.png"));
		logonostroke = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup-nostroke.png"));
		
		frame = new JFrame("unsup Creator");
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		int[] sizes = { 16, 24, 32, 64, 128 };
		List<Image> iconImages = new ArrayList<>();
		for (int s : sizes) {
			iconImages.add(logo.getScaledInstance(s, s, Image.SCALE_SMOOTH));
		}
		iconImages.add(logo);
		frame.setIconImages(iconImages);
		
		BooleanSupplier packOpen = () -> rootManifest != null;
		
		JMenuBar jmb = new JMenuBar();
		JMenu pack = new JMenu("Pack");
		pack.add(menuItem("New", "icons/new.png", 'n', () -> {
			rootManifest = RootManifest.create();
			rootManifest.name = "New Pack";
			bootstrapManifest = BootstrapManifest.create();
			bootstrapManifest.hash_function = DEFAULT_HASH_FUNCTION;
			initUI();
		}, "Create an empty pack with no versions."));
		pack.addSeparator();
		pack.add(menuItem("Open…", "icons/open.png", 'o', () -> {
			FileDialog fd = new FileDialog(frame);
			fd.setFilenameFilter(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					return "manifest.json".equals(name);
				}
			});
			fd.setTitle("Select an unsup manifest");
			fd.setLocationByPlatform(true);
			fd.setVisible(true);
		}, "Open an existing pack."));
		pack.add(menuItem("Import…", "icons/import.png", 'i', () -> {
			
		}, "Import an arbitrary directory into a pack, creating a new pack if necessary."));
		pack.addSeparator();
		addWithEnableCheck(pack, menuItem("Save", "icons/save.png", 's', () -> {
			
		}, "Save changes to the currently open pack."), packOpen);
		addWithEnableCheck(pack, menuItem("Export…", "icons/export.png", 'e', () -> {
			
		}, "Export a resolved copy of the contents of this pack to a directory."), packOpen);
		pack.addSeparator();
		addWithEnableCheck(pack, menuItem("Publish…", "icons/publish.png", 'p', () -> {
			
		}, "Upload this pack to a server."), packOpen);
		jmb.add(pack);
		JMenu options = new JMenu("Meta");
		JMenu theme = new JMenu("Theme");
		theme.setIcon(new MenuImageIcon("icons/bucket.png"));
		theme.add(decoration = checkMenuItem(true, "System Decorations", null, '\0', () -> {
			JFrame.setDefaultLookAndFeelDecorated(!decoration.isSelected());
			JDialog.setDefaultLookAndFeelDecorated(!decoration.isSelected());
			updateDecor();
			updateLaf();
		}));
		try {
			Class.forName("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
			theme.add(radioMenuItem(false, "GTK+ (buggy!)", null, '\0', () -> {
				disableDecor();
				try {
					UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
				} catch (Throwable e) {
				}
				updateLaf();
			}));
		} catch (Throwable t) {
			theme.add(radioMenuItem(false, "System", null, '\0', () -> {
				disableDecor();
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Throwable e) {
				}
				updateLaf();
			}));
		}
		theme.addSeparator();
		theme.add(radioMenuItem(true, "2020 (Flat Dark)", null, '\0', () -> {
			enableDecor();
			FlatDarkLaf.install();
			updateLaf();
		}));
		theme.add(radioMenuItem(false, "2020 (Flat Light)", null, '\0', () -> {
			enableDecor();
			FlatLightLaf.install();
			updateLaf();
		}));
		theme.add(radioMenuItem(false, "2008 (Nimbus)", null, '\0', () -> {
			disableDecor();
			try {
				UIManager.setLookAndFeel(new NimbusLookAndFeel());
			} catch (UnsupportedLookAndFeelException e) {
			}
			updateLaf();
		}));
		theme.add(radioMenuItem(false, "2004 (Ocean)", null, '\0', () -> {
			enableDecor();
			MetalLookAndFeel.setCurrentTheme(new OceanTheme());
			try {
				UIManager.setLookAndFeel(new MetalLookAndFeel());
			} catch (UnsupportedLookAndFeelException e) {
			}
			updateLaf();
		}));
		theme.add(radioMenuItem(false, "1998 (Steel)", null, '\0', () -> {
			enableDecor();
			MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
			try {
				UIManager.setLookAndFeel(new MetalLookAndFeel());
			} catch (UnsupportedLookAndFeelException e) {
			}
			updateLaf();
		}));
		try {
			Class.forName("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
			theme.add(radioMenuItem(false, "1996 (Motif)", null, '\0', () -> {
				disableDecor();
				try {
					UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
				} catch (Throwable e) {
				}
				updateLaf();
			}));
		} catch (Throwable t) {}
		options.add(theme);
		options.add(menuItem("About", "icons/about.png", '\0', () -> {
			JDialog dialog = new JDialog(frame, "About");
			if (!decoration.isSelected()) {
				dialog.setUndecorated(true);
				dialog.getRootPane().setWindowDecorationStyle(JRootPane.INFORMATION_DIALOG);
			}
			Box box = Box.createVerticalBox();
			Box box2 = Box.createHorizontalBox();
			JLabel img = new JLabel();
			img.setIcon(new ImageIcon(logo.getScaledInstance(UIScale.scale(64), UIScale.scale(64), Image.SCALE_SMOOTH)));
			box2.add(Box.createHorizontalGlue());
			box2.add(img);
			box2.add(Box.createHorizontalGlue());
			box.add(box2);
			// The Revenge of HotJava
			JEditorPane about = new JEditorPane();
			about.setEditable(false);
			about.setContentType("text/html");
			about.setText("<html><center><font face='Dialog'>"
					+ "<b>unsup creator v"+VERSION+"</b><br/>"
					+ "Copyright &copy; 2020 Una Thompson<br/>"
					+ "Released under the <a href='#gplv3'>GNU GPLv3</a> or later.<br/><br/>"
					+ "Uses <a href='https://github.com/falkreon/Jankson'>Jankson</a>, "
					+ "<a href='https://github.com/str4d/ed25519-java/'>EdDSA</a>, "
					+ "<a href='https://github.com/JFormDesigner/FlatLaf'>FlatLaF</a>, "
					+ "and <a href='https://github.com/rclone/rclone'>rclone</a>.<br/>"
					+ "Powered by Java and Swing.<br/><br/>"
					+ "This program comes with ABSOLUTELY NO WARRANTY.<br/>"
					+ "See the GNU GPLv3 Section 15 for details."
					+ "</font></center></html>");
			about.setFont(img.getFont());
			about.setBackground(img.getBackground());
			int eight = UIScale.scale(8);
			about.setBorder(new EmptyBorder(eight, eight, eight, eight));
			about.addHyperlinkListener(new HyperlinkListener() {
				
				@Override
				public void hyperlinkUpdate(HyperlinkEvent e) {
					if (e.getEventType() == EventType.ACTIVATED) {
						if (e.getDescription().equals("#gplv3")) {
							JDialog gplDialog = new JDialog(dialog, "GNU General Public License v3.0");
							if (!decoration.isSelected()) {
								gplDialog.setUndecorated(true);
								gplDialog.getRootPane().setWindowDecorationStyle(JRootPane.INFORMATION_DIALOG);
							}
							JEditorPane gpl = new JEditorPane();
							gpl.setEditable(false);
							gpl.setContentType("text/html");
							try {
								gpl.setPage(ClassLoader.getSystemResource("gpl3.html"));
							} catch (IOException e2) {
								e2.printStackTrace();
							}
							gpl.setFont(img.getFont());
							gpl.setBackground(img.getBackground());
							gpl.addHyperlinkListener(new HyperlinkListener() {
								
								@Override
								public void hyperlinkUpdate(HyperlinkEvent e) {
									if (e.getEventType() == EventType.ACTIVATED) {
										try {
											Desktop.getDesktop().browse(e.getURL().toURI());
										} catch (IOException e1) {
											e1.printStackTrace();
										} catch (URISyntaxException e1) {
											e1.printStackTrace();
										}
									}
								}
							});
							gplDialog.setContentPane(new JScrollPane(gpl, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
							gplDialog.setSize(UIScale.scale(800), UIScale.scale(600));
							gplDialog.setLocationRelativeTo(dialog);
							gplDialog.setVisible(true);
						} else {
							try {
								Desktop.getDesktop().browse(e.getURL().toURI());
							} catch (IOException e1) {
								e1.printStackTrace();
							} catch (URISyntaxException e1) {
								e1.printStackTrace();
							}
						}
					}
				}
			});
			box.add(about);
			dialog.setContentPane(box);
			dialog.setSize(UIScale.scale(400), UIScale.scale(280));
			dialog.setLocationRelativeTo(frame);
			dialog.setVisible(true);
		}, "Display an About dialog."));
		options.add(menuItem("Quit", "icons/close.png", 'q', () -> {
			System.exit(0);
		}, "Exit the Creator."));
		jmb.add(options);
		
		versions = new JList<>();
		
		cardLayout = new CardLayout();
		hintOrFiles = new JPanel(cardLayout);
		
		files = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
		
		JLabel hint = new JLabel("<html><center><b>Welcome to the unsup creator!</b><br/>A list of files and changes will appear here.<br/><br/>To get started, open an existing unsup manifest or create a new one from the Pack menu.</center></html>");
		hint.setIcon(logoIcon = new ImageIcon(logo.getScaledInstance(UIScale.scale(64), UIScale.scale(64), Image.SCALE_SMOOTH)));
		hint.setHorizontalTextPosition(SwingConstants.CENTER);
		hint.setVerticalTextPosition(SwingConstants.BOTTOM);
		hint.setHorizontalAlignment(SwingConstants.CENTER);
		hint.setVerticalAlignment(SwingConstants.CENTER);
		
		hintOrFiles.add(files, "files");
		hintOrFiles.add(hint, "hint");
		
		cardLayout.show(hintOrFiles, "hint");
		
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, versions, hintOrFiles);
		
		split.setDividerLocation(UIScale.scale(192));
		
		frame.setSize(UIScale.scale(960), UIScale.scale(480));
		frame.setContentPane(split);
		frame.setJMenuBar(jmb);
		
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
	
	private static void initUI() {
		if (rootManifest.versions.current != null) {
			OrderedVersion cur = rootManifest.versions.current;
			versionNames.put(cur.code, cur.name);
		}
		for (OrderedVersion v : rootManifest.versions.history) {
			versionNames.put(v.code, v.name);
		}
		cardLayout.show(hintOrFiles, "files");
	}

	private static void disableDecor() {
		if (!decoration.isSelected()) {
			decoration.setSelected(true);
			updateDecor();
		}
		decoration.setEnabled(false);
	}
	
	private static void enableDecor() {
		decoration.setEnabled(true);
	}

	private static void updateDecor() {
		for (Window w : Window.getWindows()) {
			boolean makeVisible = false;
			if (w.isDisplayable()) {
				w.dispose();
				makeVisible = true;
			}
			if (w instanceof JFrame) {
				((JFrame)w).setUndecorated(!decoration.isSelected());
				((JFrame)w).getRootPane().setWindowDecorationStyle(decoration.isSelected() ? JRootPane.NONE : JRootPane.FRAME);
			} else if (w instanceof JDialog) {
				((JDialog)w).setUndecorated(!decoration.isSelected());
				((JDialog)w).getRootPane().setWindowDecorationStyle(decoration.isSelected() ? JRootPane.NONE : JRootPane.PLAIN_DIALOG);
			}
			if (makeVisible) {
				w.setVisible(true);
			}
		}
		updateLaf();
	}
	
	private static void updateLaf() {
		String nm = UIManager.getLookAndFeel().getClass().getName();
		int sixtyFour = UIScale.scale(64);
		if (nm.contains("Metal") && MetalLookAndFeel.getCurrentTheme().getClass() == DefaultMetalTheme.class) {
			logoIcon.setImage(logonostroke.getScaledInstance(sixtyFour, sixtyFour, Image.SCALE_FAST));
		} else if (nm.contains("motif")) {
			logoIcon.setImage(logonostroke.getScaledInstance(sixtyFour-9, sixtyFour-9, Image.SCALE_FAST)
					.getScaledInstance(sixtyFour, sixtyFour, Image.SCALE_FAST));
		} else if (nm.contains("nimbus") || nm.contains("Metal")) {
			logoIcon.setImage(logoy2k.getScaledInstance(sixtyFour, sixtyFour, Image.SCALE_SMOOTH));
		} else {
			logoIcon.setImage(logo.getScaledInstance(sixtyFour, sixtyFour, Image.SCALE_SMOOTH));
		}
		for (Window w : Window.getWindows()) {
			SwingUtilities.updateComponentTreeUI(w);
		}
	}

	private static void addWithEnableCheck(JMenu menu, JMenuItem item, BooleanSupplier enableCheck) {
		menu.add(item);
		menu.addMenuListener(new MenuListener() {
			
			@Override
			public void menuSelected(MenuEvent e) {
				item.setEnabled(enableCheck.getAsBoolean());
			}
			
			@Override
			public void menuDeselected(MenuEvent e) {}
			@Override
			public void menuCanceled(MenuEvent e) {}
		});
	}
	
	private static JMenuItem menuItem(String name, String icon, char c, Runnable r) {
		return menuItem(JMenuItem::new, name, icon, c, r, null);
	}
	
	private static JMenuItem menuItem(String name, String icon, char c, Runnable r, String tooltip) {
		return menuItem(JMenuItem::new, name, icon, c, r, tooltip);
	}
	
	private static JRadioButtonMenuItem radioMenuItem(boolean selected, String name, String icon, char c, Runnable r) {
		return menuItem(n -> new JRadioButtonMenuItem(name, selected), name, icon, c, r, null);
	}
	
	private static JRadioButtonMenuItem radioMenuItem(boolean selected, String name, String icon, char c, Runnable r, String tooltip) {
		return menuItem(n -> new JRadioButtonMenuItem(name, selected), name, icon, c, r, tooltip);
	}
	
	private static JCheckBoxMenuItem checkMenuItem(boolean selected, String name, String icon, char c, Runnable r) {
		return menuItem(n -> new JCheckBoxMenuItem(name, selected), name, icon, c, r, null);
	}
	
	private static JCheckBoxMenuItem checkMenuItem(boolean selected, String name, String icon, char c, Runnable r, String tooltip) {
		return menuItem(n -> new JCheckBoxMenuItem(name, selected), name, icon, c, r, tooltip);
	}
	
	private static <T extends JMenuItem> T menuItem(Function<String, T> cons, String name, String icon, char c, Runnable r) {
		return menuItem(cons, name, icon, c, r, null);
	}
	
	private static <T extends JMenuItem> T menuItem(Function<String, T> cons, String name, String icon, char c, Runnable r, String tooltip) {
		Toolkit tk = Toolkit.getDefaultToolkit();
		T item = cons.apply(name);
		if (icon != null) {
			MenuImageIcon ic = new MenuImageIcon(icon);
			item.setIcon(ic);
			item.setDisabledIcon(ic.withAlpha(0.7f));
		}
		if (tooltip != null) {
			item.setToolTipText(tooltip);
		}
		if (c != '\0') {
			int ev = KeyEvent.getExtendedKeyCodeForChar(c);
			item.setMnemonic(ev);
			int mask = tk.getMenuShortcutKeyMaskEx();
			if (Character.isUpperCase(c)) {
				mask |= KeyEvent.SHIFT_DOWN_MASK;
			}
			item.setAccelerator(KeyStroke.getKeyStroke(ev, mask));
		}
		if (item instanceof JRadioButtonMenuItem) {
			item.addActionListener(a -> {
				Container parent = item.getParent();
				if (parent != null) {
					for (int i = 0; i < parent.getComponentCount(); i++) {
						Component sibling = parent.getComponent(i);
						if (sibling == item) continue;
						if (sibling instanceof JRadioButtonMenuItem) {
							((JRadioButtonMenuItem)sibling).setSelected(false);
						}
					}
				}
			});
		}
		item.addActionListener(a -> r.run());
		return item;
	}
	
}

