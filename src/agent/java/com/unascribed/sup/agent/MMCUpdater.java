package com.unascribed.sup.agent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

public class MMCUpdater {
	
	private static final String INDENT = "    ";

	public static Map<String, String> currentComponentVersions = new HashMap<>();
	public static Map<String, List<String>> componentShortnames = new HashMap<>();


	public static void scan() {
		String pfx = "mmc-component-map.";
		for (String k : Agent.config.keySet()) {
			if (k.startsWith(pfx)) {
				MMCUpdater.componentShortnames.put(k.substring(pfx.length()), new ArrayList<>(Agent.config.getAll(k)));
			}
		}
		if (forEachComponentRO(MMCUpdater::scanMMCPatch) == FERes.MISSING) {
			Log.info("update_mmc_pack is enabled, but I don't see an mmc-pack.json. Ignoring");
		}
	}
	
	private static void scanMMCPatch(JsonObject o) {
		String uid = o.getString("uid");
		String compVer = o.getString("version");
		if (uid != null && compVer != null) {
			MMCUpdater.currentComponentVersions.put(uid, compVer);
		}
	}
	
	public static boolean apply(Map<String, String> newVers) {
		return forEachComponentRW(c -> applyComponent(newVers, c)) == FERes.CHANGED;
	}
	
	private static JsonObject applyComponent(Map<String, String> newVers, JsonObject c) {
		String uid = c.getString("uid");
		String oldVer = c.getString("version");
		if (uid == null || oldVer == null) return null;
		
		boolean changedOtherVer = false;
		if (c.containsKey("mcVersion") && newVers.containsKey("net.minecraft")) {
			// I have never ever seen this used in the wild. but I gotta support it
			if (!newVers.get("net.minecraft").equals(c.getString("mcVersion"))) {
				c.put("mcVersion", newVers.get("net.minecraft"));
				changedOtherVer = true;
			}
		}
		if (c.containsKey("requires")) {
			for (Object o : c.getArray("requires", new JsonArray())) {
				if (o instanceof JsonObject) {
					if (applyRequires(newVers, (JsonObject)o)) {
						changedOtherVer = true;
					}
				}
			}
		}
		
		String newVer = newVers.get(uid);
		if (newVer == null || oldVer.equals(newVer)) {
			if (changedOtherVer) return c;
			return null;
		}
		
		try {
			walk(c, o -> {
				if (o instanceof JsonObject) {
					applyComponentChild(newVers, (JsonObject)o);
				}
			});
		} catch (UnsupportedOperationException e) {
			if (e.getMessage() != null && e.getMessage().startsWith("!")) {
				Log.warn("Cannot update component "+uid+": "+e.getMessage().substring(1));
				return null;
			} else {
				throw e;
			}
		}
		c.put("version", newVer);
		return c;
	}

	private static void applyComponentChild(Map<String, String> newVers, JsonObject c) {
		String name = c.getString("name");
		if (name != null) {
			if ("local".equals(c.getString("MMC-hint")) || c.containsKey("MMC-filename")) {
				throw new UnsupportedOperationException("!Component uses local file");
			}
			if (c.containsKey("MMC-absoluteUrl")
						|| c.containsKey("MMC-absulute_url") // ANCIENT typo that is still supported, so we have to support it too
					) {
				throw new UnsupportedOperationException("!Component uses absolute URL");
			}
			if (c.containsKey("downloads")) {
				throw new UnsupportedOperationException("!Component uses fully-specified artifact downloads format instead of implicit Maven");
			}
			if (c.containsKey("sha1")) {
				// TODO it'd be nice to support this... but I think that'd involve basically reimplementing the entire MultiMC component downloader
				throw new UnsupportedOperationException("!Component uses hash checking");
			}
			String[] split = name.split(":");
			if (split.length < 3) return;
			String group = split[0];
			String artifact = split[1];
			String oldVer = split[2];
			String uid;
			if (newVers.containsKey(group)) {
				uid = group;
			} else if (newVers.containsKey(group+"."+artifact)) {
				uid = group+"."+artifact;
			} else {
				return;
			}
			String newVer = newVers.get(uid);
			if (newVer == null || newVer.equals(oldVer)) return;
			split[2] = newVer;
			c.put("name", String.join(":", split));
		}
	}

	private static boolean applyRequires(Map<String, String> newVers, JsonObject o) {
		String uid = o.getString("uid");
		String newVer = newVers.get(uid);
		if (uid == null || newVer == null) return false;
		String oldVer = o.getString("equals");
		if (oldVer == null || oldVer.equals(newVer)) return false;
		o.put("equals", newVer);
		return true;
	}

