package com.paytech.instance.manifest.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 定位 {@code src/test/resources} 下的清单样例与快照。
 *
 * @author Claude
 */
public final class ManifestSamples {

    private ManifestSamples() {
    }

    /**
     * 清单样例路径。
     *
     * @param name 文件名，如 {@code main.yaml}
     * @return 绝对路径
     */
    public static Path manifest(String name) {
        return resource("/manifests/" + name);
    }

    /**
     * 读取快照文本。
     *
     * @param name 文件名
     * @return 快照内容
     */
    public static String snapshot(String name) {
        try {
            return Files.readString(resource("/snapshots/" + name));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path resource(String path) {
        URL url = ManifestSamples.class.getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("测试资源不存在: " + path);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
