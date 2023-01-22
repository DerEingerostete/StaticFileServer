/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.upload;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
public class UploadLoginForm {
	private @Nullable String username;
	private @Nullable String password;

}