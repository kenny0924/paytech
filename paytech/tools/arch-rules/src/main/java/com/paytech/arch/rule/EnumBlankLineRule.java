package com.paytech.arch.rule;

import com.paytech.arch.model.MavenModule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.JavaSourceMask;
import com.paytech.arch.support.ModuleFiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R10：相邻枚举常量之间保留一个空行；下一项带 JavaDoc 或注解时空行位于其之前（DEVELOPMENT_CONVENTIONS §4）。
 *
 * <p>
 * 文本规则：在去掉注释与字符串的代码结构上定位枚举常量区（{@code enum X {} 至顶层 {@code ;} 或 {@code }}），检查每个顶层逗号
 * 之后到下一项之间恰好一个空行。同行尾随的 {@code //} 注释不计入空白。
 *
 * @author Claude
 */
public final class EnumBlankLineRule implements ArchitectureRule {

    private static final Pattern ENUM_DECLARATION = Pattern.compile("\\benum\\s+([A-Za-z_$][\\w$]*)");

    private static final List<String> SOURCE_DIRECTORIES = List.of("src/main/java", "src/test/java");

    @Override
    public String id() {
        return "R10";
    }

    @Override
    public String description() {
        return "枚举常量之间保留一个空行";
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        List<Path> files = new ArrayList<>();
        for (MavenModule module : scope.targetModules()) {
            for (String directory : SOURCE_DIRECTORIES) {
                files.addAll(ModuleFiles.list(module.root(), directory, ".java"));
            }
        }
        return checkFiles(files);
    }

    /**
     * 直接检查一组 Java 源文件（不依赖 Maven 模块）。
     *
     * @param files 源文件
     * @return 违规列表
     */
    public List<RuleViolation> checkFiles(Collection<Path> files) {
        List<RuleViolation> violations = new ArrayList<>();
        for (Path file : files) {
            violations.addAll(checkSource(file.toString(), ModuleFiles.read(file)));
        }
        return violations;
    }

    /**
     * 检查一段 Java 源码。
     *
     * @param sourceName 报告中的文件名
     * @param source 源码
     * @return 违规列表
     */
    public List<RuleViolation> checkSource(String sourceName, String source) {
        String code = JavaSourceMask.mask(source);
        List<RuleViolation> violations = new ArrayList<>();
        Matcher matcher = ENUM_DECLARATION.matcher(code);
        while (matcher.find()) {
            int bodyStart = code.indexOf('{', matcher.end());
            if (bodyStart < 0) {
                continue;
            }
            for (int separator : constantSeparators(code, bodyStart + 1)) {
                RuleViolation violation = checkSeparator(sourceName, matcher.group(1), source, separator);
                if (violation != null) {
                    violations.add(violation);
                }
            }
        }
        return violations;
    }

    private static List<Integer> constantSeparators(String code, int start) {
        List<Integer> separators = new ArrayList<>();
        int depth = 0;
        for (int index = start; index < code.length(); index++) {
            char current = code.charAt(index);
            if (current == '(' || current == '{' || current == '[') {
                depth++;
            } else if (current == ')' || current == ']' || (current == '}' && depth > 0)) {
                depth--;
            } else if (depth == 0 && (current == ';' || current == '}')) {
                return separators;
            } else if (depth == 0 && current == ',') {
                separators.add(index);
            }
        }
        return separators;
    }

    private RuleViolation checkSeparator(String sourceName, String enumName, String source, int separator) {
        int index = separator + 1;
        while (index < source.length() && (source.charAt(index) == ' ' || source.charAt(index) == '\t')) {
            index++;
        }
        if (source.startsWith("//", index)) {
            int lineEnd = source.indexOf('\n', index);
            index = lineEnd < 0 ? source.length() : lineEnd;
        }
        int newlines = 0;
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            newlines += source.charAt(index) == '\n' ? 1 : 0;
            index++;
        }
        if (index >= source.length() || source.charAt(index) == ';' || source.charAt(index) == '}') {
            return null;
        }
        if (newlines == 2) {
            return null;
        }
        String found = newlines < 2 ? "缺少空行" : "空行多于一行";
        return new RuleViolation(id(), sourceName + ":" + ModuleFiles.lineOf(source, index),
            "枚举 " + enumName + " 的相邻常量之间应恰好保留一个空行（" + found + "）");
    }
}
