/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs;

import de.dereingerostete.sfs.util.RefreshingConfig;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class StaticFileServerApplication {
    private static final @NotNull Logger LOGGER = LoggerFactory.getLogger("StaticFileServer");
    private static @Getter RefreshingConfig tokenConfig;
    private static @Getter RefreshingConfig userConfig;

    public static void main(String[] args) {
        try {
            File passwordFile = new File("password-protected.json");
            if (!passwordFile.exists()) FileUtils.write(passwordFile, "{}", StandardCharsets.UTF_8);
            tokenConfig = new RefreshingConfig(passwordFile);
        } catch (IOException exception) {
            LOGGER.error("Failed to load token config", exception);
            System.exit(1);
        }

        try {
            File file = new File("api-v1-passwords.json");
            if (!file.exists()) FileUtils.write(file, "{}", StandardCharsets.UTF_8);
            userConfig = new RefreshingConfig(file);
        } catch (IOException exception) {
            LOGGER.error("Failed to load password config", exception);
            System.exit(1);
        }

        SpringApplication.run(StaticFileServerApplication.class, args);
    }

    @NotNull
    public static Logger getLogger() {
        return LOGGER;
    }

}
