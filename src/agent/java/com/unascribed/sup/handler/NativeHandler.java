package com.unascribed.sup.handler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParserException;
import com.unascribed.sup.Agent;
import com.unascribed.sup.Log;
import com.unascribed.sup.PuppetHandler;
import com.unascribed.sup.SysProps;
import com.unascribed.sup.Util;
import com.unascribed.sup.PuppetHandler.AlertMessageType;
import com.unascribed.sup.PuppetHandler.AlertOption;
import com.unascribed.sup.PuppetHandler.AlertOptionType;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.HashFunction;
import com.unascribed.sup.data.Version;
import com.unascribed.sup.data.FlavorGroup.FlavorChoice;
import com.unascribed.sup.util.RequestHelper;
import com.unascribed.sup.util.IntPredicates;
import com.unascribed.sup.util.Iterables;

public class NativeHandler extends AbstractFormatHandler {
	
	protected static final String DEFAULT_HASH_FUNCTION = HashFunction.SHA2_256.name;

	private static class FileToDownloadWithCode extends FilePlan {
		int code;
	}
	
	public static CheckResult check(URI src) throws IOException, JsonParserException, URISyntaxException {
		Log.info("Loading unsup-format manifest from "+src);
		JsonObject manifest = RequestHelper.loadJson(src, 32*K, src.resolve("manifest.sig"));
		checkManifestFlavor(manifest, "root", IntPredicates.equals(1));
		Version ourVersion = Version.fromJson(Agent.state.getObject("current_version"));
		if (!manifest.containsKey("versions")) throw new IOException("Manifest is missing versions field");
		Version theirVersion = Version.fromJson(manifest.getObject("versions").getObject("current"));
		if (theirVersion == null) throw new IOException("Manifest is missing current version field");
		if (System.getProperty("unsup.debug.overrideRemoteVersionCode") != null) {
			theirVersion = new Version(theirVersion.name, Integer.getInteger("unsup.debug.overrideRemoteVersionCode", theirVersion.code));
		}
		JsonObject newState = new JsonObject(Agent.state);
		JsonArray ourFlavors = Agent.state.getArray("flavors");
		if (ourFlavors == null && Agent.state.containsKey("flavor")) {
			ourFlavors = new JsonArray();
			ourFlavors.add(Agent.state.get("flavor"));
			newState.put("flavors", ourFlavors);
			newState.remove("flavor");
		}
		JsonArray theirFlavorGroups = manifest.getArray("flavor_groups");
		List<FlavorGroup> unpickedGroups = new ArrayList<>();
		if (theirFlavorGroups != null) {
			flavors: for (Object ele : theirFlavorGroups) {
				if (ele instanceof JsonObject) {
					JsonObject obj = (JsonObject)ele;
					JsonArray envs = obj.getArray("envs");
					if (envs != null && !Iterables.contains(envs, Agent.detectedEnv)) {
						continue;
					}
					String id = obj.getString("id");
					if (id == null)
						throw new IOException("A flavor group is missing an ID");
					String name = obj.getString("name", id);
					String description = obj.getString("description", "No description");
					String defChoice = Agent.config.get("flavors."+id);
					JsonArray choices = obj.getArray("choices");
					FlavorGroup grp = new FlavorGroup();
					grp.id = id;
					grp.name = name;
					grp.description = description;
					for (Object cele : choices) {
						FlavorChoice c = new FlavorChoice();
						if (cele instanceof JsonObject) {
							JsonObject cobj = (JsonObject)cele;
							c.id = cobj.getString("id");
							if (c.id == null)
								throw new IOException("A flavor choice in group "+id+" is missing an ID");
							c.name = cobj.getString("name", c.id);
							c.description = cobj.getString("description", "");
						} else {
							c.id = String.valueOf(cele);
							c.name = c.id;
							c.description = "";
						}
						if (Iterables.contains(ourFlavors, c.id)) {
							// a choice has already been made for this flavor
							continue flavors;
						}
						c.def = c.id.equals(defChoice);
						if (c.def) {
							grp.defChoice = c.id;
							grp.defChoiceName = c.name;
						}
						grp.choices.add(c);
					}
					unpickedGroups.add(grp);
				}
			}
			ourFlavors = handleFlavorSelection(ourFlavors, unpickedGroups, newState);
		} else {
			JsonArray theirFlavors = manifest.getArray("flavors");
			if (theirFlavors != null) {
				FlavorGroup grp = new FlavorGroup();
				grp.id = "default";
				grp.name = "Flavor";
				for (Object ele : theirFlavors) {
					if (ele instanceof JsonObject) {
						JsonObject obj = (JsonObject)ele;
						JsonArray envs = obj.getArray("envs");
						if (envs != null && !Iterables.contains(envs, Agent.detectedEnv)) {
							continue;
						}
						String id = obj.getString("id");
						if (id == null)
							throw new IOException("A flavor group is missing an ID");
						String name = obj.getString("name", id);
						String description = "No description";
						int firstOParen = name.indexOf('(');
						int lastCParen = name.lastIndexOf(')');
						if (firstOParen != -1 && lastCParen > firstOParen) {
							String newName = name.substring(0, firstOParen).trim();
							description = name.substring(firstOParen+1, lastCParen);
							name = newName;
						}
						FlavorChoice c = new FlavorChoice();
						c.id = id;
						c.name = name;
						c.description = description;
						grp.choices.add(c);
					}
				}
				unpickedGroups.add(grp);
				ourFlavors = handleFlavorSelection(ourFlavors, unpickedGroups, newState);
			}
		}
		UpdatePlan<FileToDownloadWithCode> bootstrapPlan = null;
		boolean bootstrapping = false;
		if (ourVersion == null) {
			bootstrapping = true;
			Log.info("Update available! We have nothing, they have "+theirVersion);
			JsonObject bootstrap = null;
			try {
				bootstrap = RequestHelper.loadJson(src.resolve("bootstrap.json"), 2*M, src.resolve("bootstrap.sig"));
			} catch (FileNotFoundException e) {
				Log.info("Bootstrap manifest missing, will have to retrieve and collapse every update");
			}
			if (bootstrap != null) {
				checkManifestFlavor(bootstrap, "bootstrap", IntPredicates.equals(1));
				Version bootstrapVersion = Version.fromJson(bootstrap.getObject("version"));
				if (bootstrapVersion == null) throw new IOException("Bootstrap manifest is missing version field");
				if (bootstrapVersion.code < theirVersion.code) {
					Log.warn("Bootstrap manifest version "+bootstrapVersion+" is older than root manifest version "+theirVersion+", will have to perform extra updates");
				}
				HashFunction func = HashFunction.byName(bootstrap.getString("hash_function", DEFAULT_HASH_FUNCTION));
				PuppetHandler.updateTitle("Bootstrapping...", false);
				bootstrapPlan = new UpdatePlan<>(true, newState);
				for (Object o : bootstrap.getArray("files")) {
					if (!(o instanceof JsonObject)) throw new IOException("Entry "+o+" in files array is not an object");
					JsonObject file = (JsonObject)o;
					String path = file.getString("path");
					if (path == null) throw new IOException("Entry in files array is missing path");
					String hash = file.getString("hash");
					if (hash == null) throw new IOException(path+" in files array is missing hash");
					if (hash.length() != func.sizeInHexChars())  throw new IOException(path+" in files array hash "+hash+" is wrong length ("+hash.length()+" != "+func.sizeInHexChars()+")");
					long size = file.getLong("size", -1);
					if (size < 0) throw new IOException(path+" in files array has invalid or missing size");
					if (size == 0 && !hash.equals(func.emptyHash())) throw new IOException(path+" in files array is empty file, but hash isn't the empty hash ("+hash+" != "+func.emptyHash()+")");
					String urlStr = RequestHelper.checkSchemeMismatch(src, file.getString("url"));
					JsonArray envs = file.getArray("envs");
					if (!Iterables.contains(envs, Agent.detectedEnv)) {
						Log.info("Skipping "+path+" as it's not eligible for env "+Agent.detectedEnv);
						continue;
					}
					JsonArray flavors = file.getArray("flavors");
					if (flavors != null && !Iterables.intersects(flavors, ourFlavors)) {
						Log.info("Skipping "+path+" as it's not eligible for our selected flavors");
						continue;
					}
					URI fallbackUrl = src.resolve(Util.uriOfPath(blobPath(hash)));
					URI url;
					if (urlStr == null) {
						url = fallbackUrl;
					} else {
						url = new URI(urlStr);
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
				Log.info("Update available! We have "+ourVersion+", they have "+theirVersion);
				AlertOption updateResp = SysProps.DISABLE_RECONCILIATION ? AlertOption.YES : PuppetHandler.openAlert("Update available",
						"<b>An update from "+ourVersion.name+" to "+theirVersion.name+" is available!</b><br/>Do you want to install it?",
						AlertMessageType.QUESTION, AlertOptionType.YES_NO, AlertOption.YES);
				if (updateResp == AlertOption.NO) {
					Log.info("Ignoring update by user choice.");
					return new CheckResult(ourVersion, theirVersion, null, Collections.emptyMap());
				}
			}
			UpdatePlan<FileToDownloadWithCode> plan = new UpdatePlan<>(bootstrapping, newState);
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
				JsonObject ver = RequestHelper.loadJson(src.resolve(Util.uriOfPath("versions/"+code+".json")), 2*M,
						src.resolve(Util.uriOfPath("versions/"+code+".sig")));
				checkManifestFlavor(ver, "update", IntPredicates.equals(1));
				HashFunction func = HashFunction.byName(ver.getString("hash_function", DEFAULT_HASH_FUNCTION));
				for (Object o : ver.getArray("changes")) {
					if (!(o instanceof JsonObject)) throw new IOException("Entry "+o+" in changes array is not an object");
					JsonObject file = (JsonObject)o;
					String path = file.getString("path");
					if (path == null) throw new IOException("Entry in changes array is missing path");
					String fromHash = file.getString("from_hash");
					if (fromHash != null && fromHash.length() != func.sizeInHexChars())  throw new IOException(path+" in changes array from_hash "+fromHash+" is wrong length ("+fromHash.length()+" != "+func.sizeInHexChars()+")");
					long fromSize = file.getLong("from_size", -1);
					if (fromSize < 0) throw new IOException(path+" in changes array has invalid or missing from_size");
					if (fromSize == 0 && (fromHash != null && !fromHash.equals(func.emptyHash()))) throw new IOException(path+" from in changes array is empty file, but hash isn't the empty hash or null ("+fromHash+" != "+func.emptyHash()+")");
					String toHash = file.getString("to_hash");
					if (toHash != null && toHash.length() != func.sizeInHexChars())  throw new IOException(path+" in changes array to_hash "+toHash+" is wrong length ("+toHash.length()+" != "+func.sizeInHexChars()+")");
					long toSize = file.getLong("to_size", -1);
					if (toSize < 0) throw new IOException(path+" in changes array has invalid or missing toSize");
					if (toSize == 0 && (toHash != null && !toHash.equals(func.emptyHash()))) throw new IOException(path+" to in changes array is empty file, but hash isn't the empty hash or null ("+toHash+" != "+func.emptyHash()+")");
					if (fromSize == toSize && Objects.equals(fromHash, toHash)) {
						Log.warn(path+" in changes array has same from and to hash/size? Ignoring");
						continue;
					}
					String urlStr = RequestHelper.checkSchemeMismatch(src, file.getString("url"));
					JsonArray envs = file.getArray("envs");
					if (!Iterables.contains(envs, Agent.detectedEnv)) {
						Log.info("Skipping "+path+" as it's not eligible for env "+Agent.detectedEnv);
						continue;
					}
					JsonArray flavors = file.getArray("flavors");
					if (flavors != null && !Iterables.intersects(flavors, ourFlavors)) {
						Log.info("Skipping "+path+" as it's not eligible for our selected flavors");
						continue;
					}
					URI fallbackUrl = toHash == null ? null : src.resolve(Util.uriOfPath(blobPath(toHash)));
					URI url;
					if (urlStr == null) {
						url = fallbackUrl;
					} else {
						url = new URI(urlStr);
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
							Log.warn("Cannot perform consistency check on multi-update due to mismatched hash functions");
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
			return new CheckResult(ourVersion, theirVersion, plan, Collections.emptyMap());
		} else if (bootstrapPlan != null) {
			return new CheckResult(ourVersion, theirVersion, bootstrapPlan, Collections.emptyMap());
		} else if (ourVersion.code > theirVersion.code) {
			Log.info("Remote version is older than local version, doing nothing");
			return new CheckResult(ourVersion, theirVersion, null, Collections.emptyMap());
		} else {
			Log.info("We appear to be up-to-date. Nothing to do");
			return new CheckResult(ourVersion, theirVersion, null, Collections.emptyMap());
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
