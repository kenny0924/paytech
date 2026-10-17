package com.paytech.foundation.compensation.auto;

import static org.junit.jupiter.api.Assertions.assertSame;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;

/** 验证真实 MyBatis XML 解析绑定到目标枚举，不能复用最后注册的另一枚举实例。 */
class CompensationMapperEnumBindingAutoTest {
    @Test void ownerEnumRegistrationCannotReplaceCompensationStatusHandler() throws Exception {
        var configuration = new Configuration();
        var registry = configuration.getTypeHandlerRegistry();
        registry.register(CompensationEventStatus.class,CodeEnumTypeHandler.class);
        registry.register(OtherStatus.class,CodeEnumTypeHandler.class);
        String resource="META-INF/mybatis/CompensationEventMapper.xml";
        try(var input=Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        var mapping=configuration.getResultMap("com.paytech.foundation.compensation.persistence.mapper.CompensationEventMapper.CompensationEventResultMap")
            .getConstructorResultMappings().stream().filter(item -> "execute_status".equals(item.getColumn())).findFirst().orElseThrow();
        assertSame(registry.getTypeHandler(CompensationEventStatus.class),mapping.getTypeHandler());
    }
    /** 合成不同值域，确保此测试不是因为两个枚举碰巧同名而通过。 */
    enum OtherStatus implements CodeEnum {
        DIFFERENT;
        @Override public String getCode() { return "OTHER"; }
    }
}
