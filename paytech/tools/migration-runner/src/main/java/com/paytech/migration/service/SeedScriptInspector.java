package com.paytech.migration.service;

import com.paytech.migration.model.AppliedSeed;
import com.paytech.migration.model.MigrationPlan;
import com.paytech.migration.model.MigrationTarget;
import com.paytech.migration.model.SeedOutcome;
import com.paytech.migration.model.SqlStatement;
import com.paytech.migration.model.Violation;
import com.paytech.migration.model.ViolationCode;
import com.paytech.migration.repository.SchemaMigrationRepository;
import com.paytech.migration.sql.SqlScriptSplitter;
import com.paytech.migration.sql.SqlScriptSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 初始化数据文件检查：目录内只允许 {@code <service>-initial-data.sql} 一个 SQL 文件，内容只允许 DML，已记录时只核对校验和。
 *
 * @author Claude
 */
public class SeedScriptInspector {

    private final SqlScriptSplitter sqlScriptSplitter;

    private final ChecksumCalculator checksumCalculator;

    /**
     * 构造检查器。
     *
     * @param sqlScriptSplitter 语句切分
     * @param checksumCalculator 校验和
     */
    public SeedScriptInspector(SqlScriptSplitter sqlScriptSplitter, ChecksumCalculator checksumCalculator) {
        this.sqlScriptSplitter = sqlScriptSplitter;
        this.checksumCalculator = checksumCalculator;
    }

    /**
     * seed 前的检查结果。
     *
     * @param violations 校验失败（不含计划自身的失败）
     * @param outcome 无需执行时的结果（NO_SEED_FILE、ALREADY_APPLIED）；需要执行或有失败时为 null
     * @param checksum 文件校验和；文件不存在或不可读时为 null
     * @param statements 待执行语句
     */
    public record Inspection(List<Violation> violations, SeedOutcome outcome, String checksum,
        List<SqlStatement> statements) {
    }

    /**
     * 检查是否可以执行初始化数据。
     *
     * @param target 迁移目标
     * @param plan 当前迁移计划
     * @param repository 记录表
     * @return 检查结果
     * @throws SQLException 数据库错误
     */
    public Inspection inspect(MigrationTarget target, MigrationPlan plan, SchemaMigrationRepository repository)
        throws SQLException {
        List<Violation> violations = new ArrayList<>(unexpectedFiles(target));
        if (plan.currentVersion() < plan.targetVersion()) {
            violations.add(new Violation(target.service(), ViolationCode.SEED_BEFORE_MIGRATION, null,
                "Schema 当前版本 " + plan.currentVersion() + " 低于目标版本 " + plan.targetVersion() + "，先执行 migrate"));
        }
        Path file = target.seedDirectory().resolve(target.seedFileName());
        if (!Files.isRegularFile(file)) {
            return new Inspection(violations, violations.isEmpty() ? SeedOutcome.NO_SEED_FILE : null, null, List.of());
        }
        Optional<String> content = read(file);
        if (content.isEmpty()) {
            violations.add(new Violation(target.service(), ViolationCode.SCRIPT_UNREADABLE, null,
                "无法按 UTF-8 读取 " + target.seedFileName()));
            return new Inspection(violations, null, null, List.of());
        }
        String checksum = checksumCalculator.checksum(content.get());
        Optional<AppliedSeed> recorded = repository.findSeed(target.seedFileName());
        if (recorded.isPresent()) {
            checksumViolation(target, recorded.get(), checksum).ifPresent(violations::add);
            return new Inspection(violations, violations.isEmpty() ? SeedOutcome.ALREADY_APPLIED : null, checksum,
                List.of());
        }
        List<SqlStatement> statements = statements(target, content.get(), violations);
        return new Inspection(violations, null, checksum, statements);
    }

