package com.paytech.migration.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 测试资源定位与复制。
 *
 * @author Claude
 */
public final class Fixtures {

    private Fixtures() {
    }

    /**
     * 类路径资源的文件路径。
     *
     * @param resource 资源路径，如 {@code migrations/executor}
     * @return 文件路径
     */
    public static Path resource(String resource) {
        URL url = Fixtures.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalArgumentException("测试资源不存在: " + resource);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(resource, exception);
        }
    }

    /**
     * 把一个 fixture 目录复制到临时目录，供需要修改文件的用例使用。
     *
     * @param resource fixture 目录
     * @param destination 临时目录
     * @return 复制后的根目录
     */
    public static Path copy(String resource, Path destination) {
        Path source = resource(resource);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return destination;
    }
}
