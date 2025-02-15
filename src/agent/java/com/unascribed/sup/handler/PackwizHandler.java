package com.unascribed.sup.handler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.moandjiezana.toml.Toml;
import com.unascribed.flexver.FlexVerComparator;
import com.unascribed.sup.Agent;
import com.unascribed.sup.Log;
import com.unascribed.sup.MMCUpdater;
import com.unascribed.sup.AlertMessageType;
import com.unascribed.sup.PuppetHandler;
import com.unascribed.sup.SysProps;
import com.unascribed.sup.Util;
import com.unascribed.sup.PuppetHandler.AlertOption;
import com.unascribed.sup.PuppetHandler.AlertOptionType;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.HashFunction;
import com.unascribed.sup.data.Version;
import com.unascribed.sup.data.FlavorGroup.FlavorChoice;
import com.unascribed.sup.pieces.Murmur2CFMessageDigest;
import com.unascribed.sup.util.Bases;
import com.unascribed.sup.util.RequestHelper;
import com.unascribed.sup.util.Iterables;

public class PackwizHandler extends AbstractFormatHandler {
	
	public static CheckResult check(URI src, boolean autoaccept) throws IOException, URISyntaxException {
		Version ourVersion = Version.fromJson(Agent.state.getObject("current_version"));
		Toml pack = RequestHelper.loadToml(src, 4*K, src.resolve("unsup.sig"));
		String fmt = pack.getString("pack-format");
		if (!fmt.equals("unsup-packwiz") && (!fmt.startsWith("packwiz:") || FlexVerComparator.compare("packwiz:1.1.0", fmt) < 0))
			throw new IOException("Cannot read unknown pack-format "+fmt);
		JsonObject pwstate = Agent.state.getObject("packwiz");
		if (pwstate == null) {
			pwstate = new JsonObject();
			Agent.state.put("packwiz", pwstate);
		}
		Toml indexMeta = pack.getTable("index");
		if (indexMeta == null)
			throw new IOException("Malformed pack.toml: [index] table is missing");
		HashFunction indexFunc = parseFunc(indexMeta.getString("hash-format"));
		String indexDoublet = indexFunc+":"+indexMeta.getString("hash");
		boolean hasIndexUpdate = !indexDoublet.equals(pwstate.getString("lastIndexHash"));
		Map<String, String> theirVers = Collections.emptyMap();
		boolean hasComponentUpdate = false;
		if (!MMCUpdater.currentComponentVersions.isEmpty()) {
			theirVers = new HashMap<>();
			for (Map.Entry<String, Object> en : pack.getTable("versions").entrySet()) {
				List<String> exp = MMCUpdater.componentShortnames.get(en.getKey());
				if (exp != null) {
					for (String s : exp) {
						theirVers.put(s, String.valueOf(en.getValue()));
					}
				} else {
					theirVers.put(en.getKey(), String.valueOf(en.getValue()));
				}
			}
			for (Map.Entry<String, String> en : theirVers.entrySet()) {
				String ours = MMCUpdater.currentComponentVersions.get(en.getKey());
				if (ours != null && !ours.equals(en.getValue())) {
					hasComponentUpdate = true;
				}
			}
		}
		boolean changeFlavors = SysProps.PACKWIZ_CHANGE_FLAVORS;
		boolean actualUpdate = hasIndexUpdate || hasComponentUpdate;
		if (!actualUpdate && Agent.config.getBoolean("offer_change_flavors", false)) {
			if (PuppetHandler.openAlert("$$changeFlavorsOffer", "", AlertMessageType.NONE, AlertOptionType.YES_NO, AlertOption.NO) == AlertOption.YES) {
				changeFlavors = true;
			}
		}
		if (changeFlavors || actualUpdate) {
			if (ourVersion == null) {
				ourVersion = new Version("null", 0);
			}
			Version theirVersion = new Version(pack.getString("version"), ourVersion.code+1);
			JsonObject newState = new JsonObject(Agent.state);
			pwstate = new JsonObject(pwstate);
			newState.put("packwiz", pwstate);
			
			if (hasIndexUpdate) {
				Log.info("Update available - our index state is "+pwstate.getString("lastIndexHash")+", theirs is "+indexDoublet);
			} else {
				Log.info("Update available - only components have changed");
			}
			String body = "dialog.update.named¤"+ourVersion.name+"¤"+theirVersion.name;
			if (ourVersion.name.equals(theirVersion.name)) {
				body = "dialog.update.unnamed";
			}
			boolean bootstrapping = !pwstate.containsKey("lastIndexHash");
			if (!bootstrapping && actualUpdate && !autoaccept) {
				AlertOption updateResp = PuppetHandler.openAlert("dialog.update.title",
						body, AlertMessageType.QUESTION, AlertOptionType.YES_NO, AlertOption.YES);
				if (updateResp == AlertOption.NO) {
					Log.info("Ignoring update by user choice.");
					return new CheckResult(ourVersion, theirVersion, null, Collections.emptyMap());
				}
			}
			pwstate.put("lastIndexHash", indexDoublet);
			PuppetHandler.updateTitle(bootstrapping ? "title.bootstrapping" : "title.updating", false);
			PuppetHandler.updateSubtitle("subtitle.calculating");
			Toml index = RequestHelper.loadToml(src.resolve(Util.uriOfPath(indexMeta.getString("file"))), 8*M,
					indexFunc, indexMeta.getString("hash"));
			Toml unsup = null;
			List<FlavorGroup> unpickedGroups = new ArrayList<>();
			Map<String, FlavorGroup> syntheticGroups = new HashMap<>();
			for (Map.Entry<String, Object> en : pwstate.getObject("syntheticFlavorGroups", new JsonObject()).entrySet()) {
				if (en.getValue() instanceof JsonObject) {
					JsonObject obj = (JsonObject)en.getValue();
					String id = obj.getString("id");
					if (id == null) continue;
					String name = obj.getString("name");
					String description = obj.getString("description");
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
							if (c.id == null) continue;
							c.name = cobj.getString("name");
							c.description = cobj.getString("description");
							grp.choices.add(c);
						}
					}
					syntheticGroups.put(en.getKey(), grp);
				}
			}
			Map<String, List<String>> metafileFlavors = new HashMap<>();
			JsonArray ourFlavors = Agent.state.getArray("flavors");
			if (ourFlavors == null) ourFlavors = new JsonArray();
			if (pack.containsTable("versions") && pack.getTable("versions").containsPrimitive("unsup")) {
				try {
					unsup = RequestHelper.loadToml(src.resolve("unsup.toml"), 64*K, null);
				} catch (FileNotFoundException e) {
					Log.debug("unsup is in the versions table, but there's no unsup.toml");
				}
				if (unsup != null) {
					if (unsup.containsTable("flavor_groups")) {
						flavors: for (Map.Entry<String, Object> en : unsup.getTable("flavor_groups").entrySet()) {
							if (en.getValue() instanceof Toml) {
								Toml group = (Toml)en.getValue();
								String groupId = en.getKey();
								String side = group.getString("side");
								if (side != null && !side.equals("both") && !side.equals(Agent.detectedEnv)) {
									Log.info("Skipping flavor group "+groupId+" as it's not eligible for env "+Agent.detectedEnv);
									continue;
								}
								String groupName = group.getString("name", groupId);
								String groupDescription = group.getString("description", "No description");
								String defChoice = Agent.config.get("flavors."+groupId);
								FlavorGroup grp = new FlavorGroup();
								grp.id = groupId;
								grp.name = groupName;
								grp.description = groupDescription;
								if (group.containsTableArray("choices")) {
									for (Object o : group.getList("choices")) {
										String id, name, description;
										if (o instanceof Map) {
											@SuppressWarnings("unchecked")
											Map<String, Object> choice = (Map<String, Object>)o;
											id = String.valueOf(choice.get("id"));
											name = String.valueOf(choice.getOrDefault("name", id));
											description = String.valueOf(choice.getOrDefault("description", ""));
										} else {
											id = String.valueOf(o);
											name = id;
											description = "";
										}
										if (!changeFlavors && Iterables.contains(ourFlavors, id)) {
											// a choice has already been made for this flavor
											continue flavors;
										}
										FlavorGroup.FlavorChoice c = new FlavorGroup.FlavorChoice();
										c.id = id;
										c.name = name;
										c.description = description;
										c.def = changeFlavors ? Iterables.contains(ourFlavors, id) : id.equals(defChoice);
										if (c.def) {
											grp.defChoice = c.id;
											grp.defChoiceName = c.name;
										}
										grp.choices.add(c);
									}
								}
								unpickedGroups.add(grp);
							}
						}
					}
					if (unsup.containsTable("metafile")) {
						for (Map.Entry<String, Object> en : unsup.getTable("metafile").entrySet()) {
							if (en.getValue() instanceof Toml) {
								Toml t = (Toml)en.getValue();
								if (t.contains("flavors")) {
									if (t.containsTableArray("flavors")) {
										metafileFlavors.put(en.getKey(), t.getList("flavors").stream()
												.map(String::valueOf)
												.collect(Collectors.toList()));
									} else {
										metafileFlavors.put(en.getKey(), Collections.singletonList(t.getString("flavors")));
									}
								}
							}
						}
					}
				}
			}
			UpdatePlan<FilePlan> plan = new UpdatePlan<>(bootstrapping, newState);
			Set<String> toDelete = new HashSet<>();
			JsonObject lastState = pwstate.getObject("lastState");
			if (lastState != null) {
				for (Map.Entry<String, Object> en : lastState.entrySet()) {
					toDelete.add(en.getKey());
					String v = String.valueOf(en.getValue());
					String[] s = v.split(":", 2);
					plan.expectedState.put(en.getKey(), new FileState(HashFunction.byName(s[0]), s[1], -1));
				}
			}
			JsonObject metafileState = pwstate.getObject("metafileState");
			if (metafileState == null) {
				metafileState = new JsonObject();
				pwstate.put("metafileState", metafileState);
			}
			JsonObject metafileFiles = pwstate.getObject("metafileFiles");
			if (metafileFiles == null) {
				metafileFiles = new JsonObject();
				pwstate.put("metafileFiles", metafileFiles);
			} else {
				for (Map.Entry<String, Object> en : metafileFiles.entrySet()) {
					toDelete.add(String.valueOf(en.getValue()));
				}
			}
			class Metafile {
				final String name;
				final String path;
				final String hash;
				final Toml toml;
				String target;
				
