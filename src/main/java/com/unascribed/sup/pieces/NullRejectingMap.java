package com.unascribed.sup.pieces;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class NullRejectingMap<K, V> extends AbstractMap<K, V> {
	private final Map<K, V> delegate;

	private NullRejectingMap(Map<K, V> delegate) {
		this.delegate = delegate;
	}

	public static <K, V> NullRejectingMap<K, V> of(Map<K, V> delegate) {
		return new NullRejectingMap<>(delegate);
	}
	public static <K, V> NullRejectingMap<K, V> create() {
		return of(new HashMap<>());
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