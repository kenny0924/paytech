package com.paytech.migration.model;

import java.nio.file.Path;
import java.util.List;

/**
 * 版本化 SQL 目录中的一个迁移脚本 {@code V<version>__<kebab-description>.sql}。
 *
 * @param version 版本号，从 1 起的正整数
 * @param description 文件名中的 kebab-case 描述
 * @param script 文件名
 * @param path 文件路径
 * @param checksum 按 LF 归一化后的 SHA-256；文件无法读取时为 null
 * @param statements 切分后的语句；文件无法读取或切分失败时为空
 * @param syntaxError 切分失败说明；切分成功或文件无法读取时为 null
 * @author Claude
 */
public record MigrationScript(int version, String description, String script, Path path, String checksum,
    List<SqlStatement> statements, String syntaxError) {
}