				public Metafile(String name, String path, String hash, Toml metafile) {
					this.name = name;
					this.path = path;
					this.hash = hash;
					this.toml = metafile;
				}
			}
			ExecutorService svc = Executors.newFixedThreadPool(12);
			List<Future<Metafile>> metafileFutures = new ArrayList<>();
			Map<String, FileState> postState = new HashMap<>();
			HashFunction func = parseFunc(index.getString("hash-format"));
			for (Toml file : index.getTables("files")) {
				String path = file.getString("file");
				String hash = file.getString("hash");
				if (file.getBoolean("metafile", false)) {
					String name = path.substring(path.lastIndexOf('/')+1, path.endsWith(".pw.toml") ? path.length()-8 : path.length());
					String metafileDoublet = (func+":"+hash);
					if (metafileDoublet.equals(metafileState.getString(path)) && !changeFlavors) {
						toDelete.remove(String.valueOf(metafileFiles.get(path)));
						continue;
					}
					metafileFutures.add(svc.submit(() -> {
						return new Metafile(name, path, hash, RequestHelper.loadToml(src.resolve(Util.uriOfPath(path)), 8*K, func, hash));
					}));
				} else {
					FilePlan f = new FilePlan();
					f.state = new FileState(func, hash, -1);
					f.url = src.resolve(Util.uriOfPath(path));
					toDelete.remove(path);
					postState.put(path, f.state);
					if (!plan.expectedState.containsKey(path)) {
						plan.expectedState.put(path, FileState.EMPTY);
					} else if (plan.expectedState.get(path).equals(f.state)) {
						continue;
					}
					plan.files.put(path, f);
				}
			}
			svc.shutdown();
			PuppetHandler.updateSubtitle("subtitle.packwiz.retrieving");
			PuppetHandler.updateTitle(bootstrapping ? "title.bootstrapping" : "title.updating", false);
			for (Future<Metafile> future : metafileFutures) {
				Metafile mf;
				while (true) {
					try {
						mf = future.get();
						break;
					} catch (InterruptedException e) {
					} catch (ExecutionException e) {
						for (Future<?> f2 : metafileFutures) {
							try {
								f2.cancel(false);
							} catch (Throwable t) {}
						}
						if (e.getCause() instanceof IOException) throw (IOException)e.getCause();
						throw new RuntimeException(e);
					}
				}
				String path = mf.path;
				String hash = mf.hash;
				Toml metafile = mf.toml;
				String side = metafile.getString("side");
				String metafileDoublet = (func+":"+hash);
				metafileState.put(path, metafileDoublet);
				if (side != null && !side.equals("both") && !side.equals(Agent.detectedEnv)) {
					Log.info("Skipping "+path+" as it's not eligible for env "+Agent.detectedEnv);
					continue;
				}
				path = path.replace("\\", "/");
				String pfx;
				int slash = path.lastIndexOf('/');
				if (slash >= 0) {
					pfx = path.substring(0, slash+1);
				} else {
					pfx = "";
				}
				mf.target = pfx+metafile.getString("filename");
				Toml option = metafile.getTable("option");
				syntheticGroups.remove(mf.name);
				if (option != null && option.getBoolean("optional", false) && !metafileFlavors.containsKey(mf.name)) {
					FlavorGroup synth = new FlavorGroup();
					synth.id = mf.name;
					synth.name = metafile.getString("name");
					synth.description = option.getString("description", "No description");
					String defChoice = Agent.config.get("flavors."+mf.name);
					synth.defChoice = defChoice;
					synth.defChoiceName = defChoice;
					boolean defOn = changeFlavors ? Iterables.contains(ourFlavors, mf.name+"_on") : option.getBoolean("default", false);
					FlavorGroup.FlavorChoice on = new FlavorGroup.FlavorChoice();
					on.id = mf.name+"_on";
					on.name = "On";
					on.def = defOn;
					synth.choices.add(on);
					FlavorGroup.FlavorChoice off = new FlavorGroup.FlavorChoice();
					off.id = mf.name+"_off";
					off.name = "Off";
					off.def = !defOn;
					synth.choices.add(off);
					metafileFlavors.put(mf.name, Collections.singletonList(on.id));
					syntheticGroups.put(mf.name, synth);
				}
			}

