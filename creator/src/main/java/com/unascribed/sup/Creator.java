package com.unascribed.sup;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.swing.*;
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
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.util.UIScale;
import com.unascribed.sup.data.HashFunction;
import com.unascribed.sup.json.ManifestVersion;
import com.unascribed.sup.json.Marshallable;
import com.unascribed.sup.json.OrderedVersion;
import com.unascribed.sup.json.ReportableException;
import com.unascribed.sup.json.manifest.BootstrapManifest;
import com.unascribed.sup.json.manifest.RootManifest;
import com.unascribed.sup.json.manifest.UpdateManifest;
import com.unascribed.sup.util.SwingHelper;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonArray;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.JsonNull;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.SyntaxError;
import static org.lwjgl.util.nfd.NativeFileDialog.*;

public class Creator {

	public static class State {
		public RootManifest rootManifest;
		public BootstrapManifest bootstrapManifest;
		public final SortedMap<Integer, UpdateManifest> updateManifests = new TreeMap<>();
		public final Map<Integer, String> versionNames = new HashMap<>();
		public boolean dirty;
		public long firstChangeSinceLastSave = -1;
		
		// values only used upon undo/redo
		public int selectedVersionIdx;
		public String changeTitle;
		
		public State copy() {
			// TODO optimize; copyViaJson is a quick hack and inefficient
			State nw = new State();
			nw.rootManifest = copyViaJson(rootManifest);
			nw.bootstrapManifest = copyViaJson(bootstrapManifest);
			for (Map.Entry<Integer, UpdateManifest> en : updateManifests.entrySet()) {
				nw.updateManifests.put(en.getKey(), copyViaJson(en.getValue()));
			}
			nw.versionNames.putAll(versionNames);
			nw.dirty = dirty;
			nw.firstChangeSinceLastSave = firstChangeSinceLastSave;
			nw.selectedVersionIdx = selectedVersionIdx;
			return nw;
		}

		private static <T> T copyViaJson(T obj) {
			if (obj == null || obj == JsonNull.INSTANCE) return obj;
			return (T) jkson.fromJson((JsonObject)jkson.toJson(obj), obj.getClass());
		}
	}
	
	public static final String VERSION = "0.0.1";
	
	public static final HashFunction DEFAULT_HASH_FUNCTION = HashFunction.SHA2_256;
	
	public static final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
	
	public static final Jankson jkson = Jankson.builder()
			.registerDeserializer(String.class, ManifestVersion.class, (s, m) -> ManifestVersion.parse(s))
			.registerSerializer(ManifestVersion.class, (v, m) -> new JsonPrimitive(v.toString()))
			.registerDeserializer(String.class, HashFunction.class, (s, m) -> HashFunction.byName(s))
			.registerSerializer(HashFunction.class, (hf, m) -> new JsonPrimitive(hf.name))
			.registerSerializer(Marshallable.class, (o, m) -> o.serialize(m))
			.build();
	
	public static JFrame frame;
	private static Image logo, logoy2k, logonostroke;
	private static ImageIcon logoIcon;
	private static JCheckBoxMenuItem decoration;
	
	private static File origin;
	private static final List<State> undo = new ArrayList<>();
	public static State state = new State();
	private static final List<State> redo = new ArrayList<>();

	private static CardLayout cardLayout;

	private static JTree files;

	private static JList<OrderedVersion> versions;

	private static JPanel hintOrFiles;
	
	public static String lastOpenDirectory;
	
	private static JMenuItem undoItem;
	private static JMenuItem redoItem;
	
	public static void main(String[] args) {
		SwingHelper.fixSwing();
		
		FlatUnsupDarkLaf.install();
		
		logo = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup.png"));
		logoy2k = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup-y2k.png"));
		logonostroke = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource("unsup-nostroke.png"));
		
		frame = new JFrame("unsup creator");
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		int[] sizes = { 16, 24, 32, 64, 128 };
		List<Image> iconImages = new ArrayList<>();
		for (int s : sizes) {
			iconImages.add(logo.getScaledInstance(s, s, Image.SCALE_SMOOTH));
		}
		iconImages.add(logo);
		frame.setIconImages(iconImages);
		
