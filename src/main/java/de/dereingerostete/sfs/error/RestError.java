/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.error;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;

@Data
@AllArgsConstructor
public class RestError {
    private final long timestamp = System.currentTimeMillis();
    private final int status;
    private final @NotNull String error;
    private final @NotNull String message;
    private final @Nullable AdditionalInfo[] additionalInformation;

    public RestError(int status, @NotNull String error, @NotNull String message) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.additionalInformation = new AdditionalInfo[0];
    }

    @NotNull
    public static RestError forbidden() {
        return new RestError(403, "Forbidden", "Directory traversal blocked");
    }

    @NotNull
    public static RestError unauthorized(@NotNull String message) {
        return new RestError(401, "Unauthorized", message);
    }

    @NotNull
    public static RestError missingFileParameter() {
        return badRequest("Missing fileName parameter");
    }

    @NotNull
    public static RestError unsupportedExtension(@NotNull String extension) {
        return badRequest("Extension '" + extension + "' is not supported");
    }

    @NotNull
    public static RestError unsupportedMediaType(@Nullable MediaType mediaType) {
        String message = mediaType == null ? "" : "Content-Type '" + mediaType + "' is not supported";
        return new RestError(415, "Unsupported Media Type", message);
    }

    @NotNull
    public static RestError badRequest(@NotNull String message) {
        return new RestError(400, "Bad Request", message);
    }

    @NotNull
    public static RestError notFoundError() {
        return new RestError(404, "Not Found", "No file found with the given name");
    }

    @NotNull
    public static RestError internalServerError(@NotNull String message) {
        return new RestError(500, "Internal Server Error", message);
    }

    @NotNull
    public static RestError unknownError() {
        return new RestError(-1, "Unknown error", "An unexpected error occurred");
    }

    @NotNull
    public static RestError tooManyRequests(long retryAfter) {
        AdditionalInfo[] objects = {new RetryAfter(retryAfter)};
        return new RestError(429, "Too Many Requests", "Rate limit has been exceeded", objects);
    }

}
