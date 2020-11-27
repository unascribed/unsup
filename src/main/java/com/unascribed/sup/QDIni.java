package com.unascribed.sup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A quick-and-dirty INI parser.
 */
class QDIni {
	public static class QDIniException extends IllegalArgumentException {
		public QDIniException() {}
		public QDIniException(String message, Throwable cause) { super(message, cause); }
		public QDIniException(String s) { super(s); }
		public QDIniException(Throwable cause) { super(cause); }
	}
	public static class BadValueException extends QDIniException {
		public BadValueException() {}
		public BadValueException(String message, Throwable cause) { super(message, cause); }
		public BadValueException(String s) { super(s); }
		public BadValueException(Throwable cause) { super(cause); }
	}
	public static class SyntaxErrorException extends QDIniException {
		public SyntaxErrorException() {}
		public SyntaxErrorException(String message, Throwable cause) { super(message, cause); }
		public SyntaxErrorException(String s) { super(s); }
		public SyntaxErrorException(Throwable cause) { super(cause); }
	}

	public interface IniTransformer {
		String transformLine(String path, String line);
		String transformValueComment(String key, String value, String comment);
		String transformValue(String key, String value);
	}

	private static class BlameString {
		public final String value;
		public final String file;
		public final int line;
		private BlameString(String value, String file, int line) {
			this.value = value;
			this.file = file;
			this.line = line;
		}
		public String blame() {
			return "line "+line+" in "+file;
		}
	}
	
	private final String prelude;
	private final Map<String, List<BlameString>> data;
	
	private QDIni(String prelude, Map<String, List<BlameString>> data) {
		this.prelude = prelude;
		this.data = data;
	}
	
	public boolean containsKey(String key) {
		return data.containsKey(key) && !data.get(key).isEmpty();
	}
	
	/**
	 * Return all defined values for the given key.
	 */
	public List<String> getAll(String key) {
		return unwrap(data.get(key));
	}
	
	private List<BlameString> getAllBlamed(String key) {
		return data.get(key);
	}
	
	public String getBlame(String key) {
		if (containsKey(key)) {
			return getBlamed(key).blame();
		}
		return "<unknown>";
	}
	
	public String getBlame(String key, int index) {
		if (containsKey(key)) {
			return getAllBlamed(key).get(index).blame();
		}
		return "<unknown>";
	}
	
	private List<String> unwrap(List<BlameString> list) {
		if (list == null) return null;
		return new AbstractList<String>() {

			@Override
			public String get(int index) {
				return unwrap(list.get(index));
			}

			@Override
			public int size() {
				return list.size();
			}
			
		};
	}
	
	private String unwrap(BlameString bs) {
		if (bs == null) return null;
		return bs.value;
	}

	/**
	 * Return the last defined value for the given key.
	 */
	public String get(String key) {
		return getLast(getAll(key));
	}
	
	/**
	 * Return the last defined value for the given key, or def if it isn't defined.
	 */
	public String get(String key, String def) {
		String s = get(key);
		return s == null ? def : s;
	}
	
	private BlameString getBlamed(String key) {
		return getLast(getAllBlamed(key));
	}
	
	public int getInt(String key, int def) throws BadValueException {
		return getParsed(key, Integer::parseInt, () -> "a whole number", def);
	}
	
	public double getDouble(String key, double def) throws BadValueException {
		return getParsed(key, Double::parseDouble, () -> "a number", def);
	}
	
	public boolean getBoolean(String key, boolean def) throws BadValueException {
		return getParsed(key, this::strictParseBoolean, () -> "true or false", def);
	}
	
	private boolean strictParseBoolean(String s) {
		switch (s.toLowerCase(Locale.ROOT)) {
			case "true": return true;
			case "false": return false;
			default: throw new IllegalArgumentException();
		}
	}
	
	public <E extends Enum<E>> E getEnum(String key, Class<E> clazz, E def) throws BadValueException {
		return getParsed(key, s -> Enum.valueOf(clazz, s.toUpperCase(Locale.ROOT)), () -> {
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for (E e : clazz.getEnumConstants()) {
				if (first) {
					first = false;
				} else {
					sb.append(", ");
				}
				sb.append(e.name().toLowerCase(Locale.ROOT));
			}
			return sb.toString();
		}, def);
	}
	
