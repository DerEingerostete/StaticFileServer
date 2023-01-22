/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.api.v1;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@AllArgsConstructor
public class UnprotectRequest implements FileRequest {
	private final @NotNull String fileName;
	private @Nullable List<String> tokens;

}
