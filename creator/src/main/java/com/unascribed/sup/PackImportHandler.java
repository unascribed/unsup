package com.unascribed.sup;

import static org.lwjgl.util.nfd.NativeFileDialog.NFD_CANCEL;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_GetError;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_OKAY;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_PickFolder;
import static org.lwjgl.util.nfd.NativeFileDialog.nNFD_Free;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import com.formdev.flatlaf.util.UIScale;

public class PackImportHandler {

	public static void invoke() {
		boolean retry = false;
		File selectedTmp = null;
		String defaultDir = Creator.lastOpenDirectory;
		do {
			retry = false;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer pb = stack.mallocPointer(1);
				int res = NFD_PickFolder(defaultDir, pb);
				if (res == NFD_OKAY) {
					File dir = new File(pb.getStringUTF8(0));
					// TODO perform checks on the selected directory
					defaultDir = dir.getAbsolutePath();
					selectedTmp = dir;
				} else if (res == NFD_CANCEL) {
					return;
				} else {
					JOptionPane.showMessageDialog(Creator.frame, "NFD internal error: "+NFD_GetError(), "Unexpected error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				nNFD_Free(pb.get());
			}
		} while (retry);
		Creator.lastOpenDirectory = defaultDir;
		final File selectedf = selectedTmp;
		class LocalState {
			File selected = selectedf;
			File root = selected.getParentFile();
			Set<File> unchecked = new HashSet<>();
			Map<File, File[]> sortedChildren = new HashMap<>();
			List<TreeModelListener> treeModelListeners = new ArrayList<>();

			public void pivotSelected(File newSelected) {
				File oldRoot = root;
				root = newSelected.getParentFile();
				selected = newSelected;
				for (TreeModelListener tml : treeModelListeners) {
					tml.treeStructureChanged(new TreeModelEvent(this, new Object[] {oldRoot}));
				}
			}
		}
		LocalState lstate = new LocalState();
		TreeModel treeModel = new TreeModel() {

			@Override
			public void valueForPathChanged(TreePath path, Object newValue) {}
			
			@Override
			public void removeTreeModelListener(TreeModelListener l) {
				lstate.treeModelListeners.remove(l);
			}
			
			@Override
			public boolean isLeaf(Object node) {
				return !((File)node).isDirectory() || getChildren((File)node).length == 0;
			}
			
			@Override
			public Object getRoot() {
				return lstate.root;
			}
			
			@Override
			public int getIndexOfChild(Object parent, Object child) {
				if (parent == lstate.root) {
					return child == lstate.selected ? 0 : -1;
				}
				String name = ((File)child).getName();
				File[] files = getChildren((File)parent);
				for (int i = 0; i < files.length; i++) {
					if (files[i].getName().equals(name)) return i;
				}
				return -1;
			}
			
			@Override
			public int getChildCount(Object parent) {
				return parent == lstate.root ? 1 : getChildren((File)parent).length;
			}
			
			@Override
			public Object getChild(Object parent, int index) {
				if (parent == lstate.root) {
					if (index == 0) return lstate.selected;
					throw new ArrayIndexOutOfBoundsException(index);
				}
				return getChildren((File)parent)[index];
			}
			
			private File[] getChildren(File parent) {
				if (parent == lstate.root) return new File[] {lstate.selected};
				if (!lstate.sortedChildren.containsKey(parent)) {
					File[] children = parent.listFiles();
					if (children == null) children = new File[0];
					Arrays.sort(children, (a, b) -> {
						if (a.isDirectory() != b.isDirectory()) {
							return a.isDirectory() ? -1 : 1;
						}
						return Collator.getInstance().compare(a.getName(), b.getName());
					});
					for (File f : children) {
						if (DefaultExcludes.shouldExclude(f)) {
							lstate.unchecked.add(f);
						}
					}
					lstate.sortedChildren.put(parent, children);
				}
				return lstate.sortedChildren.get(parent);
			}

			@Override
			public void addTreeModelListener(TreeModelListener l) {
				lstate.treeModelListeners.add(l);
			}
			
		};
		JDialog dialog = new JDialog(Creator.frame, "Select what to import");
		JTree tree = new JTree(treeModel) {
			@Override
			public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
				if (value == lstate.root) return value.toString();
				return ((File)value).getName();
			}
		};
		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			
			Icon archive = new MenuImageIcon("icons/file/archive.png");
			Icon audio = new MenuImageIcon("icons/file/audio.png");
			Icon code = new MenuImageIcon("icons/file/code.png");
			Icon config = new MenuImageIcon("icons/file/config.png");
			Icon empty = new MenuImageIcon("icons/file/empty.png");
			Icon focusedFolder = new MenuImageIcon("icons/file/focused-folder.png");
			Icon folder = new MenuImageIcon("icons/file/folder.png");
			Icon image = new MenuImageIcon("icons/file/image.png");
			Icon jar = new MenuImageIcon("icons/file/jar.png");
			Icon json = new MenuImageIcon("icons/file/json.png");
			Icon nbt = new MenuImageIcon("icons/file/nbt.png");
			Icon parentFolder = new MenuImageIcon("icons/file/parent-folder.png");
			Icon props = new MenuImageIcon("icons/file/props.png");
			Icon text = new MenuImageIcon("icons/file/text.png");
			Icon unknown = new MenuImageIcon("icons/file/unknown.png");
			Icon video = new MenuImageIcon("icons/file/video.png");
			
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
				File f = (File)value;
				setToolTipText(null);
				setComponentPopupMenu(null);
				if (f == lstate.root) {
					setIcon(parentFolder);
					setToolTipText("Parent folder. You can move focus here; right-click and choose \"Move focus here\".");
					JPopupMenu menu = new JPopupMenu();
					JMenuItem mi = new JMenuItem("Move focus here");
					mi.addActionListener((a) -> {
						File oldSel = lstate.selected;
						lstate.pivotSelected(lstate.root);
						tree.expandPath(new TreePath(new Object[] {lstate.root, lstate.selected, oldSel}));
					});
					menu.add(mi);
					setComponentPopupMenu(menu);
				} else if (f == lstate.selected) {
					setIcon(focusedFolder);
					JPopupMenu menu = new JPopupMenu();
					JMenuItem mi = new JMenuItem("Move focus here");
					mi.setEnabled(false);
					menu.add(mi);
					setComponentPopupMenu(menu);
					setToolTipText("Focused folder. Imported files will be relative to this folder, and it is the \"root\" of the pack.");
				} else if (f.isDirectory()) {
					setIcon(folder);
					JPopupMenu menu = new JPopupMenu();
					JMenuItem mi = new JMenuItem("Move focus here");
					mi.addActionListener((a) -> {
						lstate.pivotSelected(f);
						tree.expandPath(new TreePath(new Object[] {lstate.root, lstate.selected}));
					});
					menu.add(mi);
					setComponentPopupMenu(menu);
				} else if (f.length() == 0) {
					setIcon(empty);
				} else {
					String n = f.getName();
					if (n.endsWith(".zip") || n.endsWith(".tar") || n.endsWith(".tgz") || n.endsWith(".rar") || n.endsWith(".7z") || n.endsWith(".gz")) {
						setIcon(archive);
					} else if (n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".oga") || n.endsWith(".mp3") || n.endsWith(".opus") || n.endsWith(".m4a") || n.endsWith(".mka")) {
						setIcon(audio);
					} else if (n.endsWith(".xml") || n.endsWith(".zs") || n.endsWith(".js") || n.endsWith(".lua") || n.endsWith(".html") || n.endsWith(".class") || n.endsWith(".csv")) {
						setIcon(code);
					} else if (n.endsWith(".cfg") || n.endsWith(".conf")) {
						setIcon(config);
					} else if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".ora") || n.endsWith(".xcf")) {
						setIcon(image);
					} else if (n.endsWith(".jar") || n.endsWith(".litemod")) {
						setIcon(jar);
					} else if (n.endsWith(".json") || n.endsWith(".json5") || n.endsWith(".hjson") || n.endsWith(".jkson") || n.endsWith(".mcmeta")) {
						setIcon(json);
					} else if (n.endsWith(".dat") || n.endsWith(".nbt") || n.endsWith(".mca") || n.endsWith(".mcr")) {
						setIcon(nbt);
					} else if (n.endsWith(".toml") || n.endsWith(".ini") || n.endsWith(".properties") || n.endsWith(".props") || n.endsWith(".lang")) {
						setIcon(props);
					} else if (n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".markdown") || n.endsWith(".log")) {
						setIcon(text);
					} else if (n.endsWith(".mp4") || n.endsWith(".ogv") || n.endsWith(".webm") || n.endsWith(".m4v") || n.endsWith(".mkv")) {
						setIcon(video);
					} else {
						setIcon(unknown);
					}
				}
				boolean checked = true;
				boolean disabled = false;
				File cursor = f;
				while (cursor != null && !(cursor.equals(lstate.root))) {
					if (lstate.unchecked.contains(cursor)) {
						checked = false;
						if (cursor != f) disabled = true;
					}
					cursor = cursor.getParentFile();
				}
				setEnabled(!disabled);
				boolean fchecked = checked;
				if (disabled) {
					setDisabledIcon(new DualIcon(new CustomFlatCheckBoxIcon() {
						@Override
						protected void paintIcon(Component c, Graphics2D g2) {
							int x = UIScale.scale(1);
							int y = UIScale.scale(2);
							g2.translate(x, y);
							super.paintIcon(c, g2);
							g2.translate(-x, -y);
						}

						@Override
						protected boolean isIndeterminate(Component c) {
							return false;
						}
						
						@Override
						protected boolean isSelected(Component c) {
							return false;
						}
						
						@Override
						protected boolean isFocused(Component c) {
							return false;
						}
					}, ((MenuImageIcon)getIcon()).withAlpha(0.7f)));
				}
				setIcon(new DualIcon(new CustomFlatCheckBoxIcon() {
					@Override
					protected void paintIcon(Component c, Graphics2D g2) {
						int x = UIScale.scale(1);
						int y = UIScale.scale(2);
						g2.translate(x, y);
						super.paintIcon(c, g2);
						g2.translate(-x, -y);
					}

					@Override
					protected boolean isIndeterminate(Component c) {
						return f == lstate.root;
					}
					
					@Override
					protected boolean isSelected(Component c) {
						return fchecked;
					}
					
					@Override
					protected boolean isFocused(Component c) {
						return sel;
					}
				}, getIcon()));
				return this;
			}
		});
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				mouseHappens(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				mouseHappens(e);
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1) {
					int row = tree.getRowForLocation(e.getX(), e.getY());
					if (row != -1) {
						Rectangle rect = tree.getRowBounds(row);
						if (e.getX() > rect.x && e.getX() < rect.x+UIScale.scale(15)) {
							TreePath path = tree.getPathForRow(row);
							File o = (File)path.getLastPathComponent();
							if (o == lstate.root) return;
							File cursor = o.getParentFile();
							while (cursor != null && !(cursor.equals(lstate.root))) {
								if (lstate.unchecked.contains(cursor)) {
									return;
								}
								cursor = cursor.getParentFile();
							}
							if (lstate.unchecked.contains(o)) {
								lstate.unchecked.remove(o);
							} else {
								lstate.unchecked.add(o);
							}
							tree.repaint();
						}

					}
				}
			}

			private void mouseHappens(MouseEvent e) {
				if (e.isPopupTrigger()) {
					int row = tree.getRowForLocation(e.getX(), e.getY());
					if (row != -1) {
						TreePath path = tree.getPathForRow(row);
						Object o = path.getLastPathComponent();
						Component c = tree.getCellRenderer().getTreeCellRendererComponent(tree, o, row == tree.getMinSelectionRow(),
								tree.isExpanded(row), tree.getModel().isLeaf(o), row, true);
						if (c instanceof JComponent) {
							JPopupMenu menu = ((JComponent) c).getComponentPopupMenu();
							if (menu != null) {
								menu.show(tree, e.getX(), e.getY());
							}
						}
					}
				}
			}
		});
		tree.expandRow(1);
		Box box = Box.createVerticalBox();
		box.add(new JScrollPane(tree, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
		Box bottom = Box.createHorizontalBox();
		box.add(Box.createVerticalStrut(8));
		box.add(bottom);
		box.add(Box.createVerticalStrut(8));
		bottom.add(Box.createHorizontalGlue());
		JButton done = new JButton("Import");
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener((a) -> {
			dialog.dispose();
		});
		bottom.add(cancel);
		bottom.add(Box.createHorizontalStrut(UIScale.scale(8)));
		done.addActionListener((a) -> {
			
		});
		bottom.add(done);
		bottom.add(Box.createHorizontalStrut(8));
		dialog.setContentPane(box);
		ToolTipManager.sharedInstance().registerComponent(tree);
		dialog.getRootPane().setDefaultButton(done);
		dialog.setSize(UIScale.scale(480), UIScale.scale(320));
		dialog.setLocationRelativeTo(Creator.frame);
		dialog.setVisible(true);
	
	}

}
