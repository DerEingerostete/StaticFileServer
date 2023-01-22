package de.dereingerostete.sfs.api.v1;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data
@AllArgsConstructor
public class ProtectRequest implements FileRequest {
	private final @NotNull String fileName;
	private final @NotNull List<String> tokens;
	private boolean replace;

}
