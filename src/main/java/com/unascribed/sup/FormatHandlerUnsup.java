package com.unascribed.sup;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParserException;
import com.unascribed.sup.PuppetHandler.AlertMessageType;
import com.unascribed.sup.PuppetHandler.AlertOption;
import com.unascribed.sup.PuppetHandler.AlertOptionType;

class FormatHandlerUnsup extends FormatHandler {
	
	protected static final String DEFAULT_HASH_FUNCTION = HashFunction.SHA2_256.name;

	private static class FileToDownloadWithCode extends FilePlan {
		int code;
	}
	
	static CheckResult check(URL src) throws IOException, JsonParserException {
		Agent.log("INFO", "Loading unsup-format manifest from "+src);
		JsonObject manifest = IOHelper.loadJson(src, 32*K, new URL(src, "manifest.sig"));
		checkManifestFlavor(manifest, "root", IntPredicates.equals(1));
		Version ourVersion = Version.fromJson(Agent.state.getObject("current_version"));
		if (!manifest.containsKey("versions")) throw new IOException("Manifest is missing versions field");
		Version theirVersion = Version.fromJson(manifest.getObject("versions").getObject("current"));
		if (theirVersion == null) throw new IOException("Manifest is missing current version field");
		if (System.getProperty("unsup.debug.overrideRemoteVersionCode") != null) {
			theirVersion = new Version(theirVersion.name, Integer.getInteger("unsup.debug.overrideRemoteVersionCode", theirVersion.code));
		}
		JsonObject newState = new JsonObject(Agent.state);
		String ourFlavor = Agent.state.getString("flavor");
		JsonArray theirFlavors = manifest.getArray("flavors");
		if (ourFlavor == null && theirFlavors != null) {
			List<JsonObject> flavorObjects = theirFlavors.stream()
					.map(o -> (JsonObject)o)
					.filter(o -> o.getArray("envs") == null || Util.arrayContains(o.getArray("envs"), Agent.detectedEnv))
					.collect(Collectors.toList());
			if (flavorObjects.isEmpty()) {
				// there are no flavors eligible for our environment, so we can continue while ignoring flavors
			} else if (flavorObjects.size() == 1) {
				// there is exactly one eligible flavor choice, no sense in bothering the user
				ourFlavor = flavorObjects.get(0).getString("id");
			} else {
				List<String> flavorNames = flavorObjects.stream()
						.map(o -> o.getString("name"))
						.collect(Collectors.toList());
				Map<String, String> namesToIds = flavorObjects.stream()
						.collect(Collectors.toMap(o -> o.getString("name"), o -> o.getString("id")));
				Set<String> ids = flavorObjects.stream()
						.map(o -> o.getString("id"))
						.collect(Collectors.toSet());
				String def = Agent.config.get("flavor");
				if (def != null && !ids.contains(def)) {
					Agent.log("WARN", "Default flavor specified in unsup.ini does not exist in the manifest.");
					def = null;
				}
				String flavor = PuppetHandler.openChoiceAlert("Choose flavor", "<b>There are multiple flavor choices available.</b><br/>Please choose one.", flavorNames, def);
				if (flavor == null) {
					Agent.log("ERROR", "This is a flavorful manifest, but the unsup.ini doesn't specify a default flavor and we don't have a GUI to ask for a choice! Exiting.");
					Agent.exit(Agent.EXIT_CONFIG_ERROR);
					return null;
				}
				String id = namesToIds.getOrDefault(flavor, flavor);
				Agent.log("INFO", "Chose flavor "+id);
				ourFlavor = id;
			}
			newState.put("flavor", ourFlavor);
		}
		UpdatePlan<FileToDownloadWithCode> bootstrapPlan = null;
		boolean bootstrapping = false;
		if (ourVersion == null) {
			bootstrapping = true;
			Agent.log("INFO", "Update available! We have nothing, they have "+theirVersion);
			JsonObject bootstrap = null;
			try {
				bootstrap = IOHelper.loadJson(new URL(src, "bootstrap.json"), 2*M, new URL(src, "bootstrap.sig"));
			} catch (FileNotFoundException e) {
				Agent.log("INFO", "Bootstrap manifest missing, will have to retrieve and collapse every update");
			}
			if (bootstrap != null) {
				checkManifestFlavor(bootstrap, "bootstrap", IntPredicates.equals(1));
				Version bootstrapVersion = Version.fromJson(bootstrap.getObject("version"));
				if (bootstrapVersion == null) throw new IOException("Bootstrap manifest is missing version field");
				if (bootstrapVersion.code < theirVersion.code) {
					Agent.log("WARN", "Bootstrap manifest version "+bootstrapVersion+" is older than root manifest version "+theirVersion+", will have to perform extra updates");
				}
				HashFunction func = HashFunction.byName(bootstrap.getString("hash_function", DEFAULT_HASH_FUNCTION));
				PuppetHandler.updateTitle("Bootstrapping...", false);
				bootstrapPlan = new UpdatePlan<>(true, "null", bootstrapVersion.name, newState);
				for (Object o : bootstrap.getArray("files")) {
					if (!(o instanceof JsonObject)) throw new IOException("Entry "+o+" in files array is not an object");
					JsonObject file = (JsonObject)o;
					String path = file.getString("path");
					if (path == null) throw new IOException("Entry in files array is missing path");
					String hash = file.getString("hash");
					if (hash == null) throw new IOException(path+" in files array is missing hash");
					if (hash.length() != func.sizeInHexChars)  throw new IOException(path+" in files array hash "+hash+" is wrong length ("+hash.length()+" != "+func.sizeInHexChars+")");
					long size = file.getLong("size", -1);
					if (size < 0) throw new IOException(path+" in files array has invalid or missing size");
					if (size == 0 && !hash.equals(func.emptyHash)) throw new IOException(path+" in files array is empty file, but hash isn't the empty hash ("+hash+" != "+func.emptyHash+")");
					String urlStr = IOHelper.checkSchemeMismatch(src, file.getString("url"));
					JsonArray envs = file.getArray("envs");
					if (!Util.arrayContains(envs, Agent.detectedEnv)) {
						Agent.log("INFO", "Skipping "+path+" as it's not eligible for env "+Agent.detectedEnv);
						continue;
					}
					JsonArray flavors = file.getArray("flavors");
					if (flavors != null && !Util.arrayContains(flavors, ourFlavor)) {
						Agent.log("INFO", "Skipping "+path+" as it's not eligible for flavor "+ourFlavor);
						continue;
					}
					URL fallbackUrl = new URL(src, blobPath(hash));
					URL url;
					if (urlStr == null) {
						url = fallbackUrl;
					} else {
						url = new URL(urlStr);
					}
					FileToDownloadWithCode ftd = new FileToDownloadWithCode();
					ftd.state = new FileState(func, hash, size);
					ftd.url = url;
					ftd.fallbackUrl = fallbackUrl;
					ftd.code = bootstrapVersion.code;
					bootstrapPlan.files.put(path, ftd);
					bootstrapPlan.expectedState.put(path, FileState.EMPTY);
				}
				ourVersion = bootstrapVersion;
			} else {
				ourVersion = new Version("null", 0);
			}
		}
		if (theirVersion.code > ourVersion.code) {
			if (!bootstrapping) {
				Agent.log("INFO", "Update available! We have "+ourVersion+", they have "+theirVersion);
				AlertOption updateResp = PuppetHandler.openAlert("Update available",
						"<b>An update from "+ourVersion.name+" to "+theirVersion.name+" is available!</b><br/>Do you want to install it?",
						AlertMessageType.QUESTION, AlertOptionType.YES_NO, AlertOption.YES);
				if (updateResp == AlertOption.NO) {
					Agent.log("INFO", "Ignoring update by user choice.");
					return new CheckResult(ourVersion, theirVersion, null);
				}
			}
			UpdatePlan<FileToDownloadWithCode> plan = new UpdatePlan<>(bootstrapping, ourVersion.name, theirVersion.name, newState);
			if (bootstrapPlan != null) {
				plan.files.putAll(bootstrapPlan.files);
				plan.expectedState.putAll(bootstrapPlan.expectedState);
			}
			PuppetHandler.updateTitle(bootstrapping ? "Bootstrapping..." : "Updating...", false);
			PuppetHandler.updateSubtitle("Calculating update");
			boolean yappedAboutConsistency = false;
			int updates = theirVersion.code-ourVersion.code;
			for (int i = 0; i < updates; i++) {
				int code = ourVersion.code+(i+1);
				JsonObject ver = IOHelper.loadJson(new URL(src, "versions/"+code+".json"), 2*M, new URL(src, "versions/"+code+".sig"));
				checkManifestFlavor(ver, "update", IntPredicates.equals(1));
				HashFunction func = HashFunction.byName(ver.getString("hash_function", DEFAULT_HASH_FUNCTION));
				for (Object o : ver.getArray("changes")) {
					if (!(o instanceof JsonObject)) throw new IOException("Entry "+o+" in changes array is not an object");
					JsonObject file = (JsonObject)o;
					String path = file.getString("path");
					if (path == null) throw new IOException("Entry in changes array is missing path");
					String fromHash = file.getString("from_hash");
					if (fromHash != null && fromHash.length() != func.sizeInHexChars)  throw new IOException(path+" in changes array from_hash "+fromHash+" is wrong length ("+fromHash.length()+" != "+func.sizeInHexChars+")");
					long fromSize = file.getLong("from_size", -1);
					if (fromSize < 0) throw new IOException(path+" in changes array has invalid or missing from_size");
					if (fromSize == 0 && (fromHash != null && !fromHash.equals(func.emptyHash))) throw new IOException(path+" from in changes array is empty file, but hash isn't the empty hash or null ("+fromHash+" != "+func.emptyHash+")");
					String toHash = file.getString("to_hash");
					if (toHash != null && toHash.length() != func.sizeInHexChars)  throw new IOException(path+" in changes array to_hash "+toHash+" is wrong length ("+toHash.length()+" != "+func.sizeInHexChars+")");
					long toSize = file.getLong("to_size", -1);
					if (toSize < 0) throw new IOException(path+" in changes array has invalid or missing toSize");
					if (toSize == 0 && (toHash != null && !toHash.equals(func.emptyHash))) throw new IOException(path+" to in changes array is empty file, but hash isn't the empty hash or null ("+toHash+" != "+func.emptyHash+")");
					if (fromSize == toSize && Objects.equals(fromHash, toHash)) {
						Agent.log("WARN", path+" in changes array has same from and to hash/size? Ignoring");
						continue;
					}
					String urlStr = IOHelper.checkSchemeMismatch(src, file.getString("url"));
					JsonArray envs = file.getArray("envs");
					if (!Util.arrayContains(envs, Agent.detectedEnv)) {
						Agent.log("INFO", "Skipping "+path+" as it's not eligible for env "+Agent.detectedEnv);
						continue;
					}
					JsonArray flavors = file.getArray("flavors");
					if (flavors != null && !Util.arrayContains(flavors, ourFlavor)) {
						Agent.log("INFO", "Skipping "+path+" as it's not eligible for flavor "+ourFlavor);
						continue;
					}
					URL fallbackUrl = toHash == null ? null : new URL(src, blobPath(toHash));
					URL url;
					if (urlStr == null) {
						url = fallbackUrl;
					} else {
						url = new URL(urlStr);
					}
					if (plan.files.containsKey(path)) {
						FileToDownloadWithCode to = plan.files.get(path);
						if (to.state.func == func) {
							if (!Objects.equals(to.state.hash, fromHash) || to.state.size != fromSize) {
								throw new IOException("Bad update: "+path+" in "+to.code+" specified to become "+to.state+
										", but "+code+" expects it to have been "+func+"("+fromHash+") size "+fromSize);
							}
						} else if (!yappedAboutConsistency) {
							yappedAboutConsistency = true;
							Agent.log("WARN", "Cannot perform consistency check on multi-update due to mismatched hash functions");
						}
						to.state = new FileState(func, toHash, toSize);
						to.code = code;
						to.fallbackUrl = fallbackUrl;
						to.url = url;
					} else {
						FileToDownloadWithCode to = new FileToDownloadWithCode();
						to.code = code;
						to.state = new FileState(func, toHash, toSize);
						to.fallbackUrl = fallbackUrl;
						to.url = url;
						plan.expectedState.put(path, new FileState(func, fromHash, fromSize));
						plan.files.put(path, to);
					}
				}
			}
			return new CheckResult(ourVersion, theirVersion, plan);
		} else if (bootstrapPlan != null) {
			return new CheckResult(ourVersion, theirVersion, bootstrapPlan);
		} else if (ourVersion.code > theirVersion.code) {
			Agent.log("INFO", "Remote version is older than local version, doing nothing");
			return new CheckResult(ourVersion, theirVersion, null);
		} else {
			Agent.log("INFO", "We appear to be up-to-date. Nothing to do");
			return new CheckResult(ourVersion, theirVersion, null);
		}
	}
	
	private static String blobPath(String hash) {
		return "blobs/"+hash.substring(0, 2)+"/"+hash;
	}

	private static int checkManifestFlavor(JsonObject manifest, String flavor, IntPredicate versionPredicate) throws IOException {
		if (!manifest.containsKey("unsup_manifest")) throw new IOException("unsup_manifest key is missing");
		String str = manifest.getString("unsup_manifest");
		if (str == null) throw new IOException("unsup_manifest key is not a string");
		int dash = str.indexOf('-');
		if (dash == -1) throw new IOException("unsup_manifest value does not contain a dash");
		String lhs = str.substring(0, dash);
		if (!(flavor.equals(lhs))) throw new IOException("Manifest is of flavor "+lhs+", but we expected "+flavor);
		String rhs = str.substring(dash+1);
		int rhsI;
		try {
			rhsI = Integer.parseInt(rhs);
		} catch (IllegalArgumentException e) {
			throw new IOException("unsup_manifest value right-hand side is not a number: "+rhs);
		}
		if (!versionPredicate.test(rhsI)) throw new IOException("Don't know how to parse "+str+" manifest (version too new)");
		return rhsI;
	}
	
}
