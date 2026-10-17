package com.paytech.compliance.test.mocker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.compliance.api.enums.MaterialQualificationType;
import com.paytech.compliance.core.configuration.MaterialRequirementsProperties;
import com.paytech.compliance.test.builder.MaterialProgramBuilder;
import com.paytech.compliance.test.builder.MaterialProgramBuilder.Requirement;
import com.paytech.compliance.test.mapper.MaterialProgramTestMapper;

import tools.jackson.databind.ObjectMapper;

/**
 * 通用材料程序的本地数据 Mocker
 * <li>按 Builder 插入程序、修订和材料要求，ID 与程序编码随机生成，避免与其他测试的数据冲突。</li>
 * <li>可替换 onboarding.materials.programs-json 映射；Clear 时删除本 Mocker 插入的程序并恢复启动时的映射。</li>
 *
 * @author Kenny
 */
@TestComponent
public class MaterialProgramMocker implements Clear {

    private final MaterialProgramTestMapper mapper;

    private final MaterialRequirementsProperties properties;

    private final ObjectMapper json;

    /** 启动时的映射配置，每例结束恢复。 */
    private final String originalProgramsJson;

    private final List<Long> programIds = new ArrayList<>();

    public MaterialProgramMocker(MaterialProgramTestMapper mapper, MaterialRequirementsProperties properties,
        ObjectMapper json) {
        this.mapper = mapper;
        this.properties = properties;
        this.json = json;
        this.originalProgramsJson = properties.getProgramsJson();
    }

    /** 已插入的程序，供用例组装映射与断言。 */
    public record MockedProgram(long programId, long programVersionId, String programCode,
        MaterialQualificationType qualificationType,        List<Requirement> requirements) {}

    /** 按 Builder 插入一个程序及其唯一修订和全部材料要求。 */
    public MockedProgram mock(MaterialProgramBuilder builder) {
        long programId = randomId();
        long versionId = randomId();
        String programCode = "MOCK_MATERIAL_" + programId;
        programIds.add(programId);
        mapper.insertProgram(programId, programCode, builder.qualificationType().getCode(), builder.programStatus());
        mapper.insertVersion(versionId, programId, builder.versionNo(), builder.versionStatus(), builder.activeSlot(),
            builder.contentHash(), builder.approvedBy(), builder.approvedTime());
        mapper.insertRequirements(versionId, builder.requirements().stream()
            .map(item -> new Object[] {randomId(), item.requirementCode(), item.documentType(), item.requiredFlag()})
            .toList());
        return new MockedProgram(programId, versionId, programCode, builder.qualificationType(),
            builder.requirements());
    }

    /** 为已有程序追加一个已批准修订，用于构造历史修订或生效修订歧义。 */
    public void mockExtraVersion(MockedProgram program, int versionNo, String status, Integer activeSlot) {
        mapper.insertVersion(randomId(), program.programId(), versionNo, status, activeSlot,
            MaterialProgramBuilder.VALID_CONTENT_HASH, 10001L, Instant.now().minusSeconds(60));
    }

    /** 映射配置只包含给定程序，每个程序映射到其自身的资格类型。 */
    public void mapPrograms(MockedProgram... programs) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (MockedProgram program : programs) {
            rows.add(row(program.qualificationType().getCode(), program.programCode()));
        }
        mapRaw(json.writeValueAsString(rows));
    }

    /** 原样设置映射配置，用于构造非法结构。 */
    public void mapRaw(String programsJson) {
        properties.setProgramsJson(programsJson);
    }

    /** 映射的一行，字段顺序固定。 */
    public static Map<String, String> row(String qualificationType, String programCode) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("qualificationType", qualificationType);
        row.put("programCode", programCode);
        return row;
    }

    /** 先恢复映射，再删除本 Mocker 插入的全部程序；删除失败时保留记录，下一次清理继续重试。 */
    @Override
    public void clear() {
        properties.setProgramsJson(originalProgramsJson);
        mapper.deletePrograms(programIds);
        programIds.clear();
    }

    /** 15 位正整数 ID，远离 Snowflake 生成的生产 ID 区间。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 1_000_000_000_000_000L);
    }
}
