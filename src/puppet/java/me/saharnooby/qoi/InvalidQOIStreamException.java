package me.saharnooby.qoi;

import java.io.IOException;

import javax.annotation.NotNull;

/**
 * This exception is thrown when decoder detects invalid data in the input stream.
 */
public final class InvalidQOIStreamException extends IOException {

	InvalidQOIStreamException(@NotNull String message) {
		super(message);
	}

}
