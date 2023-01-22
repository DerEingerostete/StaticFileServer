package de.dereingerostete.sfs.upload;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.dereingerostete.sfs.StaticFileServerApplication;
import de.dereingerostete.sfs.controller.DownloadController;
import de.dereingerostete.sfs.error.RestError;
import de.dereingerostete.sfs.util.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

@RestController
public class UploadAPIController {
	private static final @NotNull UploadAuthenticator AUTHENTICATOR = UploadUIController.getAuthenticator();
	private static final @NotNull Logger LOGGER = StaticFileServerApplication.getLogger();
	private static final @NotNull String PATH_PREFIX = "/api/filepond/";
	private final @NotNull Cache<String, UploadProcess> uploadsMap;

	public UploadAPIController() {
		this.uploadsMap = Caffeine.newBuilder()
				.expireAfterAccess(Duration.ofHours(2))
				.removalListener((key, value, cause) -> {
					if (value instanceof UploadProcess process) process.close();
				}).build();
	}

	@RequestMapping(value = PATH_PREFIX + "process",
			method = RequestMethod.POST,
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	public ResponseEntity<Object> upload(@NotNull HttpServletRequest request,
										 @NotNull HttpServletResponse response,
										 @RequestParam(value = "filepond", required = false)
										 @Nullable MultipartFile multipartFile) {
		ResponseEntity<Object> authResponse = handleRequest(request, response);
		if (authResponse != null) return authResponse;

		try {
			if (multipartFile != null) {
				String fileName = multipartFile.getOriginalFilename();
				if (fileName == null || isIllegalFile(fileName)) return createIllegalFileResponse();
				else if (isAlreadyUploaded(fileName)) return createFileExistsResponse();
				LOGGER.info("Uploading whole file: " + fileName);

				UploadProcess process = new UploadProcess();
				process.setResultFile(new File(DownloadController.DOWNLOAD_DIRECTORY, fileName));
				process.handleSingle(multipartFile);

				String id = process.getId();
				uploadsMap.put(id, process);
				return ResponseEntity.ok()
						.contentType(MediaType.TEXT_PLAIN)
						.body(id);
			} else {
				UploadProcess process = new UploadProcess();
				String id = process.getId();
				uploadsMap.put(id, process);
				LOGGER.info("Started chunked Upload with id " + id);
				return ResponseEntity.ok()
						.contentType(MediaType.TEXT_PLAIN)
						.body(id);
			}
		} catch (IOException exception) {
			LOGGER.warn("Failed to handle upload", exception);
			return ResponseEntity.internalServerError()
					.contentType(MediaType.APPLICATION_JSON)
					.body(RestError.internalServerError("Failed to handle upload"));
		}
	}

	@RequestMapping(value = PATH_PREFIX + "patch",
			method = RequestMethod.PATCH,
			consumes = "application/offset+octet-stream"
	)
	public ResponseEntity<Object> patch(@RequestParam(name = "patch") @NotNull String id,
										@RequestBody byte[] data,
										@NotNull HttpServletRequest request,
										@NotNull HttpServletResponse response) {
		ResponseEntity<Object> authResponse = handleRequest(request, response);
		if (authResponse != null) return authResponse;

		UploadProcess process = uploadsMap.getIfPresent(id);
		if (process == null) return createInvalidIdResponse();

		try {
			process.nextChunk(request, data);
			return ResponseEntity.ok().build();
		} catch (IOException exception) {
			LOGGER.warn("Failed to handle chunk", exception);
			return ResponseEntity.internalServerError()
					.contentType(MediaType.APPLICATION_JSON)
					.body(RestError.badRequest("Failed to handle chunk"));
		}
	}

	@RequestMapping(
			value = PATH_PREFIX + "patch",
			method = RequestMethod.HEAD
	)
	public ResponseEntity<Object> restartChunked(@RequestParam(name = "patch") @NotNull String id,
												 @NotNull HttpServletRequest request,
												 @NotNull HttpServletResponse response) {
		ResponseEntity<Object> authResponse = handleRequest(request, response);
		if (authResponse != null) return authResponse;

		UploadProcess process = uploadsMap.getIfPresent(id);
		if (process == null) return createInvalidIdResponse();

		long offset = process.getCurrentOffset();
		return ResponseEntity.ok()
				.header("Upload-Offset", String.valueOf(offset))
				.build();
	}

	@RequestMapping(value = PATH_PREFIX + "revert",
			method = {RequestMethod.DELETE, RequestMethod.POST},
			consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<Object> revert(@RequestBody @NotNull String id,
										 @NotNull HttpServletRequest request,
										 @NotNull HttpServletResponse response) {
		ResponseEntity<Object> authResponse = handleRequest(request, response);
		if (authResponse != null) return authResponse;

		UploadProcess process = uploadsMap.getIfPresent(id);
		if (process == null) return createInvalidIdResponse();

		LOGGER.info("Reverting upload with id " + id);
		if (process.revert()) {
			uploadsMap.invalidate(id);
			return ResponseEntity.ok().build();
		} else return ResponseEntity.internalServerError()
				.contentType(MediaType.APPLICATION_JSON)
				.body(RestError.internalServerError("Failed to revert upload"));
	}

	@Nullable
	private ResponseEntity<Object> handleRequest(@NotNull HttpServletRequest request,
												 @NotNull HttpServletResponse response) {
		String address = request.getRemoteAddr();
		RateLimiter limiter = RateLimiter.get();
		if (limiter.cannotAccess(address)) {
			long timeLeft = limiter.getTimeLeft(address);
			return limiter.createResponse(timeLeft);
		}

		if (AUTHENTICATOR.isValid(request, response)) return null;
		else return AUTHENTICATOR.createUnauthorizedError("Unauthorized");
	}

	private boolean isIllegalFile(@NotNull String fileName) {
		if (fileName.contains("..")) return true;
		String[] illegalChars = {"/", "<", ">", ":", "\"", "\\", "|", "?", "*", "\0"};
		for (String illegalChar : illegalChars) {
			if (fileName.contains(illegalChar)) return true;
		}

		String uppercaseName = fileName.toUpperCase();
		String[] illegalNames = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2",
				"COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1",
				"LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
		for (String illegalName : illegalNames) {
			if (illegalName.equals(uppercaseName)) return true;
		}

		if (fileName.endsWith(".") || fileName.endsWith(" ") || fileName.startsWith(".")) return false;
		for (char c : fileName.toCharArray()) {
			if (c <= 31) return true;
		}

		return fileName.equalsIgnoreCase("desktop.ini");
	}

	private boolean isAlreadyUploaded(@NotNull String fileName) {
		return new File(DownloadController.DOWNLOAD_DIRECTORY, fileName).exists();
	}

	@NotNull
	private ResponseEntity<Object> createIllegalFileResponse() {
		return ResponseEntity.badRequest().body(RestError.badRequest("Illegal filename"));
	}

	@NotNull
	private ResponseEntity<Object> createFileExistsResponse() {
		return ResponseEntity.status(409)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new RestError(409, "Conflict", "File already exists"));
	}

	@NotNull
	private ResponseEntity<Object> createInvalidIdResponse() {
		return ResponseEntity.status(401)
				.contentType(MediaType.APPLICATION_JSON)
				.body(RestError.unauthorized("Invalid upload id"));
	}

}