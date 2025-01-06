package com.unascribed.sup.handler;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.unascribed.sup.Agent;
import com.unascribed.sup.Log;
import com.unascribed.sup.PuppetHandler;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.HashFunction;
import com.unascribed.sup.data.Version;
import com.unascribed.sup.pieces.NullRejectingMap;

public abstract class AbstractFormatHandler {
	
	public static final int K = 1024;
	public static final int M = K*1024;
	
	public static class FilePlan {
		public FileState state;
		public URI url;
		public URI fallbackUrl;
		public URI primerUrl;
		public boolean hostile;
		public boolean skip = false;
	}
	
	public static class FileState {
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
			String s = func+"("+hash+")";
			if (size == -1) return s;
			return s+" size "+size;
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
	
	public static class UpdatePlan<F extends FilePlan> {
		public final boolean isBootstrap;
		public final Map<String, F> files = NullRejectingMap.create();
		public final Map<String, FileState> expectedState = NullRejectingMap.create();
		public final JsonObject newState;
		
		public UpdatePlan(boolean isBootstrap, JsonObject newState) {
			this.isBootstrap = isBootstrap;
			this.newState = newState;
		}
	}
	
	public static class CheckResult {
		public final Version ourVersion;
		public final Version theirVersion;
		public final UpdatePlan<?> plan;
		public final Map<String, String> componentVersions;
		
		public CheckResult(Version ourVersion, Version theirVersion, UpdatePlan<?> plan, Map<String, String> componentVersions) {
			this.ourVersion = ourVersion;
			this.theirVersion = theirVersion;
			this.plan = plan;
			this.componentVersions = componentVersions;
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
						Log.info("Selecting default choice "+grp.defChoiceName+" for flavor group "+grp.name);
						ourFlavors.add(grp.defChoice);
					} else {
						Log.error("No choice provided for flavor group "+grp.name+" ("+grp.id+")");
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