		BooleanSupplier packOpen = () -> state.rootManifest != null;
		
		JMenuBar jmb = new JMenuBar();
		JMenu packMenu = new JMenu("Pack");
		packMenu.add(menuItem("New", "icons/new-pack.png", 'n', () -> {
			if (!promptDestructiveIfDirty()) return;
			createDefaultManifests("New Untitled Pack");
			initUI();
		}, "Create an empty pack with no versions."));
		addWithEnableCheck(packMenu, menuItem("Close", "icons/pack-close.png", 'w', () -> {
			if (!promptDestructiveIfDirty()) return;
			origin = null;
			markClean();
			deinitUI();
		}, "Close the currently open pack."), packOpen);
		packMenu.addSeparator();
		packMenu.add(menuItem("Open…", "icons/open-pack.png", 'o', () -> {
			if (!promptDestructiveIfDirty()) return;
			String filePath;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer pb = stack.mallocPointer(1);
				int res = NFD_OpenDialog("json", lastOpenDirectory, pb);
				if (res == NFD_OKAY) {
					filePath = pb.getStringUTF8(0);
				} else if (res == NFD_CANCEL) {
					return;
				} else {
					JOptionPane.showMessageDialog(frame, "NFD internal error: "+NFD_GetError(), "Unexpected error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				nNFD_Free(pb.get());
			}
			if (filePath != null) {
				File file = new File(filePath);
				if (!file.getName().equals("manifest.json")) {
					JOptionPane.showMessageDialog(frame, "This file doesn't look like an unsup manifest; it's not named manifest.json.", "Load error", JOptionPane.WARNING_MESSAGE);
					return;
				}
				File dir = file.getParentFile();
				lastOpenDirectory = dir.getAbsolutePath();
				String fname = file.getName();
				try {
					RootManifest root = jkson.fromJson(jkson.load(file), RootManifest.class);
					root.validate();
					File bootstrapFile = new File(dir, "bootstrap.json");
					BootstrapManifest boot = null;
					if (bootstrapFile.exists()) {
						fname = "bootstrap.json";
						boot = jkson.fromJson(jkson.load(bootstrapFile), BootstrapManifest.class);
						boot.validate();
					}
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
					undo.clear();
					redo.clear();
					origin = file;
					state.rootManifest = root;
					state.bootstrapManifest = boot;
					state.updateManifests.clear();
					state.updateManifests.putAll(updates);
					markClean();
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
		packMenu.add(menuItem("Import…", "icons/import-pack.png", 'i', () -> {
			PackImportHandler.invoke();
		}, "Import an arbitrary directory into a pack, creating a new pack if necessary."));
		packMenu.addSeparator();
		addWithEnableCheck(packMenu, menuItem("Save", "icons/save.png", 's', () -> {
			save(false);
		}, "Save changes to the currently open pack."), packOpen);
		addWithEnableCheck(packMenu, menuItem("Save As…", "icons/save-as.png", 'S', () -> {
			File origOrigin = origin;
			origin = null;
			if (!save(true)) {
				origin = origOrigin;
			}
		}, "Save a copy of the currently open pack somewhere else."), packOpen);
		addWithEnableCheck(packMenu, menuItem("Export…", "icons/export-pack.png", 'e', () -> {
			
		}, "Export a resolved copy of the contents of this pack to a directory."), packOpen);
		packMenu.addSeparator();
		addWithEnableCheck(packMenu, menuItem("Publish…", "icons/publish.png", 'p', () -> {
			
		}, "Upload this pack to a server."), packOpen);
		addWithEnableCheck(packMenu, menuItem("Options…", "icons/pack-options.png", 'O', () -> {
			JDialog d = new JDialog(frame, "Pack options");
			Box box = Box.createVerticalBox();
			int four = UIScale.scale(4);
			box.setBorder(new EmptyBorder(four,four,four,four));
			Box title = Box.createHorizontalBox();
			JLabel label = new JLabel("Pack name");
			title.add(label);
			title.add(Box.createHorizontalGlue());
			box.add(title);
			JButton done = new JButton("Save");
			JTextField field = new JTextField(state.rootManifest.name);
			field.getDocument().addDocumentListener((SimpleDocumentListener)(e) -> {
				done.setEnabled(!field.getText().isEmpty());
			});
			field.setMaximumSize(new Dimension(32767, field.getPreferredSize().height));
			box.add(field);
			box.add(Box.createVerticalGlue());
			box.add(Box.createVerticalStrut(UIScale.scale(8)));
			Box bottom = Box.createHorizontalBox();
			box.add(bottom);
			bottom.add(Box.createHorizontalGlue());
			JButton cancel = new JButton("Cancel");
			cancel.addActionListener((a) -> {
				d.dispose();
			});
			bottom.add(cancel);
			bottom.add(Box.createHorizontalStrut(UIScale.scale(8)));
			done.addActionListener((a) -> {
				if (state.rootManifest.name.equals(field.getText().trim())) {
					d.dispose();
					return;
				}
				saveState("Change Options");
				state.rootManifest.name = field.getText().trim();
				updateTitle();
				markDirty();
				d.dispose();
			});
			bottom.add(done);
			d.setContentPane(box);
			d.setSize(UIScale.scale(240), UIScale.scale(120));
			d.getRootPane().setDefaultButton(done);
			d.setLocationRelativeTo(frame);
			d.setVisible(true);
		}, "Change basic information and options for the pack."), packOpen);
		jmb.add(packMenu);
		JMenu versionsMenu = new JMenu("Versions");
		addWithEnableCheck(versionsMenu, menuItem("New…", "icons/new-version.png", 't', () -> {
			saveState("New Version");
			int nextCode = state.updateManifests.isEmpty() ? 1 : state.updateManifests.lastKey()+1;
			OrderedVersion ov = new OrderedVersion("Unnamed", nextCode);
			if (state.rootManifest.versions.current != null) {
				state.rootManifest.versions.history.add(0, state.rootManifest.versions.current);
			}
			state.rootManifest.versions.current = ov;
			state.versionNames.put(ov.code, ov.name);
			UpdateManifest um = UpdateManifest.create();
			um.dirty = true;
			um.hash_function = DEFAULT_HASH_FUNCTION;
			state.updateManifests.put(ov.code, um);
			rebuildVersionsList(true);
			markDirty();
		}, "Create a new pack version to track file changes."), packOpen);
		jmb.add(versionsMenu);
		JMenu historyMenu = new JMenu("History");
		addWithEnableCheck(historyMenu, undoItem = menuItem("Undo", "icons/undo.png", 'z', () -> {
			undo();
		}, "Undo the last change made."), () -> !undo.isEmpty());
		addWithEnableCheck(historyMenu, redoItem = menuItem("Redo", "icons/redo.png", 'Z', () -> {
			redo();
		}, "Redo the last change undone."), () -> !redo.isEmpty());
		jmb.add(historyMenu);
		JMenu metaMenu = new JMenu("Meta");
		JMenu themeMenu = new JMenu("Theme");
		themeMenu.setIcon(new MenuImageIcon("icons/bucket.png"));
		themeMenu.add(decoration = checkMenuItem(true, "System Decorations", null, '\0', () -> {
			JFrame.setDefaultLookAndFeelDecorated(!decoration.isSelected());
			JDialog.setDefaultLookAndFeelDecorated(!decoration.isSelected());
			updateDecor();
			updateLaf();
		}));
		try {
			Class.forName("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
			themeMenu.add(radioMenuItem(false, "GTK+ (buggy!)", null, '\0', () -> {
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
			themeMenu.add(radioMenuItem(false, "System", null, '\0', () -> {
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
		themeMenu.addSeparator();
		themeMenu.add(radioMenuItem(true, "2020 (Flat Dark)", null, '\0', () -> {
			FlatAnimatedLafChange.showSnapshot();
			enableDecor();
			FlatUnsupDarkLaf.install();
			updateLaf();
			FlatAnimatedLafChange.hideSnapshotWithAnimation();
		}));
		themeMenu.add(radioMenuItem(false, "2020 (Flat Light)", null, '\0', () -> {
			FlatAnimatedLafChange.showSnapshot();
			enableDecor();
			FlatUnsupLightLaf.install();
			updateLaf();
			FlatAnimatedLafChange.hideSnapshotWithAnimation();
		}));
		themeMenu.add(radioMenuItem(false, "2008 (Nimbus)", null, '\0', () -> {
			FlatAnimatedLafChange.showSnapshot();
			disableDecor();
			try {
				UIManager.setLookAndFeel(new NimbusLookAndFeel());
			} catch (UnsupportedLookAndFeelException e) {
			}
			updateLaf();
			FlatAnimatedLafChange.hideSnapshotWithAnimation();
		}));
		themeMenu.add(radioMenuItem(false, "2004 (Ocean)", null, '\0', () -> {
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
		themeMenu.add(radioMenuItem(false, "1998 (Steel)", null, '\0', () -> {
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
			themeMenu.add(radioMenuItem(false, "1996 (Motif)", null, '\0', () -> {
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
		metaMenu.add(themeMenu);
		metaMenu.add(menuItem("About", "icons/about.png", '\0', () -> {
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
					+ "Copyright &copy; 2020 — 2021 Una Thompson<br/>"
					+ "Released under the <a href='#gplv3'>GNU GPLv3</a> or later.<br/><br/>"
					+ "Uses <a href='https://github.com/falkreon/Jankson'>Jankson</a>, "
					+ "<a href='https://github.com/str4d/ed25519-java/'>EdDSA</a>, "
					+ "<a href='https://github.com/JFormDesigner/FlatLaf'>FlatLaF</a>, "
					+ "and <a href='https://github.com/rclone/rclone'>rclone</a>.<br/>"
					+ "Icons from <a href='https://materialdesignicons.com'>Material Design Icons</a> and <a href='https://icons8.com'>Icons8</a>.<br/>"
					+ "Powered by Java and Swing.<br/><br/>"
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
			Box foot = Box.createHorizontalBox();
			foot.add(Box.createHorizontalGlue());
			JButton close = new JButton("Close");
			close.addActionListener((a) -> {
				dialog.dispose();
			});
			foot.add(close);
			foot.add(Box.createHorizontalGlue());
			box.add(Box.createVerticalStrut(UIScale.scale(8)));
			box.add(foot);
			box.add(Box.createVerticalStrut(UIScale.scale(8)));
			dialog.setContentPane(box);
			dialog.getRootPane().setDefaultButton(close);
			dialog.setSize(UIScale.scale(400), UIScale.scale(330));
			dialog.setLocationRelativeTo(frame);
			dialog.setVisible(true);
		}, "Display an About dialog."));
		metaMenu.add(menuItem("Quit", "icons/close.png", 'q', () -> {
			if (!promptDestructiveIfDirty()) return;
			System.exit(0);
		}, "Exit the Creator."));
		jmb.add(metaMenu);
		
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (!promptDestructiveIfDirty()) return;
				System.exit(0);
			}
		});
		
		versions = new JList<>();
		DefaultListSelectionModel sm = new DefaultListSelectionModel();
		sm.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
		versions.addListSelectionListener(new ListSelectionListener() {
			
			private int lastSelection = 0;
			
			@Override
			public void valueChanged(ListSelectionEvent e) {
				int sel = versions.getSelectedIndex();
				if (sel == -1 || !versions.getCellRenderer().getListCellRendererComponent(versions, versions.getModel().getElementAt(sel), sel,
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
			private final JComponent border, border2;
			private final JLabel title, subtitle, alertIcon, bootIcon;
			private final JComponent[] allComponents;
			{
				box = Box.createVerticalBox();
				border = createBorderComponent();
				border2 = createBorderComponent();
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
					if (index != 0) return border2;
					title.setText("(current)");
				} else if (state.updateManifests.containsKey(v.code)) {
					if (state.bootstrapManifest != null && state.bootstrapManifest.version != null && v.code == state.bootstrapManifest.version.code) {
						bootIcon.setVisible(true);
						tooltip.add("Bootstrap version; new installs will use this version.");
					}
					title.setText(v.toString());
					UpdateManifest um = state.updateManifests.get(v.code);
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

			private JComponent createBorderComponent() {
				JComponent jc = new JComponent() {
					@Override
					public void paint(Graphics g) {
						g.clearRect(0, 0, getWidth(), getHeight());
						g.setColor(new Color(0xAA000000, true));
						g.fillRect(0, 0, getWidth(), getHeight());
					}
				};
				jc.setMinimumSize(new Dimension(1, 1));
				jc.setPreferredSize(new Dimension(20, 1));
				jc.setMaximumSize(new Dimension(32767, 1));
				return jc;
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
		
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, new JScrollPane(versions, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), hintOrFiles);
		
		split.setDividerLocation(UIScale.scale(192));
		
		frame.setSize(UIScale.scale(960), UIScale.scale(480));
		frame.setContentPane(split);
		frame.setJMenuBar(jmb);
		
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
	
	public static JsonObject copyInto(JsonObject dst, JsonElement _src) {
		if (!(_src instanceof JsonObject)) throw new IllegalArgumentException("Expected object, got "+_src);
		JsonObject src = (JsonObject)_src;
		for (Map.Entry<String, JsonElement> en : src.entrySet()) {
			dst.put(en.getKey(), en.getValue());
			dst.setComment(en.getKey(), src.getComment(en.getKey()));
		}
		return dst;
	}
	
	public static JsonArray copyInto(JsonArray dst, JsonElement _src) {
		if (!(_src instanceof JsonArray)) throw new IllegalArgumentException("Expected array, got "+_src);
		JsonArray src = (JsonArray)_src;
		for (int i = 0; i < src.size(); i++) {
			dst.add(src.get(i));
			dst.setComment(i, src.getComment(i));
		}
		return dst;
	}

	public static void saveState(String change) {
		redo.clear();
		State cpy = state.copy();
		cpy.selectedVersionIdx = versions.getSelectedIndex();
		cpy.changeTitle = change;
		undo.add(cpy);
		if (undo.size() > 1000) {
			undo.remove(0);
		}
		updateUndoRedo();
	}
	
	private static void undo() {
		if (undo.isEmpty()) return;
		State s = undo.remove(undo.size()-1);
		redo.add(0, state);
		state.changeTitle = s.changeTitle;
		state = s;
		s.changeTitle = null;
		updateUiForHistoryWalk();
	}
	
	private static void redo() {
		if (redo.isEmpty()) return;
		State s = redo.remove(0);
		state.changeTitle = s.changeTitle;
		undo.add(state);
		state = s;
		state.changeTitle = null;
		updateUiForHistoryWalk();
	}
	
	private static void updateUiForHistoryWalk() {
		updateTitle();
		rebuildVersionsList(false);
		versions.setSelectedIndex(state.selectedVersionIdx);
		updateUndoRedo();
	}

	private static void updateUndoRedo() {
		if (undo.isEmpty()) {
			undoItem.setEnabled(false);
			undoItem.setText("Undo");
		} else {
			undoItem.setEnabled(true);
			undoItem.setText("Undo "+undo.get(undo.size()-1).changeTitle);
		}
		if (redo.isEmpty()) {
			redoItem.setEnabled(false);
			redoItem.setText("Redo");
		} else {
			redoItem.setEnabled(true);
			redoItem.setText("Redo "+redo.get(0).changeTitle);
		}
	}

	public static void updateTitle() {
		if (state.rootManifest == null) {
			frame.setTitle("unsup creator");
		} else {
			frame.setTitle((state.dirty ? "*" : "")+state.rootManifest.name+" - unsup creator");
		}
	}

	public static void markDirty() {
		state.dirty = true;
		if (state.firstChangeSinceLastSave == -1) {
			state.firstChangeSinceLastSave = System.currentTimeMillis();
		}
		updateTitle();
	}
	
	public static void markClean() {
		state.dirty = false;
		state.firstChangeSinceLastSave = -1;
		updateTitle();
	}
	
	private static boolean save(boolean forceSelect) {
		boolean implicit = !forceSelect && origin != null;
		if (!implicit) {
			boolean retry = false;
			File selected = null;
			String defaultDir = lastOpenDirectory;
			do {
				retry = false;
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer pb = stack.mallocPointer(1);
					int res = NFD_PickFolder(defaultDir, pb);
					if (res == NFD_OKAY) {
						File dir = new File(pb.getStringUTF8(0));
						File manifest = new File(dir, "manifest.json");
						if (!manifest.exists()) {
							String[] list = dir.list();
							if (list != null && list.length > 2) {
								int resp = JOptionPane.showConfirmDialog(frame, "<html><b>Are you sure you want to use this folder?</b><br/>"
										+ "There are already files in this folder and no manifest.json.<br/>"
										+ "Multiple files and folders will be created in this folder if you continue.<br/>"
										+ "</html>", "Confirm tarbomb", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
								if (resp == JOptionPane.YES_OPTION) {
									selected = manifest;
								} else if (resp == JOptionPane.NO_OPTION) {
									defaultDir = dir.getAbsolutePath();
									retry = true;
								} else {
									nNFD_Free(pb.get());
									return false;
								}
							} else {
								defaultDir = dir.getAbsolutePath();
								selected = manifest;
							}
						} else {
							defaultDir = dir.getAbsolutePath();
							selected = manifest;
						}
					} else if (res == NFD_CANCEL) {
						return false;
					} else {
						JOptionPane.showMessageDialog(frame, "NFD internal error: "+NFD_GetError(), "Unexpected error", JOptionPane.ERROR_MESSAGE);
						return false;
					}
					nNFD_Free(pb.get());
				}
			} while (retry);
			if (selected == null) return false;
			lastOpenDirectory = defaultDir;
			origin = selected;
		}
		// check implicit so we don't warn the user when they Ctrl-S after renaming their pack
		if (!implicit && origin.exists()) {
			try {
				RootManifest rm = jkson.fromJson(jkson.load(origin), RootManifest.class);
				rm.validate();
				if (!Objects.equals(rm.name, state.rootManifest.name)) {
					int resp = JOptionPane.showConfirmDialog(frame, "<html><b>Really overwrite this pack?</b><br/>"
							+ "The selected folder contains a pack named \"<i>"+rm.name+"</i>\",<br/>"
							+ "but you are saving a pack named \"<i>"+state.rootManifest.name+"</i>\".<br/>"
							+ "Continuing will overwrite the existing pack. This cannot be undone."
							+ "</html>", "Confirm overwrite", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
					if (resp == JOptionPane.YES_OPTION) {
						// continue
					} else if (resp == JOptionPane.NO_OPTION) {
						return save(true);
					} else {
						return false;
					}
				}
			} catch (Throwable t) {}
		}
		File dir = origin.getParentFile();
		// TODO atomicity
		try {
			writeJson(jkson.toJson(state.rootManifest), origin);
			if (state.bootstrapManifest != null) {
				writeJson(jkson.toJson(state.bootstrapManifest), new File(dir, "bootstrap.json"));
			}
			new File(dir, "versions").mkdirs();
			for (Map.Entry<Integer, UpdateManifest> en : state.updateManifests.entrySet()) {
				if (true || en.getValue().dirty) {
					writeJson(jkson.toJson(en.getValue()), new File(dir, "versions/"+en.getKey()+".json"));
					en.getValue().dirty = false;
				}
			}
			markClean();
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame, "<html><b>An unexpected error occurred while saving the pack.</b><br/>See console output for details.</html>", "Load error", JOptionPane.ERROR_MESSAGE);
		}
		return true;
	}
	
	private static void writeJson(JsonElement json, File f) throws IOException {
		String str = json.toJson(JsonGrammar.STRICT);
		try (FileOutputStream fos = new FileOutputStream(f)) {
			fos.write(str.getBytes(StandardCharsets.UTF_8));
		}
	}

	private static boolean promptDestructiveIfDirty() {
		if (state.dirty) {
			long diff = System.currentTimeMillis()-state.firstChangeSinceLastSave;
			String time = "???";
			TimeUnit lastUnit = null;
			for (TimeUnit unit : TimeUnit.values()) {
				if (diff < unit.toMillis(1)) {
					long amt = (diff/lastUnit.toMillis(1));
					String name = lastUnit.name().toLowerCase(Locale.ROOT);
					if (name.endsWith("s") && amt == 1) {
						time = name.substring(0, name.length()-1);
					} else {
						time = amt+" "+name;
					}
					break;
				}
				lastUnit = unit;
			}
			int resp = JOptionPane.showConfirmDialog(frame, "<html><b>You have unsaved changes from the past "+time+".</b><br/>"
					+ "Do you want to save them first?</html>", "Confirm destructive operation", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (resp == JOptionPane.YES_OPTION) {
				save(false);
				return true;
			} else if (resp == JOptionPane.NO_OPTION) {
				return true;
			} else {
				return false;
			}
		}
		return true;
	}

	private static void createDefaultManifests(String name) {
		markClean();
		origin = null;
		state.rootManifest = RootManifest.create();
		state.rootManifest.name = name;
		state.bootstrapManifest = BootstrapManifest.create();
		state.bootstrapManifest.hash_function = DEFAULT_HASH_FUNCTION;
		state.updateManifests.clear();
		state.versionNames.clear();
	}

	private static String s(int i) {
		return i == 1 ? "" : "s";
	}

	private static void deinitUI() {
		state = new State();
		undo.clear();
		redo.clear();
		updateUndoRedo();
		versions.clearSelection();
		versions.setListData(new Vector<>());
		cardLayout.show(hintOrFiles, "hint");
		updateTitle();
	}
	
	private static void initUI() {
		undo.clear();
		redo.clear();
		updateUndoRedo();
		System.out.println("rootManifest="+state.rootManifest);
		System.out.println("bootstrapManifest="+state.bootstrapManifest);
		System.out.println("updateManifests="+state.updateManifests);
		state.versionNames.clear();
		if (state.rootManifest.versions.current != null) {
			state.versionNames.put(state.rootManifest.versions.current.code, state.rootManifest.versions.current.name);
		}
		for (OrderedVersion v : state.rootManifest.versions.history) {
			state.versionNames.put(v.code, v.name);
		}
		rebuildVersionsList(false);
		
		cardLayout.show(hintOrFiles, "files");
		updateTitle();
	}

	public static void rebuildVersionsList(boolean retainSelection) {
		Vector<OrderedVersion> verVec = new Vector<>(state.rootManifest.versions.history.size()+1);
		if (state.rootManifest.versions.current != null) {
			verVec.add(state.rootManifest.versions.current);
		}
		verVec.addAll(state.rootManifest.versions.history);
		Collections.sort(verVec, Comparator.reverseOrder());
		verVec.add(0, null);
		verVec.add(null);
		
		int idx;
		if (retainSelection) {
			if (versions.getSelectedValue() == null) {
				idx = 0;
			} else {
				int selectedCode = versions.getSelectedValue().code;
				out: {
					for (int i = 0; i < verVec.size(); i++) {
						OrderedVersion v = verVec.get(i);
						if (v != null && v.code == selectedCode) {
							idx = i;
							break out;
						}
					}
					idx = 0;
				}
			}
		} else {
			idx = 0;
		}
		
		versions.setListData(verVec);
		versions.setSelectedIndex(idx);
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
			int mask = tk.getMenuShortcutKeyMask();
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

