package com.paytech.migration.cli;

import com.paytech.migration.service.RunnerConfigurationException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析后的命令行参数。
 *
 * @param command 子命令；{@code --help} 时为 null
 * @param manifest 实例清单文件
 * @param sqlRoot paytech 目录（其下为 {@code <service>/db/migrations}）
 * @param service 只处理的服务；未指定为 null
 * @param target migrate 的目标版本覆盖；未指定为 null
 * @param json 是否输出 JSON
 * @author Claude
 */
public record CliArguments(MigrationCommand command, Path manifest, Path sqlRoot, String service, Integer target,
    boolean json) {

    private static final Set<String> VALUE_OPTIONS = Set.of("--manifest", "--sql-root", "--service", "--target");

    /**
     * 是否请求 JSON 输出；用于参数解析失败时仍按要求的格式报告错误。
     *
     * @param args 原始参数
     * @return 含 {@code --json} 返回 true
     */
    public static boolean requestsJson(String... args) {
        return Arrays.asList(args).contains("--json");
    }

    /**
     * 是否请求帮助。
     *
     * @param args 原始参数
     * @return 无参数或含 {@code --help}、{@code -h} 返回 true
     */
    public static boolean requestsHelp(String... args) {
        List<String> values = Arrays.asList(args);
        return values.isEmpty() || values.contains("--help") || values.contains("-h");
    }

    /**
     * 解析参数。
     *
     * @param args 原始参数：{@code <command> --manifest <yaml> --sql-root <dir> [--service <name>] [--target <n>] [--json]}
     * @return 参数
     * @throws RunnerConfigurationException 参数错误
     */
    public static CliArguments parse(String... args) {
        MigrationCommand command = MigrationCommand.fromCode(args[0]).orElseThrow(() ->
            new RunnerConfigurationException("未知子命令: " + args[0] + "，可用 plan、validate、migrate、seed"));
        Map<String, String> options = options(Arrays.copyOfRange(args, 1, args.length));
        Integer target = options.containsKey("--target") ? positiveInteger(options.get("--target")) : null;
        if (target != null && (command != MigrationCommand.MIGRATE || !options.containsKey("--service"))) {
            throw new RunnerConfigurationException("--target 只用于 migrate，且必须同时指定 --service");
        }
        return new CliArguments(command, Path.of(required(options, "--manifest")), Path.of(required(options,
            "--sql-root")), options.get("--service"), target, options.containsKey("--json"));
    }

    /**
     * 解析选项；每个选项最多出现一次。
     *
     * @param args 子命令之后的参数
     * @return 选项到值（{@code --json} 的值为空字符串）
     */
    static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            String option = args[index];
            boolean takesValue = VALUE_OPTIONS.contains(option);
            if (!takesValue && !option.equals("--json")) {
                throw new RunnerConfigurationException("未知参数: " + option);
            }
            if (takesValue && index + 1 >= args.length) {
                throw new RunnerConfigurationException(option + " 缺少取值");
            }
            String value = takesValue ? args[++index] : "";
            if (options.putIfAbsent(option, value) != null) {
                throw new RunnerConfigurationException(option + " 重复");
            }
        }
        return options;
    }

    /**
     * 读取必填选项。
     *
     * @param options 选项
     * @param name 选项名
     * @return 值
     */
    static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new RunnerConfigurationException("缺少必填参数 " + name);
        }
        return value;
    }

    /**
     * 解析正整数。
     *
     * @param value 文本
     * @return 正整数
     */
    static int positiveInteger(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number >= 1) {
                return number;
            }
        } catch (NumberFormatException exception) {
            // 与非正数一并在下方报告。
        }
        throw new RunnerConfigurationException("--target 必须是正整数: " + value);
    }
}
