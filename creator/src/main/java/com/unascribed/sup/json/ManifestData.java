package com.unascribed.sup.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManifestData {
	String flavor();
	int currentVersion();
	int minVersion() default 1;
	int maxVersion() default -1;
}