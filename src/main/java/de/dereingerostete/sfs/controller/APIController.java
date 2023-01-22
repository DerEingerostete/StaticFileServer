/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.controller;

import de.dereingerostete.sfs.StaticFileServerApplication;
import de.dereingerostete.sfs.api.v1.ProtectRequest;
import de.dereingerostete.sfs.api.v1.UnprotectRequest;
import de.dereingerostete.sfs.error.RestError;
import de.dereingerostete.sfs.util.BasicAuthUtils;
import de.dereingerostete.sfs.util.FileDetailsUtils;
import de.dereingerostete.sfs.util.RefreshingConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@RestController
public class APIController {
	private static final @NotNull Logger LOGGER = StaticFileServerApplication.getLogger();
	public static final @NotNull String PATH_PREFIX = "/api/v1/";

	@RequestMapping(value = PATH_PREFIX + "protect", method = RequestMethod.POST)
	public ResponseEntity<Object> protect(@RequestBody ProtectRequest protectRequest,
											 @NotNull HttpServletRequest request) {
		ResponseEntity<Object> authResponse = BasicAuthUtils.handleAuthorizedRequest(request, protectRequest);
		if (authResponse != null) return authResponse;

		String fileName = protectRequest.getFileName();
		RefreshingConfig passwordConfig = StaticFileServerApplication.getTokenConfig();

		Set<String> tokens = passwordConfig.getTokens(fileName);
		if (tokens == null || protectRequest.isReplace()) passwordConfig.put(fileName, protectRequest.getTokens());
		else {
			tokens.addAll(protectRequest.getTokens());
			passwordConfig.put(fileName, tokens);
		}

		try {
			passwordConfig.save();
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_JSON)
					.build();
		} catch (IOException exception) {
			LOGGER.warn("Failed to save password config", exception);
			return ResponseEntity.internalServerError().body(RestError.internalServerError("Failed to protect file"));
		}
	}

	@RequestMapping(value = PATH_PREFIX + "unprotect", method = RequestMethod.POST)
	public ResponseEntity<Object> unprotect(@RequestBody UnprotectRequest unprotectRequest,
											@NotNull HttpServletRequest request) {
		ResponseEntity<Object> authResponse = BasicAuthUtils.handleAuthorizedRequest(request, unprotectRequest);
		if (authResponse != null) return authResponse;

		String fileName = unprotectRequest.getFileName();
		RefreshingConfig passwordConfig = StaticFileServerApplication.getTokenConfig();

		List<String> requestTokens = unprotectRequest.getTokens();
		Set<String> tokens = passwordConfig.getTokens(fileName);
		if (requestTokens == null) passwordConfig.remove(fileName);
		else if (tokens != null) {
			requestTokens.forEach(tokens::remove);
			passwordConfig.put(fileName, tokens);
		}

		try {
			passwordConfig.save();
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_JSON)
					.build();
		} catch (IOException exception) {
			LOGGER.warn("Failed to save password config", exception);
			return ResponseEntity.internalServerError().body(RestError.internalServerError("Failed to protect file"));
		}
	}

	@RequestMapping(value = PATH_PREFIX + "list", method = RequestMethod.POST)
	public ResponseEntity<Object> unprotect(@NotNull HttpServletRequest request) {
		ResponseEntity<Object> authResponse = BasicAuthUtils.handleAuthorizedRequest(request);
		if (authResponse != null) return authResponse;

		File[] files = DownloadController.DOWNLOAD_DIRECTORY.listFiles();
		if (files == null) {
			LOGGER.warn("Could not list files for api request");
			return ResponseEntity.internalServerError()
					.contentType(MediaType.APPLICATION_JSON)
					.body(RestError.internalServerError("Could not list files"));
		}

		RefreshingConfig config = StaticFileServerApplication.getTokenConfig();
		Set<String> keySet = config.getRootObject().keySet();
		JSONObject responseObject = new JSONObject();
		for (File file : files) {
			String fileName = file.getName();
			JSONObject fileJson = new JSONObject();
			fileJson.put("creation", FileDetailsUtils.getFileCreationDate(file));
			fileJson.put("formattedSize", FileDetailsUtils.getFormattedFileSize(file));
			fileJson.put("size", file.length());
			fileJson.put("requires-token", keySet.contains(fileName));
			responseObject.put(fileName, fileJson);
		}

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.body(responseObject.toString(4));
	}

}