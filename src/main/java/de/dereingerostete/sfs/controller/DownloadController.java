package de.dereingerostete.sfs.controller;

import de.dereingerostete.sfs.StaticFileServerApplication;
import de.dereingerostete.sfs.error.RestError;
import de.dereingerostete.sfs.util.RateLimiter;
import de.dereingerostete.sfs.util.RefreshingConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

@RestController
public class DownloadController {
    public static final @NotNull File DOWNLOAD_DIRECTORY = new File("files");
    private final @NotNull RefreshingConfig passwordConfig = StaticFileServerApplication.getTokenConfig();
    private final @NotNull Tika tika = new Tika();
    private final CharsetEncoder charsetEncoder;

    public DownloadController() {
        if (!DOWNLOAD_DIRECTORY.exists() && !DOWNLOAD_DIRECTORY.mkdir())
            throw new IllegalStateException("Failed to create directory");
        charsetEncoder = StandardCharsets.ISO_8859_1.newEncoder();
    }

    @SuppressWarnings("unchecked")
    @RequestMapping(path = "/preview", method = RequestMethod.GET)
    public ResponseEntity<Object> preview(@RequestParam(required = false) @Nullable String fileName,
										   @RequestParam(required = false) @Nullable String token,
                                           @NotNull HttpServletRequest request) throws IOException {
        Object response = handleRequest(fileName, token, request);
        if (response instanceof ResponseEntity<?>) return (ResponseEntity<Object>) response;
        if (!(response instanceof File file)) throw new IllegalStateException(response.getClass().getName());

        fileName = file.getName();
        String mime = tika.detect(file);
        if (mime == null) {
            String extension = FilenameUtils.getExtension(fileName).toLowerCase();
            return ResponseEntity.badRequest().body(RestError.unsupportedExtension(extension));
        }

        MediaType mediaType = MediaType.parseMediaType(mime);
        InputStream inputStream = FileUtils.openInputStream(file);
        InputStreamResource resource = new InputStreamResource(inputStream);
        return ResponseEntity.ok()
                .contentLength(file.length())
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "filename=" + fileName)
                .body(resource);
    }

    @SuppressWarnings("unchecked")
    @RequestMapping(path = "/download", method = RequestMethod.GET)
    public ResponseEntity<Object> download(@RequestParam(required = false) @Nullable String fileName,
										   @RequestParam(required = false) @Nullable String token,
                                           @NotNull HttpServletRequest request) throws IOException {
        Object response = handleRequest(fileName, token, request);
        if (response instanceof ResponseEntity<?>) return (ResponseEntity<Object>) response;
        if (!(response instanceof File file)) throw new IllegalStateException(response.getClass().getName());

        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);

        InputStream inputStream = FileUtils.openInputStream(file);
        InputStreamResource resource = new InputStreamResource(inputStream);
        return ResponseEntity.ok()
                .headers(header)
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @NotNull
    protected Object handleRequest(@Nullable String fileName, @Nullable String password,
								   @NotNull HttpServletRequest request) {
        String address = request.getRemoteAddr();
        RateLimiter limiter = RateLimiter.get();
        if (limiter.cannotAccess(address)) {
            long timeLeft = limiter.getTimeLeft(address);
            return limiter.createResponse(timeLeft);
        }

        if (fileName == null) return ResponseEntity.badRequest().body(RestError.missingFileParameter());
        else if (isInvalid(fileName)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(RestError.forbidden()); //Prevent directory traversal
        }

        //No filename was entered
        if (fileName.isBlank()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(RestError.missingFileParameter());
        }

        File file = new File(DOWNLOAD_DIRECTORY, Objects.requireNonNull(fileName));
        if (isInvalidDirectory(file)) return ResponseEntity.status(403)
                .contentType(MediaType.APPLICATION_JSON)
                .body(RestError.forbidden()); //Prevent directory traversal
        if (!file.exists()) return ResponseEntity.status(404).body(RestError.notFoundError());

		Set<String> tokens = passwordConfig.getTokens(file.getName());
		if (tokens == null) return file;
		else if (password == null) return ResponseEntity.status(401)
				.contentType(MediaType.APPLICATION_JSON)
				.body(RestError.unauthorized("No token was specified"));

		if (tokens.contains(password)) return file;
		else return ResponseEntity.status(401)
				.contentType(MediaType.APPLICATION_JSON)
				.body(RestError.unauthorized("The specified token is invalid"));
    }

    protected boolean isInvalid(@NotNull String string) {
        if (!charsetEncoder.canEncode(string)) return true;
        else if (string.matches("[^a-zA-Z0-9_.-]")) return true;
        else if (string.contains("..")) return true;
        for (byte b : string.getBytes(StandardCharsets.US_ASCII)) {
            if (b < 32 || b == 127) return true;
        }
        return false;
    }

    protected boolean isInvalidDirectory(@NotNull File file) {
        return !DOWNLOAD_DIRECTORY.equals(file.getParentFile());
    }

    public static boolean existsFile(@NotNull String fileName) {
        return new File(DOWNLOAD_DIRECTORY, fileName).exists();
    }

}
