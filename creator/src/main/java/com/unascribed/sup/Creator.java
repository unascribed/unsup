package com.unascribed.sup;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.swing.Box;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.util.UIScale;
import com.unascribed.sup.json.ManifestVersion;
import com.unascribed.sup.json.OrderedVersion;
import com.unascribed.sup.json.ReportableException;
import com.unascribed.sup.json.manifest.BootstrapManifest;
import com.unascribed.sup.json.manifest.RootManifest;
import com.unascribed.sup.json.manifest.UpdateManifest;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.SyntaxError;

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

	private static JList<OrderedVersion> versions;

	private static JPanel hintOrFiles;
	
	private static String lastOpenDirectory;
	
	public static void main(String[] args) {
		Util.fixSwing();
		
		FlatUnsupDarkLaf.install();
		
		logo = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup.png"));
		logoy2k = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup-y2k.png"));
		logonostroke = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup-nostroke.png"));
		
		frame = new JFrame("unsup creator");
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
			fd.setFilenameFilter((dir, name) -> "manifest.json".equals(name));
			fd.setTitle("Select an unsup manifest");
			fd.setLocation(frame.getLocation());
			fd.setDirectory(lastOpenDirectory);
			fd.setVisible(true);
			String filePath = fd.getFile();
			if (filePath != null) {
				lastOpenDirectory = fd.getDirectory();
				File dir;
				if (lastOpenDirectory != null) {
					dir = new File(lastOpenDirectory);
				} else {
					dir = new File("");
				}
				File file = new File(dir, filePath);
				String fname = file.getName();
				try {
					RootManifest root = jkson.fromJson(jkson.load(file), RootManifest.class);
					root.validate();
					File bootstrapFile = new File(dir, "bootstrap.json");
					fname = "bootstrap.json";
					BootstrapManifest boot = jkson.fromJson(jkson.load(bootstrapFile), BootstrapManifest.class);
					boot.validate();
					List<OrderedVersion> versionsToRetrieve = new ArrayList<>();
					if (root.versions.current != null) {
						versionsToRetrieve.add(root.versions.current);
					}
					versionsToRetrieve.addAll(root.versions.history);
					List<String> warnings = new ArrayList<>();
					Map<Integer, UpdateManifest> updates = new HashMap<>();
					for (OrderedVersion v : versionsToRetrieve) {
						File updateFile = new File(dir, "versions/"+v.code+".json");
						if (!updateFile.exists()) {
							continue;
						}
						fname = "versions/"+v.code+".json";
						UpdateManifest update = jkson.fromJson(jkson.load(updateFile), UpdateManifest.class);
						update.validate();
						updates.put(v.code, update);
					}
					rootManifest = root;
					bootstrapManifest = boot;
					updateManifests.clear();
					updateManifests.putAll(updates);
					initUI();
					if (!warnings.isEmpty()) {
						StringBuilder sb = new StringBuilder();
						for (String w : warnings) {
							sb.append("<li><plaintext>");
							sb.append(w);
							sb.append("</plaintext></li>");
						}
						JOptionPane.showMessageDialog(frame, "<html><b>Warnings were encountered during pack load:</b><br/><ul>"+sb+"</ul></html>", "Load warnings", JOptionPane.WARNING_MESSAGE);
					}
				} catch (ReportableException e) {
					JOptionPane.showMessageDialog(frame, "<html><b>An error occurred while loading the pack.</b><br/>"+e.getMessage()+"</html>", "Load error", JOptionPane.ERROR_MESSAGE);
				} catch (SyntaxError e) {
					JOptionPane.showMessageDialog(frame, "<html><b>A syntax error was encountered in "+fname+".</b><br/>"
							+ "<plaintext>"+e.getLineMessage()+"</plaintext><br/>"
							+ "<plaintext>"+e.getMessage()+"</plaintext>", "Load error", JOptionPane.ERROR_MESSAGE);
				} catch (Exception e) {
					e.printStackTrace();
					JOptionPane.showMessageDialog(frame, "<html><b>An unexpected error occurred while loading the pack.</b><br/>See console output for details.</html>", "Load error", JOptionPane.ERROR_MESSAGE);
				}
			}
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
				FlatAnimatedLafChange.showSnapshot();
				disableDecor();
				try {
					UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
				} catch (Throwable e) {
				}
				updateLaf();
				FlatAnimatedLafChange.hideSnapshotWithAnimation();
			}));
		} catch (Throwable t) {
			theme.add(radioMenuItem(false, "System", null, '\0', () -> {
				FlatAnimatedLafChange.showSnapshot();
				disableDecor();
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Throwable e) {
				}
				updateLaf();
				FlatAnimatedLafChange.hideSnapshotWithAnimation();
			}));
		}
		theme.addSeparator();
		theme.add(radioMenuItem(true, "2020 (Flat Dark)", null, '\0', () -> {
			FlatAnimatedLafChange.showSnapshot();
			enableDecor();
			FlatUnsupDarkLaf.install();
			updateLaf();
			FlatAnimatedLafChange.hideSnapshotWithAnimation();
		}));
		theme.add(radioMenuItem(false, "2020 (Flat Light)", null, '\0', () -> {
			FlatAnimatedLafChange.showSnapshot();
			enableDecor();
			FlatUnsupLightLaf.install();
			updateLaf();
			FlatAnimatedLafChange.hideSnapshotWithAnimation();
		}));
		theme.add(radioMenuItem(false, "2008 (Nimbus)", null, '\0', () -> {
			FlatAnimatedLafChange.showSnapshot();
			disableDecor();
			try {
				UIManager.setLookAndFeel(new NimbusLookAndFeel());
			} catch (UnsupportedLookAndFeelException e) {
			}
			updateLaf();
			FlatAnimatedLafChange.hideSnapshotWithAnimation();
		}));
		theme.add(radioMenuItem(false, "2004 (Ocean)", null, '\0', () -> {
			FlatAnimatedLafChange.showSnapshot();
			enableDecor();
			MetalLookAndFeel.setCurrentTheme(new OceanTheme());
			try {
				UIManager.setLookAndFeel(new MetalLookAndFeel());
			} catch (UnsupportedLookAndFeelException e) {
			}
			updateLaf();
			FlatAnimatedLafChange.hideSnapshotWithAnimation();
		}));
		theme.add(radioMenuItem(false, "1998 (Steel)", null, '\0', () -> {
			FlatAnimatedLafChange.showSnapshot();
			enableDecor();
			MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
			try {
				UIManager.setLookAndFeel(new MetalLookAndFeel());
			} catch (UnsupportedLookAndFeelException e) {
			}
			updateLaf();
			FlatAnimatedLafChange.hideSnapshotWithAnimation();
		}));
		try {
			Class.forName("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
			theme.add(radioMenuItem(false, "1996 (Motif)", null, '\0', () -> {
				FlatAnimatedLafChange.showSnapshot();
				disableDecor();
				try {
					UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
				} catch (Throwable e) {
				}
				updateLaf();
				FlatAnimatedLafChange.hideSnapshotWithAnimation();
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
					+ "Powered by Java and Swing. Some icons from <a href='https://icons8.com'>Icons8</a>.<br/><br/>"
					+ "This program comes with ABSOLUTELY NO WARRANTY.<br/>"
					+ "See the GNU GPLv3 Section 15 for details."
					+ "</font></center></html>");
			about.setFont(img.getFont());
			about.setBackground(img.getBackground());
			int eight = UIScale.scale(8);
			about.setBorder(new EmptyBorder(eight, eight, eight, eight));
			about.addHyperlinkListener(e -> {
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
						gpl.addHyperlinkListener(e2 -> {
							if (e2.getEventType() == EventType.ACTIVATED) {
								try {
									Desktop.getDesktop().browse(e2.getURL().toURI());
								} catch (IOException e11) {
									e11.printStackTrace();
								} catch (URISyntaxException e12) {
									e12.printStackTrace();
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
						} catch (IOException e11) {
							e11.printStackTrace();
						} catch (URISyntaxException e12) {
							e12.printStackTrace();
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
		DefaultListSelectionModel sm = new DefaultListSelectionModel();
		sm.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
		versions.addListSelectionListener(new ListSelectionListener() {
			
			private int lastSelection = 0;
			
			@Override
			public void valueChanged(ListSelectionEvent e) {
				int sel = versions.getSelectedIndex();
				if (!versions.getCellRenderer().getListCellRendererComponent(versions, versions.getModel().getElementAt(sel), sel,
						false, false).isEnabled()) {
					versions.setSelectedIndex(lastSelection);
				} else {
					lastSelection = sel;
				}
			}
		});
		versions.setSelectionModel(sm);
		versions.setCellRenderer(new ListCellRenderer<OrderedVersion>() {
			
			private final Box box;
			private final JLabel title, subtitle, alertIcon, bootIcon;
			private final JComponent[] allComponents;
			{
				box = Box.createVerticalBox();
				JComponent border = new JComponent() {
					@Override
					public void paint(Graphics g) {
						g.clearRect(0, 0, getWidth(), getHeight());
						g.setColor(new Color(0xAA000000, true));
						g.fillRect(0, 0, getWidth(), getHeight());
					}
				};
				border.setMinimumSize(new Dimension(1, 1));
				border.setPreferredSize(new Dimension(20, 1));
				border.setMaximumSize(new Dimension(32767, 1));
				Box inner = Box.createVerticalBox();
				int four = UIScale.scale(4);
				inner.setBorder(new EmptyBorder(four,four,four,four));
				
				Box horz = Box.createHorizontalBox();
				title = new JLabel();
				subtitle = new JLabel();
				alertIcon = new JLabel();
				bootIcon = new JLabel();
				Box horz2 = Box.createHorizontalBox();
				
				MenuImageIcon alert = new MenuImageIcon("icons/alert.png");
				alertIcon.setIcon(alert);
				alertIcon.setDisabledIcon(alert.withAlpha(0.7f));
				
				MenuImageIcon boot = new MenuImageIcon("icons/boot.png");
				bootIcon.setIcon(boot);
				bootIcon.setDisabledIcon(boot.withAlpha(0.7f));
				
				subtitle.setMaximumSize(new Dimension(32767, 32767));
				subtitle.putClientProperty("unsup.fontSizeMult", 0.8f);
				subtitle.putClientProperty("unsup.bold", true);
				
				title.setMinimumSize(new Dimension(1, UIScale.scale(18)));
				title.setAlignmentY(0.5f);
				title.setVerticalAlignment(SwingConstants.CENTER);
				
				box.add(border);
				horz.add(title);
				horz.add(Box.createHorizontalGlue());
				horz.add(alertIcon);
				horz.add(bootIcon);
				inner.add(horz);
				horz2.add(subtitle);
				horz2.add(Box.createHorizontalGlue());
				inner.add(horz2);
				box.add(inner);
				allComponents = new JComponent[]{
						title, subtitle, alertIcon, bootIcon
				};
			}
			
			@Override
			public Component getListCellRendererComponent(JList<? extends OrderedVersion> list, OrderedVersion v,
					int index, boolean isSelected, boolean cellHasFocus) {
				box.setOpaque(true);
				// asking the UIManager directly because sometimes the JList's colors get stuck when
				// switching between various LaFs (might be Nimbus' fault?)
				box.setBackground(isSelected ? UIManager.getColor("List.selectionBackground") : UIManager.getColor("List.background"));
				box.setForeground(isSelected ? UIManager.getColor("List.selectionForeground") : UIManager.getColor("List.foreground"));
				box.setBorder(null);
				box.setFont(UIManager.getFont("Label.font"));
				box.setToolTipText(null);
				box.setEnabled(true);
				subtitle.setText(" ");
				alertIcon.setVisible(false);
				bootIcon.setVisible(false);
				List<String> tooltip = new ArrayList<>();
				if (v == null) {
					title.setText("(current)");
				} else if (updateManifests.containsKey(v.code)) {
					if (bootstrapManifest != null && bootstrapManifest.version != null && v.code == bootstrapManifest.version.code) {
						bootIcon.setVisible(true);
						tooltip.add("Bootstrap version; new installs will use this version.");
					}
					title.setText(v.toString());
					UpdateManifest um = updateManifests.get(v.code);
					int additions = 0;
					int changes = 0;
					int removals = 0;
					for (UpdateManifest.Change c : um.changes) {
						if (c.from_hash == null) {
							additions++;
						} else if (c.to_hash == null) {
							removals++;
						} else {
							changes++;
						}
					}
					char ad = '+';
					char ch = '~';
					char rm = '-';
					tooltip.add(additions+" addition"+s(additions)+" ("+ad+"), "+changes+" change"+s(changes)+" ("+ch+"), "+removals+" removal"+s(removals)+" ("+rm+")");
					int total = additions+changes+removals;
					if (total > 20) {
						additions = (int)((additions/((double)total))*20);
						changes = (int)((changes/((double)total))*20);
						removals = (int)((removals/((double)total))*20);
					}
					StringBuilder adStr = new StringBuilder(additions);
					StringBuilder chStr = new StringBuilder(changes);
					StringBuilder rmStr = new StringBuilder(removals);
					for (int i = 0; i < additions; i++) adStr.append(ad);
					for (int i = 0; i < changes; i++) chStr.append(ch);
					for (int i = 0; i < removals; i++) rmStr.append(rm);
					// TODO this doesn't work on light themes
					subtitle.setText("<html><font face=Monospaced size=5><font color=lime>"+adStr+"</font><font color=yellow>"+chStr+"</font><font color=red>"+rmStr+"</font></font></html>");
				} else {
					title.setText(v.toString());
					alertIcon.setVisible(true);
					subtitle.setText("UPDATE DATA MISSING");
					box.setEnabled(false);
					tooltip.add("Update manifest (versions/"+v.code+".json) is missing.");
				}
				if (!tooltip.isEmpty()) {
					StringBuilder tooltipBldr = new StringBuilder();
					for (String s : tooltip) {
						tooltipBldr.append(s);
						tooltipBldr.append("\n");
					}
					tooltipBldr.deleteCharAt(tooltipBldr.length()-1);
					box.setToolTipText(tooltipBldr.toString());
				}
				for (JComponent c : allComponents) {
					c.setForeground(box.getForeground());
					c.setEnabled(box.isEnabled());
					Font f = box.getFont();
					if (c.getClientProperty("unsup.fontSizeMult") != null) {
						f = f.deriveFont(box.getFont().getSize2D()*((Number)c.getClientProperty("unsup.fontSizeMult")).floatValue());
					}
					if (c.getClientProperty("unsup.bold") != null) {
						f = f.deriveFont(Font.BOLD);
					}
					c.setFont(f);
				}
				box.validate();
				return box;
			}
		});
		
		cardLayout = new CardLayout();
		hintOrFiles = new JPanel(cardLayout);
		
		files = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
		
		JLabel hint = new JLabel("<html>"
				+ "<center>"
				+ "<b>Welcome to the unsup creator!</b><br/>"
				+ "A list of files and changes will appear here.<br/>"
				+ "<br/>"
				+ "To get started, open an existing unsup manifest or create a new one from the Pack menu."
				+ "</center>"
				+ "</html>") {
			{
				setOpaque(true);
			}
			@Override
			public void updateUI() {
				super.updateUI();
				setBackground(UIManager.getColor("List.background"));
			}
		};
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
	
	private static String s(int i) {
		return i == 1 ? "" : "s";
	}

	private static void initUI() {
		System.out.println("rootManifest="+rootManifest);
		System.out.println("bootstrapManifest="+bootstrapManifest);
		System.out.println("updateManifests="+updateManifests);
		Vector<OrderedVersion> verVec = new Vector<>(rootManifest.versions.history.size()+1);
		if (rootManifest.versions.current != null) {
			verVec.add(rootManifest.versions.current);
		}
		verVec.addAll(rootManifest.versions.history);
		for (OrderedVersion v : verVec) {
			versionNames.put(v.code, v.name);
		}
		Collections.sort(verVec, Comparator.reverseOrder());
		verVec.add(0, null);
		
		versions.setListData(verVec);
		versions.setSelectedIndex(0);
		
		cardLayout.show(hintOrFiles, "files");
		frame.setTitle(rootManifest.name+" - unsup creator");
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