			JsonObject syntheticGroupsJson = new JsonObject();
			pwstate.put("syntheticFlavorGroups", syntheticGroupsJson);
			final JsonArray fourFlavors = ourFlavors;
			for (Map.Entry<String, FlavorGroup> en : syntheticGroups.entrySet()) {
				if (changeFlavors || !en.getValue().choices.stream().anyMatch(c -> Iterables.contains(fourFlavors, c.id))) {
					unpickedGroups.add(en.getValue());
				}
				FlavorGroup grp = en.getValue();
				JsonObject obj = new JsonObject();
				obj.put("id", grp.id);
				obj.put("name", grp.name);
				obj.put("description", grp.description);
				JsonArray choices = new JsonArray();
				for (FlavorChoice c : grp.choices) {
					JsonObject cobj = new JsonObject();
					cobj.put("id", c.id);
					cobj.put("name", c.name);
					cobj.put("description", c.description);
					choices.add(cobj);
				}
				obj.put("choices", choices);
				syntheticGroupsJson.put(en.getKey(), obj);
			}

			PuppetHandler.updateSubtitle("subtitle.waiting_for_flavors");
			if (changeFlavors) {
				ourFlavors.clear();
			}
			ourFlavors = handleFlavorSelection(ourFlavors, unpickedGroups, newState);
			
