package com.paytech.foundation.compensation.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.core.error.CompensationErrorCode;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.compensation.persistence.mapper.CompensationEventMapper;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;

class CompensationCodeEnumTest {

    private static final String MAPPER_RESOURCE = "META-INF/mybatis/CompensationEventMapper.xml";

    @Test
    void usesStableCodesForEveryCompensationEnum() {
        assertEquals("COMPENSATION_EVENT_CONFLICT", CompensationErrorCode.EVENT_CONFLICT.getCode());
        assertInstanceOf(CodeMessageEnum.class, CompensationErrorCode.EVENT_CONFLICT);
        assertEquals("FAILED", CompensationEventStatus.FAILED.getCode());
        assertEquals("RETRYABLE_FAILURE", CompensationHandlerResult.Outcome.RETRYABLE_FAILURE.getCode());
        assertStableCodes(CompensationErrorCode.values());
        assertStableCodes(CompensationEventStatus.values());
        assertStableCodes(CompensationHandlerResult.Outcome.values());
    }

    @Test
    void resolvesPersistenceStatusByCode() {
        assertSame(CompensationEventStatus.PROCESSING, CompensationEventStatus.fromCode("PROCESSING"));
        assertThrows(IllegalArgumentException.class, () -> CompensationEventStatus.fromCode("RENAMED_CONSTANT"));
    }

    @Test
    void mapperExplicitlyUsesStableCodeTypeHandler() throws IOException {
        Configuration configuration = new Configuration();
        // 与自动配置一致：按目标枚举注册，然后由 XML 的 javaType 查找对应实例。
        configuration.getTypeHandlerRegistry().register(CompensationEventStatus.class, CodeEnumTypeHandler.class);
        try (var inputStream = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
            XMLMapperBuilder mapperBuilder =
                new XMLMapperBuilder(inputStream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments());
            mapperBuilder.parse();
        }

        String resultMapId = CompensationEventMapper.class.getName() + ".CompensationEventResultMap";
        ResultMapping statusMapping = configuration.getResultMap(resultMapId).getResultMappings().stream()
            .filter(mapping -> "execute_status".equals(mapping.getColumn())).findFirst().orElseThrow();
        assertInstanceOf(CodeEnumTypeHandler.class, statusMapping.getTypeHandler());
    }

    private void assertStableCodes(CodeEnum[] values) {
        Set<String> codes = new HashSet<>();
        for (CodeEnum value : values) {
            assertFalse(value.getCode().isBlank());
            assertTrue(codes.add(value.getCode()), () -> "枚举编码重复: " + value.getCode());
        }
    }
}
