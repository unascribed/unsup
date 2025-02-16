package com.unascribed.sup.pieces.pseudolocale;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class AccentedEnglish {

	private static final int[][] replacementsLo = Arrays.stream("äāáǎàăåǻãǟǡǻȁȃȧᶏḁẚạảấầẩẫậắằẳẵặɑαάὰἀἁἂἃἆἇᾂᾃᾰᾱᾲᾳᾴᾶᾷⱥ|bьвЪБбβƀƃɓᵬᶀḃḅḇꞗ|cçςĉčċćĉċƈȼ¢ɕḉꞓꞔ|dďđ₫ðδ|eēéěèêĕėëęзεέэ℮|fƒ|ḡģǧĝğġǥǵɠᶃꞡ|ĥħђн|ıīíǐìĭîïίįΐι|jĵ|ƙκ|ŀļℓĺļľł|m₥мṁ|ńňŉηήийñлпπ|ōóŏòôõöőσøǿ|pρƥφ|qʠɋ|řŗгѓґя|śšŝșşƨ|țţťŧтτ|ūúǔùûũůųüǖǘǚǜύϋΰµцџ|ν|ẃẁẅŵшщωώ|xжẋ×|yỳŷчγ|zźżžƶȥʐᵶᶎẑẓẕⱬ"
			.split("\\|")).map(s -> s.codePoints().toArray()).toArray(int[][]::new);

	private static final int[][] replacementsHi = Arrays.stream("ĀÁǍÀÂÃÄÅǺΆĂΔΛДĄ|ß฿|ČÇĈĆ€|ĎÐ|EĒÉĚÈĔЁΣΈЄЭЗ|F₣|ḠǴǦĜĞĢĠƓǤꞠ|HĤĦ|ĪÍǏÌÎÏĬΊ|JĴ|КĶǨ|Ŀ£ĻŁĹ|MṀ|ŃŇИЙΠЛ|ŌÓǑÒÔÕÖΌΘǾ|PÞ₽|QɊℚ|ŘЯГҐ|ŠŞȘ§|ŤŢȚŦ|ŪǓǕǗǙǛЦ|VVṼṾV̇Ꝟ|ẀẂẄŴШЩ|XЖẊχ|ΫŸŶỲΎΨ￥УЎЧ|ZŹŻŽƵȤẒẔẐⱫℤ"
			.split("\\|")).map(s -> s.codePoints().toArray()).toArray(int[][]::new);
	
	private static final int[] numbers = "⓪①②③④⑤⑥⑦⑧⑨".codePoints().toArray();
	
	private static final String symbols = "!@#$%^&*()_+-=[]\\{}|;':\",./<>?";
	
	public static String toEnXA(String str) {
		str = str.replace("…", "...");
		StringBuilder out = new StringBuilder((str.length()*3)/2);
		for (int cp : str.codePoints().toArray()) {
			for (int i = 0; i < Math.max(1, ThreadLocalRandom.current().nextInt(12)-ThreadLocalRandom.current().nextInt(12)); i++) {
				int rp = cp;
				if (cp >= 'a' && cp <= 'z') {
					rp = pick(replacementsLo[cp-'a']);
				} else if (cp >= 'A' && cp <= 'Z') {
					rp = pick(replacementsHi[cp-'A']);
				} else if (cp >= '0' && cp <= '9') {
					rp = numbers[cp-'0'];
				} else if (symbols.indexOf(cp) >= 0) {
					rp = pick(symbols);
				}
				out.appendCodePoint(rp);
			}
		}
		return out.toString();
	}
	
	private static char pick(String arr) {
		return arr.charAt(ThreadLocalRandom.current().nextInt(arr.length()));
	}
	
	private static int pick(int[] arr) {
		return arr[ThreadLocalRandom.current().nextInt(arr.length)];
	}
	
	public static void main(String[] args) {
		System.out.println(toEnXA("Sphinx of black quartz, judge my vow... 98467598635497"));
	}
	
}
