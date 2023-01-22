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
import de.dereingerostete.sfs.controller.DownloadController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.security.MD5Encoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Data
public class UploadProcess {
	private static final @NotNull ScheduledExecutorService SERVICE;
	private static final @NotNull File TEMP_ROOT;
	private static final @NotNull Random RANDOM;
	private static final @NotNull Logger LOGGER;
	private final @NotNull String id;
	private final @NotNull File tempDir;

	private final @NotNull Map<Long, File> chunks;
	private @Nullable File resultFile;
	private long totalLength;

	static {
		RANDOM = new Random();
		SERVICE = Executors.newScheduledThreadPool(2);
		LOGGER = StaticFileServerApplication.getLogger();
		String tempPath =  System.getProperty("java.io.tmpdir", null);
		if (tempPath == null) {
			TEMP_ROOT = new File("temp");
			if (!TEMP_ROOT.exists() && !TEMP_ROOT.mkdir())
				throw new IllegalStateException("Failed to create temp directory");
		} else TEMP_ROOT = new File(tempPath);
	}

	public UploadProcess() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		this.id = MD5Encoder.encode(bytes);
		this.tempDir = new File(TEMP_ROOT, id);
		this.chunks = new HashMap<>();

		this.totalLength = -1;
		this.resultFile = null;
	}

	public void handleSingle(@NotNull MultipartFile file) throws IOException {
		if (resultFile == null) throw new IOException("Result file is not set");
		InputStream inputStream = file.getInputStream();
		FileUtils.copyInputStreamToFile(inputStream, resultFile);
	}

	public void nextChunk(@NotNull HttpServletRequest request, byte[] data) throws IOException {
		if (totalLength == -1) totalLength = Long.parseLong(request.getHeader("Upload-Length"));
		if (resultFile == null) {
			String fileName = request.getHeader("Upload-Name");
			resultFile = new File(DownloadController.DOWNLOAD_DIRECTORY, fileName);
			LOGGER.info("Set filename of chunked upload with id '" + id + "' to '" + fileName + "'");
		}

		long offset = Long.parseLong(request.getHeader("Upload-Offset"));
		long chunkSize = request.getContentLengthLong();

		File file = new File(tempDir, "chunk-" + offset + "-" + System.currentTimeMillis());
		FileUtils.writeByteArrayToFile(file, data, false);
		chunks.put(offset, file);

		if (offset + chunkSize >= totalLength) {
			LOGGER.info("Chunked upload completed");
			File combinedFile = combineChunks();
			FileUtils.moveFile(combinedFile, resultFile);
			deleteTempDirectory();
		}
	}

	@NotNull
	public File combineChunks() throws IOException {
		LOGGER.info("Combining " + chunks.size() + " chunks");
		long startTime = System.currentTimeMillis();

		File combinedFile = new File(tempDir, "combined");
		RandomAccessFile randomAccessFile = new RandomAccessFile(combinedFile, "rw");
		for (Map.Entry<Long, File> entry : chunks.entrySet()) {
			File file = entry.getValue();
			byte[] chunkData = FileUtils.readFileToByteArray(file);

			long offset = entry.getKey();
			randomAccessFile.seek(offset);
			randomAccessFile.write(chunkData);
		}
		randomAccessFile.close();

		long took = System.currentTimeMillis() - startTime;
		LOGGER.info("Combining took " + (took / 1000.0) + "s");
		return combinedFile;
	}

	public boolean revert() {
		if (resultFile != null) {
			LOGGER.info("Deleting file: " + resultFile.getName());
			return !resultFile.exists() || FileUtils.deleteQuietly(resultFile);
		} else return false;
	}

	public long getCurrentOffset() {
		OptionalLong maxOffset = chunks.keySet().stream()
				.mapToLong(Long::longValue).max();
		return maxOffset.orElse(0L);
	}

	public void close() {
		String fileName = resultFile == null ? "null" : resultFile.getName();
		LOGGER.info("Closing upload process with id '" + id + "' and filename '" + fileName + "'");
		deleteTempDirectory();
	}

	private void deleteTempDirectory() {
		SERVICE.submit(() -> {
			try {
				if (tempDir.exists()) FileUtils.deleteDirectory(tempDir);
			} catch (IOException exception) {
				LOGGER.warn("Failed to delete temporary directory: " + tempDir.getAbsolutePath(), exception);
			}
		});
	}

}
