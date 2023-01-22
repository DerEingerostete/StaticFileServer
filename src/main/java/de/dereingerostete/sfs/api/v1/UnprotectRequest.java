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
