package com.paytech.migration.cli;

import com.paytech.migration.model.ExecutedMigration;
import com.paytech.migration.model.ExecutionFailure;
import com.paytech.migration.model.MigrationPlan;
import com.paytech.migration.model.MigrationScript;
import com.paytech.migration.model.ServiceReport;
import com.paytech.migration.model.Violation;

import java.io.PrintStream;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 把结果写为人读文本或 JSON。只输出服务名、库名、版本、文件名与错误摘要，不输出 URL 与密码。
 *
 * @author Claude
 */
public class ReportWriter {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final PrintStream out;

    private final PrintStream err;

    /**
     * 绑定输出流。
     *
     * @param out 标准输出
     * @param err 标准错误
     */
    public ReportWriter(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * 写命令结果。
     *
     * @param command 子命令
     * @param json 是否 JSON
     * @param reports 各服务结果
     * @param exitCode 退出码
     */
    public void write(MigrationCommand command, boolean json, List<ServiceReport> reports, int exitCode) {
        if (json) {
            ObjectNode root = jsonMapper.createObjectNode();
            root.put("command", command.getCode());
            root.put("exitCode", exitCode);
            ArrayNode services = root.putArray("services");
            reports.forEach(report -> services.add(toJson(report)));
            out.println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            return;
        }
        reports.forEach(report -> writeText(command, report));
        out.println("exit " + exitCode);
    }

    /**
     * 写参数或环境错误。
     *
     * @param command 子命令；未能解析为 null
     * @param json 是否 JSON
     * @param message 错误说明
     * @param exitCode 退出码
     */
    public void writeError(MigrationCommand command, boolean json, String message, int exitCode) {
        if (!json) {
            err.println("error: " + message);
            err.println("exit " + exitCode);
            return;
        }
        ObjectNode root = jsonMapper.createObjectNode();
        root.put("command", command == null ? null : command.getCode());
        root.put("exitCode", exitCode);
        root.put("error", message);
        out.println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    /**
     * 写用法。
     */
    public void writeUsage() {
        out.println("""
            usage: paytech-migration-runner <plan|validate|migrate|seed> --manifest <instance.yaml> --sql-root <paytech dir>
                   [--service <name>] [--target <version>] [--json]
              --target    migrate only, requires --service, must not exceed versions.schema.<service>
            environment per service (<SERVICE> = upper snake case, e.g. CHANNEL_CONNECTOR):
              PAYTECH_DB_URL_<SERVICE>, PAYTECH_DB_USER_<SERVICE>, PAYTECH_DB_PASSWORD_<SERVICE>
              PAYTECH_DB_SCHEMA_<SERVICE> (optional physical database name when it differs from the manifest db)
            exit codes: 0 success, 1 validation failed, 2 execution failed, 3 argument or environment error""");
    }

    /**
     * 一个服务结果的文本。
     *
     * @param command 子命令
     * @param report 结果
     */
    void writeText(MigrationCommand command, ServiceReport report) {
        MigrationPlan plan = report.plan();
        out.println(command.getCode() + " " + plan.service() + " (database " + plan.database() + "): current V"
            + plan.currentVersion() + ", target V" + plan.targetVersion());
        String pendingLabel = command == MigrationCommand.MIGRATE ? "  planned   " : "  pending   ";
        if (command != MigrationCommand.SEED) {
            plan.pending().forEach(script -> out.println(pendingLabel + script.script()));
        }
        report.executed().forEach(executed -> out.println("  executed  " + executed.script() + " ("
            + executed.executionMs() + " ms)"));
        plan.violations().forEach(violation -> out.println("  violation " + violation));
        if (report.failure() != null) {
            out.println("  failure   " + describe(report.failure()));
        }
        if (report.seedOutcome() != null) {
            out.println("  seed      " + report.seedOutcome());
        }
    }

    /**
     * 一个服务结果的 JSON。
     *
     * @param report 结果
     * @return JSON 对象
     */
    ObjectNode toJson(ServiceReport report) {
        MigrationPlan plan = report.plan();
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("service", plan.service());
        node.put("database", plan.database());
        node.put("currentVersion", plan.currentVersion());
        node.put("targetVersion", plan.targetVersion());
        node.put("exitCode", report.exitCode());
        ArrayNode pending = node.putArray("pending");
        plan.pending().forEach(script -> pending.add(scriptJson(script)));
        ArrayNode executed = node.putArray("executed");
        report.executed().forEach(migration -> executed.add(executedJson(migration)));
        ArrayNode violations = node.putArray("violations");
        plan.violations().forEach(violation -> violations.add(violationJson(violation)));
        node.set("failure", report.failure() == null ? null : failureJson(report.failure()));
        node.put("seed", report.seedOutcome() == null ? null : report.seedOutcome().name());
        return node;
    }

    /**
     * 待执行脚本的 JSON。
     *
     * @param script 脚本
     * @return JSON 对象
     */
    ObjectNode scriptJson(MigrationScript script) {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("version", script.version());
        node.put("script", script.script());
        node.put("checksum", script.checksum());
        return node;
    }

    /**
     * 已执行版本的 JSON。
     *
     * @param migration 已执行版本
     * @return JSON 对象
     */
    ObjectNode executedJson(ExecutedMigration migration) {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("version", migration.version());
        node.put("script", migration.script());
        node.put("executionMs", migration.executionMs());
        return node;
    }

    /**
     * 校验失败的 JSON。
     *
     * @param violation 校验失败
     * @return JSON 对象
     */
    ObjectNode violationJson(Violation violation) {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("code", violation.code().name());
        node.put("version", violation.version());
        node.put("message", violation.message());
        return node;
    }

    /**
     * 执行失败的 JSON。
     *
     * @param failure 执行失败
     * @return JSON 对象
     */
    ObjectNode failureJson(ExecutionFailure failure) {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("version", failure.version());
        node.put("script", failure.script());
        node.put("statementIndex", failure.statementIndex());
        node.put("line", failure.line());
        node.put("sqlState", failure.sqlState());
        node.put("message", failure.message());
        return node;
    }

    /**
     * 执行失败的文本。
     *
     * @param failure 执行失败
     * @return 文本
     */
    static String describe(ExecutionFailure failure) {
        String location = failure.script() == null ? "" : failure.script() + " ";
        return location + failure.message();
    }
}
