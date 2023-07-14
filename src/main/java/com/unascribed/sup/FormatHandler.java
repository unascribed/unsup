package com.unascribed.sup;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

abstract class FormatHandler {
	
	protected static final int K = 1024;
	protected static final int M = K*1024;
	
	protected static class FilePlan {
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
		public int hashCode() {
			return func.hashCode()^hash.hashCode();
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
	
	protected static class UpdatePlan<F extends FilePlan> {
		final boolean isBootstrap;
		final Map<String, F> files = Util.nullRejectingMap(new HashMap<>());
		final Map<String, FileState> expectedState = Util.nullRejectingMap(new HashMap<>());
		final JsonObject newState;
		
		public UpdatePlan(boolean isBootstrap, JsonObject newState) {
			this.isBootstrap = isBootstrap;
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
	
	protected static JsonArray handleFlavorSelection(JsonArray ourFlavors, List<FlavorGroup> unpickedGroups, JsonObject newState) {
		if (!unpickedGroups.isEmpty()) {
			ourFlavors = new JsonArray(ourFlavors == null ? Collections.emptyList() : ourFlavors);
			if (PuppetHandler.puppetOut != null) {
				PuppetHandler.tellPuppet(":expedite=openTimeout");
				ourFlavors.addAll(PuppetHandler.openFlavorSelectDialog("Select flavors", "", unpickedGroups));
			} else {
				for (FlavorGroup grp : unpickedGroups) {
					if (grp.defChoice != null) {
						Agent.log("INFO", "Selecting default choice "+grp.defChoiceName+" for flavor group "+grp.name);
						ourFlavors.add(grp.defChoice);
					} else {
						Agent.log("ERROR", "No choice provided for flavor group "+grp.name+" ("+grp.id+")");
						Agent.exit(Agent.EXIT_CONFIG_ERROR);
						return null;
					}
				}
			}
			newState.put("flavors", ourFlavors);
		}
		return ourFlavors;
	}

}
