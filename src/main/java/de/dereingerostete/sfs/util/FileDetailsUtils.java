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

}
