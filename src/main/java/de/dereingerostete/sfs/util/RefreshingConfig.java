package de.dereingerostete.sfs.util;

import de.dereingerostete.sfs.StaticFileServerApplication;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RefreshingConfig {
	private static final @NotNull Logger LOGGER = StaticFileServerApplication.getLogger();
	private final @NotNull File file;
	private final @NotNull WatchService service;
	private final @NotNull WatchKey registerWatchKey;
	private @NotNull @Getter JSONObject rootObject;

	public RefreshingConfig(@NotNull File file) throws IOException {
		this.file = file;

		String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
		rootObject = new JSONObject(fileContent);

		service = FileSystems.getDefault().newWatchService();
		Path directory = file.getAbsoluteFile().getParentFile().toPath();
		registerWatchKey = directory.register(service, StandardWatchEventKinds.ENTRY_MODIFY);
		LOGGER.info("Registered file watcher for file '" + file.getName() + "' in path '" + directory + "'");

		ExecutorService executorService = Executors.newFixedThreadPool(1);
		executorService.submit(() -> {
			try {
				WatchKey key;
				while ((key = service.take()) != null) {
					for (WatchEvent<?> event : key.pollEvents()) {
						WatchEvent.Kind<?> kind = event.kind();
						File changedFile = ((Path) event.context()).toFile();
						if (!changedFile.equals(file)) continue;

						try {
							if (StandardWatchEventKinds.ENTRY_MODIFY.equals(kind)) {
								LOGGER.info("Refreshing config (" + file.getName() + ") after file change");
								refresh();
								break;
							}
						} catch (IOException exception) {
							LOGGER.warn("Failed to refresh config", exception);
						}
					}
					key.reset();
				}
			} catch (InterruptedException exception) {
				LOGGER.warn("Failed to watch for file event in config '" + file.getName() + "'", exception);
			}
		});
	}

	@Nullable
	public Set<String> getTokens(@NotNull String key) {
		JSONArray array = rootObject.optJSONArray(key);
		if (array == null) return null;

		Set<String> list = new HashSet<>();
		array.forEach(object -> {
			if (object instanceof String string) list.add(string);
		});
		return list;
	}

	@Nullable
	public String getString(@NotNull String key, @Nullable String defaultValue) {
		return rootObject.optString(key, defaultValue);
	}

	public void put(@NotNull String key, @NotNull Object object) {
		rootObject.put(key, object);
	}

	@Nullable
	public Object remove(@NotNull String key) {
		return rootObject.remove(key);
	}

	public void refresh() throws IOException {
		String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
		rootObject = new JSONObject(fileContent);
	}

	public void close() throws IOException {
		registerWatchKey.cancel();
		service.close();
	}

	public void save() throws IOException {
		String jsonString = rootObject.toString(4);
		FileUtils.write(file, jsonString, StandardCharsets.UTF_8, false);
	}

}
