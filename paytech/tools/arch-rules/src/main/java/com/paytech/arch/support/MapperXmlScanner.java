package com.paytech.arch.support;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描 MyBatis Mapper XML 中引用的 {@code t_} 表名。
 *
 * @author Claude
 */
public final class MapperXmlScanner {

    private static final Pattern TABLE = Pattern.compile("(?<![A-Za-z0-9_$.#{])t_[a-z0-9_]+");

    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private MapperXmlScanner() {
    }

    /**
     * 列出模块资源目录下的 Mapper XML（根元素为 {@code <mapper}）。
     *
     * @param moduleRoot 模块根目录
     * @param resourceDirectory 资源目录，如 {@code src/main/resources}
     * @return Mapper XML 文件
     */
    public static List<Path> mapperFiles(Path moduleRoot, String resourceDirectory) {
        return ModuleFiles.list(moduleRoot, resourceDirectory, ".xml").stream()
            .filter(file -> ModuleFiles.read(file).contains("<mapper"))
            .toList();
    }

    /**
     * 返回 XML 中引用的表名及其行号（注释内的不计）。
     *
     * @param file Mapper XML
     * @return 表引用
     */
    public static List<TableReference> tableReferences(Path file) {
        String text = ModuleFiles.read(file);
        String withoutComments = blankComments(text);
        List<TableReference> references = new ArrayList<>();
        Matcher matcher = TABLE.matcher(withoutComments);
        while (matcher.find()) {
            references.add(new TableReference(matcher.group(), ModuleFiles.lineOf(text, matcher.start())));
        }
        return references;
    }

    private static String blankComments(String text) {
        Matcher matcher = XML_COMMENT.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String blanked = matcher.group().replaceAll("[^\n]", " ");
            matcher.appendReplacement(result, Matcher.quoteReplacement(blanked));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Mapper XML 中的一次表引用。
     *
     * @param table 表名
     * @param line 行号
     * @author Claude
     */
    public record TableReference(String table, int line) {
    }
}
