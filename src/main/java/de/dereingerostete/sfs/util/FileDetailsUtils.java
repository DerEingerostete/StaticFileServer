/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.util;

import de.dereingerostete.sfs.StaticFileServerApplication;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class FileDetailsUtils {
	private static final @NotNull DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;
	private static final @NotNull Logger LOGGER = StaticFileServerApplication.getLogger();

	@Nullable
	public static String getFileCreationDate(@NotNull File file) {
		try {
			Path path = file.toPath();
			LinkOption option = LinkOption.NOFOLLOW_LINKS;
			BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, option);
			Instant instant = attributes.creationTime().toInstant();
			return FORMATTER.format(instant);
		} catch (IOException exception) {
			LOGGER.warn("Failed to get file details", exception);
			return null;
		}
	}

	@NotNull
	public static String getFormattedFileSize(@NotNull File file) {
		long byteCount = file.length();
		return FileUtils.byteCountToDisplaySize(byteCount);
	}

	public static boolean isIllegalFile(@NotNull String fileName) {
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

}
