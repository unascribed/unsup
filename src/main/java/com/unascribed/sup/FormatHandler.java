package com.unascribed.sup;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

abstract class FormatHandler {
	
	protected static final int K = 1024;
	protected static final int M = K*1024;
	
	protected static class FileToDownload {
		FileState state;
		URL url;
		URL fallbackUrl;
	}
	
	protected static class FileState {
		public static final FileState EMPTY = new FileState(null, null, 0);
		
		public final HashFunction func;
		public final String hash;
		public final long size;
		
		public FileState(HashFunction func, String hash, long size) {
			this.func = func;
			this.hash = hash;
			this.size = size;
		}

		@Override
		public String toString() {
			return func+"("+hash+") size "+size;
		}
	}
	
	protected static class UpdatePlan<F extends FileToDownload> {
		final boolean isBootstrap;
		final String fromVersion;
		final String toVersion;
		Map<String, F> files = new HashMap<>();
		Map<String, FileState> expectedState = new HashMap<>();
		
		public UpdatePlan(boolean isBootstrap, String fromVersion, String toVersion) {
			this.isBootstrap = isBootstrap;
			this.fromVersion = fromVersion;
			this.toVersion = toVersion;
		}
	}
	
	protected static class CheckResult {
		final Version ourVersion;
		final Version theirVersion;
		final UpdatePlan<?> plan;
		
		public CheckResult(Version ourVersion, Version theirVersion, UpdatePlan<?> plan) {
			this.ourVersion = ourVersion;
			this.theirVersion = theirVersion;
			this.plan = plan;
		}
	}

}
