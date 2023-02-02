package com.unascribed.sup;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.grack.nanojson.JsonObject;

abstract class FormatHandler {
	
	protected static final int K = 1024;
	protected static final int M = K*1024;
	
	protected static class FileToDownload {
		FileState state;
		URL url;
		URL fallbackUrl;
		URL primerUrl;
		boolean hostile;
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
		
		public boolean sizeMatches(long size) {
			if (this.size == -1) return true;
			return size == this.size;
		}

		@Override
		public String toString() {
			return func+"("+hash+") size "+size;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			FileState other = (FileState) obj;
			if (func != other.func) return false;
			if (hash == null) {
				if (other.hash != null)
					return false;
			} else if (!hash.equals(other.hash))
				return false;
			if (size == -1 || other.size == -1)
				return true;
			if (size != other.size)
				return false;
			return true;
		}
		
		
	}
	
	protected static class UpdatePlan<F extends FileToDownload> {
		final boolean isBootstrap;
		final String fromVersion;
		final String toVersion;
		Map<String, F> files = new HashMap<>();
		Map<String, FileState> expectedState = new HashMap<>();
		final JsonObject newState;
		
		public UpdatePlan(boolean isBootstrap, String fromVersion, String toVersion, JsonObject newState) {
			this.isBootstrap = isBootstrap;
			this.fromVersion = fromVersion;
			this.toVersion = toVersion;
			this.newState = newState;
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
