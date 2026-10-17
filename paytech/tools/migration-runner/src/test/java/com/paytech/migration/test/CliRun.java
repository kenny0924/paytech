package com.paytech.migration.test;

import com.paytech.migration.cli.MigrationRunnerCli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 一次真实 {@link MigrationRunnerCli#run(String...)} 调用的退出码与输出。
 *
 * @param exitCode 退出码
 * @param out 标准输出
 * @param err 标准错误
 * @author Claude
 */
public record CliRun(int exitCode, String out, String err) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * 以指定环境变量调用命令行入口。
     *
     * @param environment 环境变量
     * @param args 参数
     * @return 结果
     */
    public static CliRun invoke(Map<String, String> environment, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = new MigrationRunnerCli(environment, new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8)).run(args);
        return new CliRun(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /**
     * 断言退出码。
     *
     * @param expected 预期退出码
     * @return 本结果
     */
    public CliRun exit(int expected) {
        Assertions.assertEquals(expected, exitCode, () -> "stdout:\n" + out + "\nstderr:\n" + err);
        return this;
    }

    /**
     * 断言标准输出或标准错误包含文本。
     *
     * @param text 文本
     * @return 本结果
     */
    public CliRun contains(String text) {
        Assertions.assertTrue(out.contains(text) || err.contains(text),
            () -> "输出不含 \"" + text + "\"\nstdout:\n" + out + "\nstderr:\n" + err);
        return this;
    }

    /**
     * 断言标准输出与标准错误都不含文本。
     *
     * @param text 文本
     * @return 本结果
     */
    public CliRun notContains(String text) {
        Assertions.assertFalse(out.contains(text) || err.contains(text), () -> "输出不应含 \"" + text + "\"");
        return this;
    }

    /**
     * 解析 {@code --json} 输出中唯一服务的结果。
     *
     * @return 服务结果
     */
    public JsonNode service() {
        JsonNode services = JSON.readTree(out).path("services");
        Assertions.assertEquals(1, services.size(), out);
        return services.get(0);
    }
}