    /**
     * validate 用：目录内多余文件，以及已记录初始化数据的文件缺失或校验和漂移。
     *
     * @param target 迁移目标
     * @param repository 记录表
     * @return 校验失败
     * @throws SQLException 数据库错误
     */
    public List<Violation> recordedSeedViolations(MigrationTarget target, SchemaMigrationRepository repository)
        throws SQLException {
        List<Violation> violations = new ArrayList<>(unexpectedFiles(target));
        Optional<AppliedSeed> recorded = repository.findSeed(target.seedFileName());
        if (recorded.isEmpty()) {
            return violations;
        }
        Optional<String> content = read(target.seedDirectory().resolve(target.seedFileName()));
        String checksum = content.map(checksumCalculator::checksum).orElse(null);
        checksumViolation(target, recorded.get(), checksum).ifPresent(violations::add);
        return violations;
    }

    /**
     * 切分并检查语句：不能为空，不得含禁止语句或 DDL。
     *
     * @param target 迁移目标
     * @param content 文件全文
     * @param violations 失败收集
     * @return 语句；有失败时内容不保证可执行
     */
    List<SqlStatement> statements(MigrationTarget target, String content, List<Violation> violations) {
        String name = target.seedFileName();
        List<SqlStatement> statements;
        try {
            statements = sqlScriptSplitter.split(content);
        } catch (SqlScriptSyntaxException exception) {
            violations.add(new Violation(target.service(), ViolationCode.SQL_SYNTAX, null,
                name + " 无法切分: " + exception.getMessage()));
            return List.of();
        }
        if (statements.isEmpty()) {
            violations.add(new Violation(target.service(), ViolationCode.EMPTY_SCRIPT, null, name + " 不含任何语句"));
        }
        ScriptStatementRules.firstForbidden(statements).ifPresent(statement -> violations.add(new Violation(
            target.service(), ViolationCode.FORBIDDEN_STATEMENT, null,
            name + " 含禁止语句（DELIMITER、USE、CALL 或存储程序）: " + ScriptStatementRules.describe(statement))));
        ScriptStatementRules.firstDdl(statements).ifPresent(statement -> violations.add(new Violation(
            target.service(), ViolationCode.SEED_DDL_FORBIDDEN, null,
            name + " 含 DDL，结构变更应放入版本化迁移: " + ScriptStatementRules.describe(statement))));
        return statements;
    }

    /**
     * 已记录校验和与当前文件的比较。
     *
     * @param target 迁移目标
     * @param recorded 记录
     * @param checksum 当前文件校验和；文件不存在为 null
     * @return 不一致时的失败
     */
    static Optional<Violation> checksumViolation(MigrationTarget target, AppliedSeed recorded, String checksum) {
        if (recorded.getChecksum().equals(checksum)) {
            return Optional.empty();
        }
        String current = checksum == null ? "文件已不存在" : "文件 " + checksum;
        return Optional.of(new Violation(target.service(), ViolationCode.SEED_CHECKSUM_MISMATCH, null,
            target.seedFileName() + " 已执行后被修改：记录 " + recorded.getChecksum() + "，" + current
                + "；初始化数据只执行一次，后续数据变更应放入版本化迁移"));
    }

    /**
     * 初始化数据目录中约定文件之外的 SQL 文件。
     *
     * @param target 迁移目标
     * @return 校验失败
     */
    static List<Violation> unexpectedFiles(MigrationTarget target) {
        return MigrationPlannerImpl.listFiles(target.seedDirectory()).stream()
            .map(file -> file.getFileName().toString())
            .filter(name -> name.endsWith(".sql") && !name.equals(target.seedFileName()))
            .map(name -> new Violation(target.service(), ViolationCode.SEED_FILE_UNEXPECTED, null,
                "初始化数据只允许一个文件 " + target.seedFileName() + "，多余文件: " + name))
            .toList();
    }

    /**
     * 按 UTF-8 读取文件。
     *
     * @param file 文件
     * @return 全文；不存在或不可读为空
     */
    static Optional<String> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
