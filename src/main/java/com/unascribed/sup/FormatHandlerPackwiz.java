package com.unascribed.sup;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.grack.nanojson.JsonObject;
import com.moandjiezana.toml.Toml;
import com.unascribed.flexver.FlexVerComparator;
import com.unascribed.sup.PuppetHandler.AlertMessageType;
import com.unascribed.sup.PuppetHandler.AlertOption;
import com.unascribed.sup.PuppetHandler.AlertOptionType;

public class FormatHandlerPackwiz extends FormatHandler {
	
	static CheckResult check(URL src) throws IOException {
		Agent.log("INFO", "Loading packwiz-format manifest from "+src);
		Agent.log("WARN", "Packwiz format updating is still experimental! Please report bugs!");
		Version ourVersion = Version.fromJson(Agent.state.getObject("current_version"));
		Toml pack = IOHelper.loadToml(src, 4*K, new URL(src, "unsup.sig"));
		String fmt = pack.getString("pack-format");
		if (!fmt.startsWith("packwiz:") || FlexVerComparator.compare("packwiz:1.1.0", fmt) < 0)
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
		if (!indexDoublet.equals(pwstate.getString("lastIndexHash"))) {
			if (ourVersion == null) {
				ourVersion = new Version("null", 0);
			}
			Version theirVersion = new Version(pack.getString("version"), ourVersion.code+1);
			JsonObject newState = new JsonObject(Agent.state);
			pwstate = new JsonObject(pwstate);
			newState.put("packwiz", pwstate);
			Agent.log("INFO", "Update available - our index state is "+pwstate.getString("lastIndexHash")+", theirs is "+indexDoublet);
			String interlude = " from "+ourVersion.name+" to "+theirVersion.name;
			if (ourVersion.name.equals(theirVersion.name)) {
				interlude = "";
			}
			boolean bootstrapping = !pwstate.containsKey("lastIndexHash");
			if (!bootstrapping) {
				AlertOption updateResp = PuppetHandler.openAlert("Update available",
						"<b>An update"+interlude+" is available!</b><br/>Do you want to install it?",
						AlertMessageType.QUESTION, AlertOptionType.YES_NO, AlertOption.YES);
				if (updateResp == AlertOption.NO) {
					Agent.log("INFO", "Ignoring update by user choice.");
					return new CheckResult(ourVersion, theirVersion, null);
				}
			}
			pwstate.put("lastIndexHash", indexDoublet);
			PuppetHandler.updateTitle(bootstrapping ? "Bootstrapping..." : "Updating...", false);
			PuppetHandler.updateSubtitle("Calculating update");
			Toml index = IOHelper.loadToml(new URL(src, indexMeta.getString("file")), 64*K,
					indexFunc, indexMeta.getString("hash"));
			UpdatePlan<FilePlan> plan = new UpdatePlan<>(bootstrapping, ourVersion.name, theirVersion.name, newState);
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
			}
			class Metafile {
				final String path;
				final String hash;
				final Toml toml;
				
				public Metafile(String path, String hash, Toml metafile) {
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
					String metafileDoublet = (func+":"+hash);
					if (metafileDoublet.equals(metafileState.getString(path))) {
						toDelete.remove(String.valueOf(metafileFiles.get(path)));
						continue;
					}
					metafileFutures.add(svc.submit(() -> {
						return new Metafile(path, hash, IOHelper.loadToml(new URL(src, path), 8*K, func, hash));
					}));
				} else {
					FilePlan f = new FilePlan();
					f.state = new FileState(func, hash, -1);
					f.url = new URL(src, path);
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
			PuppetHandler.updateSubtitle("Retrieving metafiles");
			PuppetHandler.updateTitle(bootstrapping ? "Bootstrapping..." : "Updating...", false);
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
					Agent.log("INFO", "Skipping "+path+" as it's not eligible for env "+Agent.detectedEnv);
					continue;
				}
				String mfpath = path;
				path = path.replace("\\", "/");
				String pfx;
				int slash = path.lastIndexOf('/');
				if (slash >= 0) {
					pfx = path.substring(0, slash+1);
				} else {
					pfx = "";
				}
				path = pfx+metafile.getString("filename");
				metafileFiles.put(mfpath, path);
				toDelete.remove(path);
				Toml download = metafile.getTable("download");
				FilePlan f = new FilePlan();
				HashFunction thisFunc = parseFunc(download.getString("hash-format"));
				String thisHash = download.getString("hash");
				if (thisFunc == HashFunction.MURMUR2_CF) {
					thisHash = Murmur2MessageDigest.decToHex(thisHash);
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
					f.url = new URL(url);
				} else {
					String mode = download.getString("mode");
					if (Util.b64Str("bWV0YWRhdGE6Y3Vyc2Vmb3JnZQ==").equals(mode)) {
						// Not a virus. Trust me, I'm a dolphin
						Toml tbl = metafile.getTable(Util.b64Str("dXBkYXRlLmN1cnNlZm9yZ2U="));
						f.hostile = true;
						String str = Long.toString(tbl.getLong(Util.b64Str("ZmlsZS1pZA==")));
						int i = (str.length()+1)/2;
						String l = str.substring(0, i);
						String r = str.substring(i);
						while (r.startsWith("0")) r = r.substring(1);
						f.url = new URL(String.format(Util.b64Str("aHR0cHM6Ly9tZWRpYWZpbGV6LmZvcmdlY2RuLm5ldC9maWxlcy8lcy8lcy8lcw=="),
								l, r, metafile.getString(Util.b64Str("ZmlsZW5hbWU=")).replace("+", "%2B")));
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
			for (Map.Entry<String, FileState> en : postState.entrySet()) {
				if (en.getValue().hash == null) continue;
				lastState.put(en.getKey(), en.getValue().func+":"+en.getValue().hash);
			}
			pwstate.put("lastState", lastState);
			return new CheckResult(ourVersion, theirVersion, plan);
		} else {
			Agent.log("INFO", "We appear to be up-to-date. Nothing to do");
		}
		return new CheckResult(ourVersion, ourVersion, null);
	}
	
	@SuppressWarnings("deprecation")
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