			for (Future<Metafile> future : metafileFutures) {
				Metafile mf;
				try {
					mf = future.get();
				} catch (Throwable e) {
					throw new AssertionError(e);
				}
				
				List<String> mfFlavors = metafileFlavors.get(mf.name);
				if (mfFlavors != null) Log.debug("Flavors for "+mf.name+": "+mfFlavors);
				if (mfFlavors != null && !Iterables.intersects(mfFlavors, ourFlavors)) {
					Log.info("Skipping "+mf.target+" as it's not eligible for our selected flavors");
					continue;
				}
				
				if (mf.target == null) {
					// skipped in an earlier pass
					continue;
				}
				
				String mfpath = mf.path;
				String path = mf.target;
				Toml metafile = mf.toml;
				
				metafileFiles.put(mfpath, path);
				toDelete.remove(path);
				Toml download = metafile.getTable("download");
				FilePlan f = new FilePlan();
				HashFunction thisFunc = parseFunc(download.getString("hash-format"));
				String thisHash = download.getString("hash");
				if (thisFunc == HashFunction.MURMUR2_CF) {
					thisHash = Murmur2CFMessageDigest.decToHex(thisHash);
				}
				f.state = new FileState(thisFunc, thisHash, -1);
				postState.put(path, f.state);
				if (!plan.expectedState.containsKey(path)) {
					plan.expectedState.put(path, FileState.EMPTY);
				} else if (plan.expectedState.get(path).equals(f.state)) {
					continue;
				}
				String url = download.getString("url");
				if (url != null) {
					f.url = new URI(url);
				} else {
					String mode = download.getString("mode");
					if (Bases.b64ToString("bWV0YWRhdGE6Y3Vyc2Vmb3JnZQ==").equals(mode)) {
						// Not a virus. Trust me, I'm a dolphin
						Toml tbl = metafile.getTable(Bases.b64ToString("dXBkYXRlLmN1cnNlZm9yZ2U="));
						f.hostile = true;
						String str = Long.toString(tbl.getLong(Bases.b64ToString("ZmlsZS1pZA==")));
						int i = (str.length()+1)/2;
						String l = str.substring(0, i);
						String r = str.substring(i);
						while (r.startsWith("0") && r.length() > 1) r = r.substring(1);
						f.url = new URI(String.format(Bases.b64ToString("aHR0cHM6Ly9tZWRpYWZpbGV6LmZvcmdlY2RuLm5ldC9maWxlcy8lcy8lcy8="), l, r))
								.resolve(URLEncoder.encode(metafile.getString(Bases.b64ToString("ZmlsZW5hbWU=")), "UTF-8"));
					} else {
						throw new IOException("Cannot update "+path+" - unrecognized download mode "+mode);
					}
				}
				plan.files.put(path, f);
			}
			for (String path : toDelete) {
				FilePlan f = new FilePlan();
				f.state = FileState.EMPTY;
				plan.files.put(path, f);
				postState.put(path, FileState.EMPTY);
			}
			lastState = new JsonObject();
			for (Map.Entry<String, FileState> en : plan.expectedState.entrySet()) {
				if (en.getValue().hash == null) continue;
				lastState.put(en.getKey(), en.getValue().func+":"+en.getValue().hash);
			}
			for (Map.Entry<String, FileState> en : postState.entrySet()) {
				if (en.getValue().hash == null) {
					lastState.remove(en.getKey());
					continue;
				}
				lastState.put(en.getKey(), en.getValue().func+":"+en.getValue().hash);
			}
			pwstate.put("lastState", lastState);
			return new CheckResult(ourVersion, theirVersion, plan, theirVers);
		} else {
			Log.info("We appear to be up-to-date. Nothing to do");
		}
		return new CheckResult(ourVersion, ourVersion, null, Collections.emptyMap());
	}
	
	static HashFunction parseFunc(String str) {
		switch (str) {
			case "md5": return HashFunction.MD5;
			case "sha1": return HashFunction.SHA1;
			case "sha256": return HashFunction.SHA2_256;
			case "sha384": return HashFunction.SHA2_384;
			case "sha512": return HashFunction.SHA2_512;
			case "murmur2": return HashFunction.MURMUR2_CF;
			default: throw new IllegalArgumentException("Unknown packwiz hash function "+str);
		}
	}
	
}
