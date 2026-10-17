package com.paytech.migration.service;

import com.paytech.migration.model.AppliedMigration;
import com.paytech.migration.model.MigrationPlan;
import com.paytech.migration.model.MigrationScript;
import com.paytech.migration.model.MigrationTarget;
import com.paytech.migration.model.SqlStatement;
import com.paytech.migration.model.Violation;
import com.paytech.migration.model.ViolationCode;
import com.paytech.migration.sql.SqlScriptSplitter;
import com.paytech.migration.sql.SqlScriptSyntaxException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 目录校验、脚本内容校验与记录校验依次执行，结果全部汇总后一次返回，不在首个失败处中断。
 *
 * @author Claude
 */
public class MigrationPlannerImpl implements MigrationPlanner {

    private static final Pattern SCRIPT_NAME = Pattern.compile("^V([1-9][0-9]{0,8})__([a-z0-9]+(?:-[a-z0-9]+)*)\\.sql$");

    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private final SqlScriptSplitter sqlScriptSplitter;

    private final ChecksumCalculator checksumCalculator;

    /**
     * 构造计划器。
     *
     * @param sqlScriptSplitter 语句切分
     * @param checksumCalculator 校验和
     */
    public MigrationPlannerImpl(SqlScriptSplitter sqlScriptSplitter, ChecksumCalculator checksumCalculator) {
        this.sqlScriptSplitter = sqlScriptSplitter;
        this.checksumCalculator = checksumCalculator;
    }

    @Override
    public MigrationPlan plan(MigrationTarget target, String database, List<AppliedMigration> applied) {
        List<Violation> violations = new ArrayList<>();
        NavigableMap<Integer, MigrationScript> scripts = loadScripts(target, violations);
        checkDirectory(target, scripts, violations);
        checkContents(target, scripts, violations);
        int currentVersion = checkApplied(target, scripts, applied, violations);
        List<MigrationScript> pending = scripts.values().stream()
            .filter(script -> script.version() > currentVersion && script.version() <= target.targetVersion())
            .sorted(Comparator.comparingInt(MigrationScript::version))
            .toList();
        return new MigrationPlan(target.service(), database, currentVersion, target.targetVersion(), pending,
            List.copyOf(violations));
    }

    /**
     * 列出迁移目录中的脚本；忽略以点开头的隐藏文件，文件名不合法或版本重复记为失败。
     *
     * @param target 迁移目标
     * @param violations 失败收集
     * @return 版本到脚本（重复版本只保留第一个文件）
     */
    NavigableMap<Integer, MigrationScript> loadScripts(MigrationTarget target, List<Violation> violations) {
        NavigableMap<Integer, MigrationScript> scripts = new TreeMap<>();
        for (Path file : listFiles(target.migrationsDirectory())) {
            String name = file.getFileName().toString();
            Matcher matcher = SCRIPT_NAME.matcher(name);
            if (!matcher.matches() || matcher.group(2).length() > MAX_DESCRIPTION_LENGTH) {
                violations.add(new Violation(target.service(), ViolationCode.INVALID_SCRIPT_NAME, null,
                    "文件名不符合 V<从 1 起的版本号>__<kebab-case 描述>.sql: " + name));
                continue;
            }
            int version = Integer.parseInt(matcher.group(1));
            MigrationScript previous = scripts.putIfAbsent(version, readScript(version, matcher.group(2), file));
            if (previous != null) {
                violations.add(new Violation(target.service(), ViolationCode.DUPLICATE_VERSION, version,
                    "版本号重复: " + previous.script() + " 与 " + name));
            }
        }
        return scripts;
    }

