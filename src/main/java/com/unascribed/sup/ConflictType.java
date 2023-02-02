package com.unascribed.sup;

enum ConflictType {
	NO_CONFLICT(null),
	LOCAL_AND_REMOTE_CREATED("created by you and in this update"),
	LOCAL_AND_REMOTE_CHANGED("changed by you and in this update"),
	LOCAL_CHANGED_REMOTE_DELETED("changed by you and deleted in this update"),
	LOCAL_DELETED_REMOTE_CHANGED("deleted by you and changed in this update"),
	;
	public final String msg;
	ConflictType(String msg) {
		this.msg = msg;
	}
}