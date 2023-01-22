package de.dereingerostete.sfs.util;

import de.dereingerostete.sfs.StaticFileServerApplication;
import de.dereingerostete.sfs.api.v1.FileRequest;
import de.dereingerostete.sfs.controller.DownloadController;
import de.dereingerostete.sfs.error.RestError;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicAuthUtils {
	private static final @NotNull Logger LOGGER = StaticFileServerApplication.getLogger();
	private static final @NotNull RefreshingConfig USER_CONFIG = StaticFileServerApplication.getUserConfig();

	@Nullable
	public static ResponseEntity<Object> handleAuthorizedRequest(@NotNull HttpServletRequest servletRequest) {
		return handleAuthorizedRequest(servletRequest, null);
	}

	@Nullable
	public static ResponseEntity<Object> handleAuthorizedRequest(@NotNull HttpServletRequest servletRequest,
														   @Nullable FileRequest fileRequest) {
		String address = servletRequest.getRemoteAddr();
		RateLimiter limiter = RateLimiter.get();
		if (limiter.cannotAccess(address)) {
			long timeLeft = limiter.getTimeLeft(address);
			return limiter.createResponse(timeLeft);
		}

		String authentication = servletRequest.getHeader("Authorization");
		if (authentication == null) return createUnauthorizedError("No authentication was specified");

		String decoded;
		try {
			Base64.Decoder decoder = Base64.getDecoder();
			String credentials = authentication.substring(authentication.indexOf("Basic ") + 6);
			decoded = new String(decoder.decode(credentials), StandardCharsets.UTF_8);

			String[] parts = decoded.split(":", 2);
			if (parts.length != 2) return createUnauthorizedError("Invalid authentication");

			String password = USER_CONFIG.getString(parts[0], null);
			if (password == null || !password.equals(parts[1])) return createUnauthorizedError("Invalid Username or Password");
			else if (fileRequest == null) return null;
		} catch (RuntimeException exception) {
			LOGGER.info("User (" + address + ") used illegal authentication: " + exception.getMessage());
			return ResponseEntity.badRequest()
					.contentType(MediaType.APPLICATION_JSON)
					.body(RestError.badRequest("Invalid authentication"));
		}

		String fileName = fileRequest.getFileName();
		if (!DownloadController.existsFile(fileName)) {
			return ResponseEntity.badRequest()
					.body(RestError.badRequest("The target file does not exists"));
		} else return null;
	}

	@NotNull
	private static ResponseEntity<Object> createUnauthorizedError(@NotNull String message) {
		return ResponseEntity.status(401)
				.header("WWW-Authenticate", "Basic realm=\"API V1\", charset=\"UTF-8\"")
				.contentType(MediaType.APPLICATION_JSON)
				.body(RestError.unauthorized(message));
	}

}
