/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.upload;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.dereingerostete.sfs.StaticFileServerApplication;
import de.dereingerostete.sfs.error.RestError;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

public class UploadAuthenticator {
	private static final @NotNull String AUTH_COOKIE_NAME = "sessionToken";
	private static final long COOKIE_MAX_AGE = Duration.ofHours(2).toMillis(); //2Hr
	private final @NotNull Cache<String, Long> tokenCache;
	private final @NotNull SecureRandom random;

	public UploadAuthenticator() {
		random = new SecureRandom();
		tokenCache = Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofMillis(COOKIE_MAX_AGE))
				.build();
	}

	@NotNull
	public String generateToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);

		Base64.Encoder encoder = Base64.getEncoder();
		String token = encoder.encodeToString(bytes);
		tokenCache.put(token, System.currentTimeMillis());
		return token;
	}

	public void generateToken(@NotNull HttpServletResponse response) {
		Cookie cookie = new Cookie(AUTH_COOKIE_NAME, generateToken());
		cookie.setAttribute("SameSite", "Strict");
		//cookie.setMaxAge(COOKIE_MAX_AGE); Let only the server handle this
		cookie.setHttpOnly(true);
		cookie.setSecure(true); //Disable for debug, enable for production
		response.addCookie(cookie);
	}

	public void refreshToken(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
		Cookie cookie = getSessionCookie(request);
		if (cookie == null) return;

		String token = cookie.getValue();
		Long creationTime = tokenCache.getIfPresent(token);
		if (creationTime == null || System.currentTimeMillis() < creationTime + (COOKIE_MAX_AGE / 2)) return;

		StaticFileServerApplication.getLogger().info("Regenerating old token");
		tokenCache.invalidate(token);
		generateToken(response);
	}

	public boolean isValid(@NotNull String token) {
		return tokenCache.getIfPresent(token) != null;
	}

	public boolean isValid(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
		Cookie sessionCookie = getSessionCookie(request);
		if (sessionCookie == null) return false;
		if (!isValid(sessionCookie.getValue())) {
			sessionCookie.setMaxAge(0);
			response.addCookie(sessionCookie);
			return false;
		}
		return true;
	}

	@NotNull
	public ResponseEntity<Object> createUnauthorizedError(@NotNull String message) {
		return ResponseEntity.status(401)
				.header("WWW-Authenticate", "Bearer realm=\"Filepond\", charset=\"UTF-8\"")
				.contentType(MediaType.APPLICATION_JSON)
				.body(RestError.unauthorized(message));
	}

	@Nullable
	private Cookie getSessionCookie(@NotNull HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) return null;
		for (Cookie cookie : cookies) {
			if (cookie.getName().equals(AUTH_COOKIE_NAME)) return cookie;
		}
		return null;
	}

}
