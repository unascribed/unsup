package com.unascribed.sup;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DefaultExcludes {

	private static final Set<String> defExclNames = new HashSet<>(Arrays.asList(
			".fabric", ".mixin.out", "crash-reports", "logs", "renders", "saves",
			"schematics", "screenshots", "server-resource-packs", ".unsup-tmp",
			".unsup-state.json", "WailaErrorOutput.txt", "realms_persistence.json",
			"usercache.json", "usernamecache.json", "not-enough-crashes",
			"texturepacks-mp-cache", "stats", "ModLoader.txt", "LiteLoader.txt", "cache",
			"servers.dat_old", "backups"
		));
	private static final Set<String> defExclExts = new HashSet<>(Arrays.asList(
			"log", "nps", "dmp", "heapdump", "lck", "lock", "bak"
		));
	
	public static boolean shouldExclude(File file) {
		String name = file.getName();
		if (defExclNames.contains(name)) return true;
		if (name.startsWith("hs_err_pid")) return true;
		String ext = name.substring(name.lastIndexOf('.')+1);
		if (defExclExts.contains(ext)) return true;
		return false;
	}
	
}
