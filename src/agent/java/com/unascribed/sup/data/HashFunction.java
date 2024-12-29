package com.unascribed.sup.data;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.unascribed.sup.Agent;
import com.unascribed.sup.Log;
import com.unascribed.sup.pieces.Murmur2CFMessageDigest;
import com.unascribed.sup.util.Bases;

public enum HashFunction {
	MD5("MD5", "MD5", 128, true),
	SHA1("SHA-1", "SHA-1", 160, true),
	SHA2_256("SHA-2 256", "SHA-256", 256, false),
	SHA2_384("SHA-2 384", "SHA-384", 384, false),
	SHA2_512("SHA-2 512", "SHA-512", 512, false),
	SHA2_512_256("SHA-2 512/256", "SHA-512/256", 256, false),

	MURMUR2_CF("Murmur2-CF", Murmur2CFMessageDigest::new, 32, true),
	;
	
	private static final Map<String, HashFunction> BY_NAME = new HashMap<>();
	
	public final String name;
	private final Supplier<MessageDigest> supplier;
	private final int sizeInBits;
	private final int sizeInBytes;
	private final int sizeInHexChars;
	private final boolean insecure;
	private final String emptyHash;
	private final boolean unsupported;
	
	private boolean hasWarned = false;
	
	HashFunction(String name, String alg, int sizeInBits, boolean insecure) {
		this(name, () -> {
			try {
				return MessageDigest.getInstance(alg);
			} catch (NoSuchAlgorithmException e) {
				throw new UnsupportedOperationException(e);
			}
		}, sizeInBits, insecure);
	}
	
	HashFunction(String name, Supplier<MessageDigest> supplier, int sizeInBits, boolean insecure) {
		this.name = name;
		this.supplier = supplier;
		this.sizeInBits = sizeInBits;
		this.sizeInBytes = (sizeInBits+7)/8;
		this.sizeInHexChars = sizeInBytes()*2;
		this.insecure = insecure;
		boolean unsupported;
		String emptyHash;
		try {
			emptyHash = Bases.bytesToHex(createMessageDigest().digest());
			unsupported = false;
		} catch (UnsupportedOperationException e) {
			Log.warn("This JRE does not support "+name);
			emptyHash = "?????";
			unsupported = true;
		}
		this.emptyHash = emptyHash;
		this.unsupported = unsupported;
	}
	
	private UnsupportedOperationException unsupported() {
		return new UnsupportedOperationException(name+" is not supported by this JRE - the manifest needs to be changed to not use this hash function, or you need to update to a newer Java patch");
	}
	
	public MessageDigest createMessageDigest() {
		if (unsupported) throw unsupported();
		return supplier.get();
	}
	
	public String emptyHash() {
		if (unsupported) throw unsupported();
		return emptyHash;
	}

	public boolean insecure() {
		return insecure;
	}

	public int sizeInHexChars() {
		return sizeInHexChars;
	}

	public int sizeInBytes() {
		return sizeInBytes;
	}

	public int sizeInBits() {
		return sizeInBits;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public static HashFunction byName(String name) {
		if (!BY_NAME.containsKey(name)) throw new IllegalArgumentException("No hash function with name "+name);
		HashFunction func = BY_NAME.get(name);
		if (func != null && func.insecure() && !func.hasWarned) {
			func.hasWarned = true;
			if (Agent.packSig != null) {
				Log.warn("Using insecure hash function "+func+" for a signed manifest! This is a very bad idea!");
			} else {
				Log.warn("Using insecure hash function "+func);
			}
		}
		return func;
	}

	static {
		for (HashFunction func : values()) {
			BY_NAME.put(func.name, func);
		}
	}
}
