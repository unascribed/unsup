package com.unascribed.sup.signing;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SignatureException;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;

public class RawEdDSAProvider implements SigProvider {

	private final EdDSAEngine engine;
	
	public RawEdDSAProvider(EdDSAPublicKey key) throws InvalidKeyException {
		this.engine = new EdDSAEngine();
		engine.initVerify(key);
		try {
			engine.setParameter(EdDSAEngine.ONE_SHOT_MODE);
		} catch (InvalidAlgorithmParameterException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public boolean verify(byte[] data, byte[] signature) throws SignatureException {
		return engine.verifyOneShot(data, signature);
	}
	
}
