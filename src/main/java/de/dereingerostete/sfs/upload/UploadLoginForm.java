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