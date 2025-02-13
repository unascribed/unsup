package com.unascribed.sup.data;

import java.util.Locale;

public enum ConflictType {
	NO_CONFLICT,
	LOCAL_AND_REMOTE_CREATED,
	LOCAL_AND_REMOTE_CHANGED,
	LOCAL_CHANGED_REMOTE_DELETED,
	LOCAL_DELETED_REMOTE_CHANGED,
	;
	public final String translationKey;
	ConflictType() {
		this.translationKey = name().toLowerCase(Locale.ROOT);
	}
}