package com.unascribed.sup.json;

/**
 * A runtime exception with a friendly message that can be displayed to the user.
 */
public class ReportableException extends RuntimeException {

	public ReportableException(String msg) {
		super(msg);
	}
	
}