	private static void walk(Object o, Consumer<Object> walker) {
		walker.accept(o);
		if (o instanceof JsonObject) {
			((JsonObject)o).values().forEach(o2 -> walk(o2, walker));
		} else if (o instanceof JsonArray) {
			((JsonArray)o).forEach(o2 -> walk(o2, walker));
		}
	}
	
	private enum FERes {
		MISSING,
		SUCCESS,
		CHANGED,
	}

	private static FERes forEachComponentRO(Consumer<JsonObject> cb) {
		return forEachComponentRW(c -> {
			cb.accept(c);
			return null;
		});
	}
	
	private static FERes forEachComponentRW(Function<JsonObject, JsonObject> cb) {
		boolean anyChanges = false;
		File mmcPackF = new File("../mmc-pack.json");
		File mmcPackFTmp = new File("../mmc-pack.json.unsupnew");
		if (mmcPackF.exists()) {
			try {
				JsonObject mmcPack = JsonParser.object().from(mmcPackF.toURI().toURL());
				if (mmcPack.getInt("formatVersion", 0) == 1) {
					boolean changed = false;
					for (Object o : mmcPack.getArray("components", new JsonArray())) {
						if (o instanceof JsonObject) {
							JsonObject jo = (JsonObject)o;
							JsonObject res = cb.apply(jo);
							if (res != null) {
								changed = true;
								if (jo != res) {
									jo.clear();
									jo.putAll(res);
								}
							}
						}
					}
					if (changed) {
						try (FileOutputStream fos = new FileOutputStream(mmcPackFTmp)) {
							JsonWriter
								.indent(INDENT)
								.on(fos)
									.object(mmcPack)
								.done();
						} catch (FileNotFoundException e) {
							Log.error("How did we get here?", e);
						} catch (IOException e) {
							Log.warn("Failed to save MultiMC pack JSON", e);
						}
						try {
							/*
							 * Whenever MultiMC updates the mmc-pack.json, it waits 5 seconds before
							 * writing the changes, probably to avoid writing to it too many times.
							 * Loading component data counts as "changing" the file, and launching
							 * the game loads the component data. So, this means if it takes less
							 * than 5 seconds for a component update to apply, MMC will proceed to
							 * clobber it if the player does not restart the game within the
							 * remaining 5 second window, which would result in another component
							 * re-read.
							 * 
							 * This does not apply to components that are in the patches/ directory,
							 * just the mmc-pack.json. packwiz-installer is unaffected as it's a
							 * pre-launch command rather than a Java agent. (unsup could be used as
							 * a pre-launch command via standalone mode, but the Java agent approach
							 * makes it non-launcher-specific.)
							 *
							 * So just wait 5½ seconds to ensure MMC's timer has expired by the time
							 * we modify the file.
							 */
							try {
								Thread.sleep(5500);
							} catch (InterruptedException e) {}
							Files.move(mmcPackFTmp.toPath(), mmcPackF.toPath(), StandardCopyOption.REPLACE_EXISTING);
							anyChanges = true;
						} catch (IOException e) {
							Log.warn("Failed to save MultiMC pack JSON", e);
						}
					}
				} else {
					Log.warn("Failed to load MultiMC pack JSON: Unknown format version");
				}
			} catch (JsonParserException | MalformedURLException e) {
				Log.warn("Failed to load MultiMC pack JSON", e);
			}
			File[] lf = new File("../patches").listFiles();
			if (lf != null) {
				for (File f : lf) {
					try {
						JsonObject patch = JsonParser.object().from(f.toURI().toURL());
						if (patch.getInt("formatVersion", 0) == 1) {
							JsonObject res = cb.apply(patch);
							if (res != null) {
								File fTmp = new File(f.getParentFile(), f.getName()+".unsupnew");
								try (FileOutputStream fos = new FileOutputStream(fTmp)) {
									JsonWriter
										.indent(INDENT)
										.on(fos)
											.object(res)
										.done();
								} catch (FileNotFoundException e) {
									Log.error("How did we get here?", e);
								} catch (IOException e) {
									Log.warn("Failed to save MultiMC patch JSON "+f.getName(), e);
								}
								try {
									Files.move(fTmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
									anyChanges = true;
								} catch (IOException e) {
									Log.warn("Failed to save MultiMC patch JSON "+f.getName(), e);
								}
							}
						} else {
							Log.warn("Failed to load MultiMC patch JSON "+f.getName()+": Unknown format version");
						}
					} catch (JsonParserException | MalformedURLException e) {
						Log.warn("Failed to load MultiMC patch JSON "+f.getName(), e);
					}
				}
			}
			return anyChanges ? FERes.CHANGED : FERes.SUCCESS;
		} else {
			return FERes.MISSING;
		}
	}
	
}
