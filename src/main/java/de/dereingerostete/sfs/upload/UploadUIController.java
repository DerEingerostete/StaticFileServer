/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.upload;

import de.dereingerostete.sfs.StaticFileServerApplication;
import de.dereingerostete.sfs.util.RateLimiter;
import de.dereingerostete.sfs.util.RefreshingConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class UploadUIController {
	private static final @NotNull Logger LOGGER = StaticFileServerApplication.getLogger();
	private static final @NotNull UploadAuthenticator AUTHENTICATOR = new UploadAuthenticator();

	@RequestMapping(value = "/upload", method = RequestMethod.GET)
	public String getLogin(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
						   @NotNull Model model) {
		if (!AUTHENTICATOR.isValid(request, response)) {
			model.addAttribute("form", new UploadLoginForm());
			model.addAttribute("failed", false);
			return "upload-login";
		}

		AUTHENTICATOR.refreshToken(request, response);
		return "upload";
	}

	@RequestMapping(value = "/upload",
			method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
	)
	public Object authorize(@ModelAttribute @NotNull UploadLoginForm form,
							@NotNull Model model,
							@NotNull HttpServletRequest request,
							@NotNull HttpServletResponse response) {
		String address = request.getRemoteAddr();
		RateLimiter limiter = RateLimiter.get();
		if (limiter.cannotAccess(address)) {
			long timeLeft = limiter.getTimeLeft(address);
			return limiter.createResponse(timeLeft);
		}

		String username = form.getUsername();
		String enteredPassword = form.getPassword();
		if (username == null || enteredPassword == null) {
			LOGGER.info("Upload: Username or password null");
			model.addAttribute("form", new UploadLoginForm());
			model.addAttribute("failed", true);
			return "upload-login";
		}

		RefreshingConfig config = StaticFileServerApplication.getUserConfig();
		String password = config.getString(username, null);
		if (password == null || !password.equals(enteredPassword)) {
			LOGGER.info("Upload: Password invalid or no user found");
			model.addAttribute("form", new UploadLoginForm());
			model.addAttribute("failed", true);
			return "upload-login";
		}

		AUTHENTICATOR.generateToken(response);
		return "upload";
	}

	@NotNull
	public static UploadAuthenticator getAuthenticator() {
		return AUTHENTICATOR;
	}

}
