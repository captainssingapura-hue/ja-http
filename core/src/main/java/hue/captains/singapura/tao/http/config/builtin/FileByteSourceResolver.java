package hue.captains.singapura.tao.http.config.builtin;

import hue.captains.singapura.tao.http.config.ByteSourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Built-in resolver for {@link FileByteSource}. */
public final class FileByteSourceResolver implements ByteSourceResolver<FileByteSource> {

    @Override
    public Class<FileByteSource> specType() {
        return FileByteSource.class;
    }

    @Override
    public byte[] resolve(FileByteSource spec) throws IOException {
        return Files.readAllBytes(Path.of(spec.path()));
    }
}