	private <T> T getParsed(String key, Function<String, T> parser, Supplier<String> error, T def) throws BadValueException {
		String s = get(key);
		if (s == null) return def;
		try {
			return parser.apply(s);
		} catch (IllegalArgumentException e) {
			throw new BadValueException(key+" must be "+error.get()+" (got "+s+") at "+getBlame(key), e);
		}
	}

	private <T> T getLast(List<T> list) {
		return list == null || list.isEmpty() ? null : list.get(list.size()-1);
	}

	public Set<String> keySet() {
		return data.keySet();
	}
	
	public Set<Map.Entry<String, List<String>>> entrySet() {
		return new AbstractSet<Map.Entry<String, List<String>>>() {

			@Override
			public Iterator<Map.Entry<String, List<String>>> iterator() {
				Iterator<Map.Entry<String, List<BlameString>>> delegate = data.entrySet().iterator();
				return new Iterator<Map.Entry<String, List<String>>>() {

					@Override
					public boolean hasNext() {
						return delegate.hasNext();
					}

					@Override
					public Map.Entry<String, List<String>> next() {
						Map.Entry<String, List<BlameString>> den = delegate.next();
						return new AbstractMap.SimpleImmutableEntry<>(den.getKey(), unwrap(den.getValue()));
					}
				};
			}

			@Override
			public int size() {
				return size();
			}
			
		};
	}
	
	public int size() {
		return data.size();
	}

	/**
	 * Lossily convert this QDIni's data back into an INI. Comments, section declarations, etc will
	 * be lost. If you want to modify an ini file rather than just do basic debugging, you should
	 * use {@link QDIni#loadAndTransform}.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("; Loaded from ");
		sb.append(prelude);
		sb.append("\r\n");
		for (Map.Entry<String, List<String>> en : entrySet()) {
			for (String v : en.getValue()) {
				sb.append(en.getKey());
				sb.append("=");
				sb.append(v);
				sb.append("\r\n");
			}
		}
		return sb.toString();
	}
	
	/**
	 * Merge the given QDIni's data with this QDIni's data, returning a new QDIni object. Keys
	 * defined in the given QDIni will have their values appended to this one's. For usages of
	 * {@link #get}, this is equivalent to an override.
	 */
	public QDIni merge(QDIni that) {
		Map<String, List<BlameString>> newData = new LinkedHashMap<>(Math.max(this.size(), that.size()));
		newData.putAll(data);
		for (Map.Entry<String, List<BlameString>> en : that.data.entrySet()) {
			if (newData.containsKey(en.getKey())) {
				List<BlameString> merged = new ArrayList<>(newData.get(en.getKey()).size()+en.getValue().size());
				merged.addAll(newData.get(en.getKey()));
				merged.addAll(en.getValue());
				newData.put(en.getKey(), Collections.unmodifiableList(merged));
			} else {
				newData.put(en.getKey(), en.getValue());
			}
		}
		return new QDIni(prelude+", merged with "+that.prelude, Collections.unmodifiableMap(newData));
	}
	
	/**
	 * Return a view of this QDIni's data, dropping multivalues and collapsing to a basic key-value
	 * mapping that returns the last defined value for any given key.
	 */
	public Map<String, String> flatten() {
		return new AbstractMap<String, String>() {

			@Override
			public String get(Object key) {
				return QDIni.this.get((String)key);
			}
			
			@Override
			public boolean containsKey(Object key) {
				return QDIni.this.containsKey((String)key);
			}
			
			@Override
			public Set<String> keySet() {
				return QDIni.this.keySet();
			}
			
			@Override
			public int size() {
				return QDIni.this.size();
			}
			
			@Override
			public Set<Entry<String, String>> entrySet() {
				return new AbstractSet<Map.Entry<String,String>>() {

					@Override
					public Iterator<Entry<String, String>> iterator() {
						Iterator<Entry<String, List<String>>> delegate = QDIni.this.entrySet().iterator();
						return new Iterator<Map.Entry<String,String>>() {

							@Override
							public boolean hasNext() {
								return delegate.hasNext();
							}

							@Override
							public Entry<String, String> next() {
								Entry<String, List<String>> den = delegate.next();
								return new SimpleImmutableEntry<>(den.getKey(), getLast(den.getValue()));
							}
						};
					}

					@Override
					public int size() {
						return size();
					}
					
				};
			}
			
		};
	}
	
