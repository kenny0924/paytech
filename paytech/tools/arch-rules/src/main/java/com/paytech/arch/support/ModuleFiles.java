package com.paytech.arch.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 模块源码与资源文件的遍历工具。
 *
 * @author Claude
 */
public final class ModuleFiles {

    private ModuleFiles() {
    }

    /**
     * 列出模块下指定相对目录中扩展名匹配的文件；目录不存在时返回空。
     *
     * @param moduleRoot 模块根目录
     * @param relativeDirectory 相对目录，如 {@code src/main/java}
     * @param extension 扩展名，如 {@code .java}
     * @return 排序后的文件
     */
    public static List<Path> list(Path moduleRoot, String relativeDirectory, String extension) {
        Path directory = moduleRoot.resolve(relativeDirectory);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(extension))
                .sorted()
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("目录遍历失败: " + directory, exception);
        }
    }

    /**
     * 读取 UTF-8 文本。
     *
     * @param file 文件
     * @return 文本
     */
    public static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("文件读取失败: " + file, exception);
        }
    }

    /**
     * 返回字符偏移所在行号（从 1 开始）。
     *
     * @param text 文本
     * @param offset 偏移
     * @return 行号
     */
    public static int lineOf(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset && index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }
}
