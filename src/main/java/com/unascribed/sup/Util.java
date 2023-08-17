package com.unascribed.sup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JPopupMenu;

public class Util {

	public static final String VERSION = "0.2.3";

	public static boolean containsWholeWord(String haystack, String needle) {
		if (haystack == null || needle == null) return false;
		return haystack.equals(needle) || haystack.endsWith(" "+needle) || haystack.startsWith(needle+" ") || haystack.contains(" "+needle+" ");
	}

	/* (non-Javadoc)
	 * used in the agent to suspend the update flow at a safe point if we're waiting for a
	 * System.exit due to the user closing the puppet dialog (the puppet handling is multithreaded,
	 * and a mutex is used to ensure we don't kill the updater during a sensitive period that could
	 * corrupt the directory state)
	 */
	public static void blockForever() {
		while (true) {
			try {
				Thread.sleep(Integer.MAX_VALUE);
			} catch (InterruptedException e) {}
		}
	}
	
	protected static boolean iterableContains(Iterable<?> arr, Object obj) {
		if (arr != null) {
			boolean anyMatch = false;
			for (Object en : arr) {
				if (Objects.equals(en, obj)) {
					anyMatch = true;
					break;
				}
			}
			if (!anyMatch) {
				return false;
			}
		}
		return true;
	}
	
	protected static boolean iterablesIntersect(Iterable<?> a, Iterable<?> b) {
		if (a != null) {
			boolean anyMatch = false;
			for (Object en : a) {
				if (iterableContains(b, en)) {
					anyMatch = true;
					break;
				}
			}
			if (!anyMatch) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Closes the stream when done.
	 */
	public static byte[] collectLimited(InputStream in, int limit) throws IOException {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int totalRead = 0;
			byte[] buf = new byte[limit/4];
			while (true) {
				int read = in.read(buf);
				if (read == -1) break;
				totalRead += read;
				if (totalRead > limit) {
					return null;
				}
				baos.write(buf, 0, read);
			}
			return baos.toByteArray();
		} finally {
			in.close();
		}
	}

	// here, I made it better. do you like it now
	private static final String hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff";
	
	public static String toHexString(byte[] bys) {
		StringBuilder sb = new StringBuilder(bys.length*2);
		for (int i = 0; i < bys.length; i++) {
			int idx = (bys[i]&0xFF)*2;
			sb.append(hex.substring(idx, idx+2));
		}
		return sb.toString();
	}

	public static void fixSwing() {
		// enable a bunch of nice things that are off by default for legacy compat
		// use OpenGL if possible
		System.setProperty("sun.java2d.opengl", "true");
		// do not use DirectX, it's buggy. software is better if OGL support is missing
		System.setProperty("sun.java2d.d3d", "false");
		System.setProperty("sun.java2d.noddraw", "true");
		// force font antialiasing
		System.setProperty("awt.useSystemAAFontSettings", "on");
		System.setProperty("swing.aatext", "true");
		System.setProperty("swing.useSystemFontSettings", "true");
		// only call invalidate as needed
		System.setProperty("java.awt.smartInvalidate", "true");
		// disable Metal's abuse of bold fonts
		System.setProperty("swing.boldMetal", "false");
		// always create native windows for popup menus (allows animations to play, etc)
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
		// no ImageIO, I don't want you to write tons of tiny files to the disk, to be quite honest
		ImageIO.setUseCache(false);
	}

	public static String b64Str(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	public static <K, V> Map<K, V> nullRejectingMap(Map<K, V> delegate) {
		return new NullRejectingMap<K, V>(delegate);
	}

	private static class NullRejectingMap<K, V> extends AbstractMap<K, V> {
		private final Map<K, V> delegate;

		private NullRejectingMap(Map<K, V> delegate) {
			this.delegate = delegate;
		}

		@Override
		public Set<Entry<K, V>> entrySet() {
			Set<Entry<K, V>> delegateSet = delegate.entrySet();
			return new AbstractSet<Map.Entry<K,V>>() {

				@Override
				public int size() {
					return delegate.size();
				}

				@Override
				public Iterator<Entry<K, V>> iterator() {
					Iterator<Entry<K, V>> delegateIter = delegateSet.iterator();
					return new Iterator<Map.Entry<K,V>>() {
						@Override
						public boolean hasNext() {
							return delegateIter.hasNext();
						}
						
						@Override
						public Entry<K, V> next() {
							Entry<K, V> delegateEn = delegateIter.next();
							return new Entry<K, V>() {

								@Override
								public K getKey() {
									return delegateEn.getKey();
								}

								@Override
								public V getValue() {
									return delegateEn.getValue();
								}

								@Override
								public V setValue(V value) {
									if (value == null) throw new IllegalArgumentException("Cannot assign null to a key: "+getKey());
									return delegateEn.setValue(value);
								}
							};
						}
					};
				}

				@Override
				public boolean add(Entry<K, V> e) {
					return delegateSet.add(e);
				}

				@Override
				public boolean addAll(Collection<? extends Entry<K, V>> c) {
					return delegateSet.addAll(c);
				}
			};
		}

		@Override
		public V put(K key, V value) {
			if (key == null) throw new IllegalArgumentException("Cannot assign a value to a null key: "+value);
			if (value == null) throw new IllegalArgumentException("Cannot assign null to a key: "+key);
			return delegate.put(key, value);
		}

		@SuppressWarnings("unlikely-arg-type")
		@Override
		public V remove(Object key) {
			return delegate.remove(key);
		}
	}

}
