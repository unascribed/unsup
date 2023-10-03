package com.unascribed.sup.pieces;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class MemoryCookieJar implements CookieJar {
	private final List<Cookie> cookies = new ArrayList<>();

	@Override
	public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
		Iterator<Cookie> iter = this.cookies.iterator();
		while (iter.hasNext()) {
			Cookie c1 = iter.next();
			if (c1.expiresAt() < System.currentTimeMillis()) {
				iter.remove();
			} else {
				for (Cookie c2 : cookies) {
					if (c2.expiresAt() < System.currentTimeMillis()) continue;
					if (Objects.equals(c1.name(), c2.name())
								&& Objects.equals(c1.domain(), c2.domain())
								&& Objects.equals(c1.path(), c2.path())
								&& c1.secure() == c2.secure()
								&& c1.hostOnly() == c2.hostOnly()
							) {
						iter.remove();
					}
				}
			}
		}
		this.cookies.addAll(cookies);
	}

	@Override
	public synchronized List<Cookie> loadForRequest(HttpUrl url) {
		List<Cookie> out = new ArrayList<>();
		Iterator<Cookie> iter = cookies.iterator();
		while (iter.hasNext()) {
			Cookie c = iter.next();
			if (c.expiresAt() < System.currentTimeMillis()) {
				iter.remove();
			} else if (c.matches(url)) {
				out.add(c);
			}
		}
		return out;
	}
}