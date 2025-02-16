package com.unascribed.sup.agent.pieces.pseudolocale;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PigLatin {
	private static final Pattern WORD = Pattern.compile("[A-Za-z]+");
	private static final Pattern ALL_CAPS = Pattern.compile("^[A-Z]{2,}$");
	private static final Pattern PROPER_CASE = Pattern.compile("^[A-Z][a-z]*$");

	private static final Pattern VOWEL = Pattern.compile("[aAeEiIoOuU]");

	public static String toPigLatin(String s) {
		StringBuffer sb = new StringBuffer();
		Matcher m = WORD.matcher(s);
		while (m.find()) {
			String english = m.group().toLowerCase(Locale.ENGLISH);
			String pigLatin;
			Matcher vowelM = VOWEL.matcher(english);
			if (!vowelM.find()) {
				// must not be a real word
				// just pass it through
				pigLatin = m.group();
			} else {
				int firstVowel = vowelM.start();
				if (firstVowel == 0) {
					if (!vowelM.find()) {
						// a -> away
						// I -> Iway
						pigLatin = english+"way";
					} else {
						int secondVowel = vowelM.start();
						// every -> eryevay
						// okay, usually the above rule is applied to any words starting with vowels
						// this is an "alternate convention" that i think sounds much better
						String displaced = english.substring(0, secondVowel);
						String kept = english.substring(secondVowel);
						pigLatin = kept+displaced+"ay";
					}
				} else {
					// pig -> igpay
					// happy -> appyhay
					// glove -> oveglay
					String displaced = english.substring(0, firstVowel);
					String kept = english.substring(firstVowel);
					pigLatin = kept+displaced+"ay";
				}
			}
			if (ALL_CAPS.matcher(m.group()).matches()) {
				pigLatin = pigLatin.toUpperCase(Locale.ENGLISH);
			} else if (PROPER_CASE.matcher(m.group()).matches()) {
				pigLatin = Character.toUpperCase(pigLatin.charAt(0))+pigLatin.substring(1);
			}
			m.appendReplacement(sb, pigLatin);
		}
		m.appendTail(sb);
		return sb.toString();
	}

}
