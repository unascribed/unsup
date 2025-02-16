package com.unascribed.sup.agent.signing;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SignatureException;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;

public class RawEdDSAProvider implements SigProvider {

	private final ThreadLocal<EdDSAEngine> engine;
	
	public RawEdDSAProvider(EdDSAPublicKey key) throws InvalidKeyException {
		new EdDSAEngine().initVerify(key);
		this.engine = ThreadLocal.withInitial(() -> {
			EdDSAEngine n = new EdDSAEngine();
			try {
				n.initVerify(key);
				n.setParameter(EdDSAEngine.ONE_SHOT_MODE);
			} catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
				throw new AssertionError(e);
			}
			return n;
		});
	}

	@Override
	public boolean verify(byte[] data, byte[] signature) throws SignatureException {
		return engine.get().verifyOneShot(data, signature);
	}
	
}