    /**
     * 读取并切分一个脚本；无法读取或切分时校验和或语句留空，由内容校验报告。
     *
     * @param version 版本号
     * @param description 描述
     * @param file 文件
     * @return 脚本
     */
    MigrationScript readScript(int version, String description, Path file) {
        String name = file.getFileName().toString();
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException exception) {
            return new MigrationScript(version, description, name, file, null, List.of(), null);
        }
        String checksum = checksumCalculator.checksum(content);
        try {
            List<SqlStatement> statements = sqlScriptSplitter.split(content);
            return new MigrationScript(version, description, name, file, checksum, statements, null);
        } catch (SqlScriptSyntaxException exception) {
            return new MigrationScript(version, description, name, file, checksum, List.of(), exception.getMessage());
        }
    }

    /**
     * 校验版本从 1 起连续、无超前文件、目标版本有文件。
     *
     * @param target 迁移目标
     * @param scripts 脚本
     * @param violations 失败收集
     */
    void checkDirectory(MigrationTarget target, NavigableMap<Integer, MigrationScript> scripts,
        List<Violation> violations) {
        int maxVersion = scripts.isEmpty() ? 0 : scripts.lastKey();
        for (int version = 1; version < maxVersion; version++) {
            if (!scripts.containsKey(version)) {
                violations.add(new Violation(target.service(), ViolationCode.VERSION_GAP, version,
                    "版本不连续：缺少 V" + version + "，目录最高版本为 V" + maxVersion));
            }
        }
        for (MigrationScript script : scripts.values()) {
            if (script.version() > target.manifestVersion()) {
                violations.add(new Violation(target.service(), ViolationCode.SCRIPT_AHEAD_OF_TARGET, script.version(),
                    "超前文件 " + script.script() + " 高于清单目标版本 " + target.manifestVersion()));
            }
        }
        if (!scripts.containsKey(target.manifestVersion())) {
            violations.add(new Violation(target.service(), ViolationCode.TARGET_SCRIPT_MISSING,
                target.manifestVersion(), "清单目标版本 V" + target.manifestVersion() + " 在 "
                    + target.migrationsDirectory() + " 中没有文件"));
        }
    }

    /**
     * 校验清单目标版本及以下脚本可读、可切分、非空且不含禁止语句。
     *
     * @param target 迁移目标
     * @param scripts 脚本
     * @param violations 失败收集
     */
    void checkContents(MigrationTarget target, Map<Integer, MigrationScript> scripts, List<Violation> violations) {
        for (MigrationScript script : scripts.values()) {
            if (script.version() > target.manifestVersion()) {
                continue;
            }
            Optional<Violation> violation = contentViolation(target.service(), script);
            violation.ifPresent(violations::add);
        }
    }

    /**
     * 一个脚本的内容失败。
     *
     * @param service 服务名
     * @param script 脚本
     * @return 第一个内容失败
     */
    Optional<Violation> contentViolation(String service, MigrationScript script) {
        if (script.checksum() == null) {
            return Optional.of(new Violation(service, ViolationCode.SCRIPT_UNREADABLE, script.version(),
                "无法按 UTF-8 读取 " + script.script()));
        }
        if (script.syntaxError() != null) {
            return Optional.of(new Violation(service, ViolationCode.SQL_SYNTAX, script.version(),
                script.script() + " 无法切分: " + script.syntaxError()));
        }
        if (script.statements().isEmpty()) {
            return Optional.of(new Violation(service, ViolationCode.EMPTY_SCRIPT, script.version(),
                script.script() + " 不含任何语句"));
        }
        return ScriptStatementRules.firstForbidden(script.statements())
            .map(statement -> new Violation(service, ViolationCode.FORBIDDEN_STATEMENT, script.version(),
                script.script() + " 含禁止语句（DELIMITER、USE、CALL 或存储程序）: "
                    + ScriptStatementRules.describe(statement)));
    }

    /**
     * 校验执行记录并计算当前版本。
     *
     * @param target 迁移目标
     * @param scripts 脚本
     * @param applied 记录
     * @param violations 失败收集
     * @return 已成功执行的最高版本；没有为 0
     */
    int checkApplied(MigrationTarget target, Map<Integer, MigrationScript> scripts, List<AppliedMigration> applied,
        List<Violation> violations) {
        int currentVersion = applied.stream().filter(AppliedMigration::isSuccess)
            .mapToInt(AppliedMigration::getVersion).max().orElse(0);
        Map<Integer, AppliedMigration> byVersion = new TreeMap<>();
        for (AppliedMigration record : applied) {
            byVersion.put(record.getVersion(), record);
            appliedViolation(target, scripts.get(record.getVersion()), record).ifPresent(violations::add);
        }
        for (int version = 1; version < Math.min(currentVersion, target.manifestVersion() + 1); version++) {
            AppliedMigration record = byVersion.get(version);
            if (record == null) {
                violations.add(new Violation(target.service(), ViolationCode.APPLIED_VERSION_GAP, version,
                    "V" + version + " 没有执行记录，但已执行到 V" + currentVersion));
            }
        }
        return currentVersion;
    }

    /**
     * 一条记录的失败：失败记录；或目标版本及以下的成功记录缺文件、文件名或校验和不一致。
     *
     * @param target 迁移目标
     * @param script 同版本脚本；没有为 null
     * @param record 记录
     * @return 失败
     */
    Optional<Violation> appliedViolation(MigrationTarget target, MigrationScript script, AppliedMigration record) {
        String service = target.service();
        int version = record.getVersion();
        if (!record.isSuccess()) {
            return Optional.of(new Violation(service, ViolationCode.FAILED_MIGRATION, version,
                record.getScript() + " 执行失败，需人工恢复后删除该失败记录: " + record.getErrorMessage()));
        }
        if (version > target.manifestVersion()) {
            return Optional.empty();
        }
        if (script == null) {
            return Optional.of(new Violation(service, ViolationCode.APPLIED_SCRIPT_MISSING, version,
                "已执行的 " + record.getScript() + " 在目录中没有文件"));
        }
        if (!script.script().equals(record.getScript())) {
            return Optional.of(new Violation(service, ViolationCode.SCRIPT_NAME_MISMATCH, version,
                "已执行文件名 " + record.getScript() + "，当前文件名 " + script.script()));
        }
        if (!record.getChecksum().equals(script.checksum())) {
            return Optional.of(new Violation(service, ViolationCode.CHECKSUM_MISMATCH, version,
                script.script() + " 已执行后被修改：记录 " + record.getChecksum() + "，文件 " + script.checksum()));
        }
        return Optional.empty();
    }

    /**
     * 列出目录中的非隐藏普通文件；目录不存在时为空。
     *
     * @param directory 目录
     * @return 按文件名排序
     */
    static List<Path> listFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                .filter(file -> !file.getFileName().toString().startsWith("."))
                .sorted()
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("无法列出目录: " + directory, exception);
        }
    }
}
