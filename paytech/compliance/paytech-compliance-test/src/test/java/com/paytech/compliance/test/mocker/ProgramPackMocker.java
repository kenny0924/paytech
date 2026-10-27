package com.paytech.compliance.test.mocker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.compliance.test.builder.ProgramBuilder;
import com.paytech.compliance.test.mapper.ProgramTestMapper;

/**
 * 尽调程序夹具（technique S-10）
 * <li>{@link #mockCountryPacks()} 读取仓库初始化 DML compliance-initial-data.sql，替换 @ 变量后逐条执行 INSERT，使 AE / SA / EG
 * 夹具与初始化 DML 同源。</li>
 * <li>{@link #mock(ProgramBuilder)} 按 Builder 插入单个程序（如虚构国家 ZZ、全局程序、PayFac 定制程序）。</li>
 * <li>数据由全局清理器在每例结束时删除，本 Mocker 不持有状态。</li>
 */
@TestComponent
public class ProgramPackMocker {
    /** 夹具程序版本的批准人。 */
    public static final long APPROVER = 777000000000001L;

    private final ProgramTestMapper mapper;

    /** 注入程序测试 SQL。 */
    public ProgramPackMocker(ProgramTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 已插入的程序
     *
     * @param programId 程序 ID
     * @param versionId 版本 ID
     * @param requirementIds 要求代码到模板 ID
     * @param checkIds 检查代码到模板 ID
     */
    public record MockedProgram(long programId, long versionId, Map<String, Long> requirementIds, Map<String, Long> checkIds) {
    }

    /** 执行初始化 DML，以 ACTIVE + {@link #APPROVER} 插入 AE / SA / EG 九个程序。 */
    public void mockCountryPacks() {
        for (String statement : initialDataStatements()) {
            mapper.execute(statement.replace("@program_status", "'ACTIVE'").replace("@now", "UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE")
                .replace("@approved_by", Long.toString(APPROVER)));
        }
    }

    /** 初始化 DML 中的全部 INSERT 语句（去掉注释与 SET 语句）。 */
    public static List<String> initialDataStatements() {
        Path root = Path.of(System.getProperty("compliance.reactor.root"));
        String sql;
        try {
            sql = Files.readString(root.resolve("docs/database/sql/compliance-initial-data.sql"));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        List<String> statements = new ArrayList<>();
        for (String line : sql.split("\n")) {
            if (line.startsWith("INSERT INTO")) {
                statements.add(line.substring(0, line.lastIndexOf(';')));
            }
        }
        return statements;
    }

    /** 按 Builder 插入一个程序及其 ACTIVE 版本 1。 */
    public MockedProgram mock(ProgramBuilder builder) {
        long programId = id();
        mapper.insertProgram(programId, builder.getCode(), builder.getQualificationType(), builder.getJurisdictionCode(),
            builder.getPartyType(), builder.getRoleType(), builder.getTier(), builder.getOwnerPartnerId(), builder.getStatus());
        return mockVersion(programId, 1, builder);
    }

    /** 为已有程序插入新版本（调用方先退役旧版本）。 */
    public MockedProgram mockVersion(long programId, int versionNo, ProgramBuilder builder) {
        long versionId = id();
        mapper.insertVersion(versionId, programId, versionNo, builder.getReviewIntervalMonths(), builder.isFourEyes(), "ACTIVE",
            builder.isApproved() ? APPROVER : null);
        Map<String, Long> requirements = new LinkedHashMap<>();
        for (ProgramBuilder.Material material : builder.getMaterials()) {
            long requirementId = id();
            mapper.insertRequirement(requirementId, versionId, material.code(), material.required(), material.maxAgeDays(),
                material.waivable());
            material.types().forEach(type -> mapper.insertDocType(id(), requirementId, type));
            requirements.put(material.code(), requirementId);
        }
        Map<String, Long> checks = new LinkedHashMap<>();
        for (ProgramBuilder.Check check : builder.getChecks()) {
            long checkId = id();
            mapper.insertCheck(checkId, versionId, check.code(), check.blocking(), check.validityDays());
            checks.put(check.code(), checkId);
        }
        builder.getScreenings().forEach(type -> mapper.insertScreening(id(), versionId, type));
        return new MockedProgram(programId, versionId, requirements, checks);
    }

    /** 测试 ID（与初始化 DML 的 9102… 段不冲突）。 */
    static long id() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