	public static QDIni load(String fileName, String s) {
		try {
			return load(fileName, new StringReader(s));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
	
	public static QDIni load(File f) throws IOException {
		try(InputStream in = new FileInputStream(f)) {
			return load(f.getName(), in);
		}
	}
	
	public static QDIni load(String fileName, InputStream in) throws IOException {
		return load(fileName, new InputStreamReader(in, StandardCharsets.UTF_8));
	}
	
	public static QDIni load(String fileName, Reader r) throws IOException {
		return loadAndTransform(fileName, r, null, null);
	}

	public static QDIni loadAndTransform(String fileName, Reader r, IniTransformer transformer, Writer w) throws IOException, SyntaxErrorException {
		BufferedReader br = r instanceof BufferedReader ? (BufferedReader)r : new BufferedReader(r);
		Map<String, List<BlameString>> data = new LinkedHashMap<>();
		int lineNum = 1;
		String path = "";
		while (true) {
			String line = br.readLine();
			if (transformer != null) {
				boolean eof = line == null;
				line = transformer.transformLine(path, line);
				if (line == null) {
					if (eof) {
						break;
					} else {
						continue;
					}
				}
				if (eof) {
					if (w != null) {
						w.write(line);
						w.write("\r\n");
					}
					break;
				}
			}
			if (line == null) break;
			String trunc = line.trim();
			if (trunc.startsWith(";") || trunc.isEmpty()) {
				if (w != null) {
					w.write(line);
					w.write("\r\n");
				}
				lineNum++;
				continue;
			}
			if (line.startsWith("[")) {
				if (line.contains(";")) {
					trunc = line.substring(0, line.indexOf(';'));
				}
				trunc = trunc.trim();
				if (trunc.endsWith("]")) {
					path = trunc.substring(1, trunc.length()-1);
					if (path.contains("[") || path.contains("]")) {
						throw new SyntaxErrorException("Malformed section header at line "+lineNum+" in "+fileName);
					}
					if (!(path.isEmpty() || path.endsWith(":") || path.endsWith("."))) {
						path += ".";
					}
				} else {
					throw new SyntaxErrorException("Malformed section header at line "+lineNum+" in "+fileName);
				}
			} else if (line.contains("=")) {
				String comment = null;
				if (trunc.contains(";")) {
					comment = trunc.substring(trunc.indexOf(';')+1);
					trunc = trunc.substring(0, trunc.indexOf(';'));
				}
				trunc = trunc.trim();
				int equals = line.indexOf('=');
				String key = path+line.substring(0, equals);
				String value = line.substring(equals+1);
				if (transformer != null) {
					String newValue = transformer.transformValue(key, value);
					String newComment = transformer.transformValueComment(key, value, comment);
					if (!Objects.equals(value, newValue)) {
						line = line.replaceFirst("=(\\s*)\\Q"+value+"\\E", "=$1"+newValue);
					}
					if (!Objects.equals(comment, newComment)) {
						line = (line.contains(";") ? line.substring(0, line.indexOf(';')+1) : line+" ;")+newComment;
					}
				}
				final int lineNumF = lineNum;
				data.compute(key, (k, l) -> {
					if (l == null) l = new ArrayList<>();
					l.add(new BlameString(value, fileName, lineNumF));
					return l;
				});
			} else {
				throw new SyntaxErrorException("Couldn't find a section, comment, or key-value assigment at line "+lineNum+" in "+fileName);
			}
			if (w != null) {
				w.write(line);
				w.write("\r\n");
			}
			lineNum++;
		}
		Map<String, List<BlameString>> unmod = new LinkedHashMap<>(data.size());
		for (Map.Entry<String, List<BlameString>> en : data.entrySet()) {
			unmod.put(en.getKey(), Collections.unmodifiableList(en.getValue()));
		}
		return new QDIni(fileName, Collections.unmodifiableMap(unmod));
	}

}
