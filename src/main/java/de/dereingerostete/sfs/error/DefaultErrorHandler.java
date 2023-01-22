/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
@RestControllerAdvice
public class DefaultErrorHandler implements ErrorController {
    protected final @NotNull JSONObject errorObject;

    public DefaultErrorHandler() {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("error_messages.json");
            if (inputStream == null) throw new IllegalStateException("Error message json not found");
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            errorObject = new JSONObject(content);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public RestError handleError(@NotNull HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (!(status instanceof Integer statusInteger)) {
            return RestError.unknownError();
        }

        String code = String.valueOf(status);
        JSONObject messageObject = errorObject.optJSONObject(code);
        if (messageObject == null) {
            return RestError.unknownError();
        }

        String error = messageObject.optString("error", null);
        String message = messageObject.optString("message", null);
        if (message == null || error == null) return RestError.unknownError();
        else return new RestError(statusInteger, error, message);
    }

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<RestError> handleException(@NotNull HttpMediaTypeNotSupportedException exception) {
		MediaType mediaType = exception.getContentType();
		return ResponseEntity
				.status(415)
				.body(RestError.unsupportedMediaType(mediaType));
	}

}
