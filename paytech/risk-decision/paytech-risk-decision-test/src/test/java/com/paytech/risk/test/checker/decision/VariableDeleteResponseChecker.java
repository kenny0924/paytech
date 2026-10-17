package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskVariableErrorCode.VARIABLE_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.VariableStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 变量逻辑删除的 HTTP 响应 Checker
 * <li>成功只返回墓碑编码、DELETED 状态及字符串形式的新根版本。</li>
 *
 * @author Kenny
 */
public final class VariableDeleteResponseChecker extends AbstractHttpResponseChecker<VariableDeleteResponseChecker> {

    public VariableDeleteResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected VariableDeleteResponseChecker self() {
        return this;
    }

    /** 删除成功（200）：墓碑编码、DELETED 与新根版本。 */
    public VariableDeleteResponseChecker assertDeleted(String code, long version) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("variableCode", "status", "version"), VariableDetailResponseChecker.names(body)),
            () -> assertEquals(code, body.path("variableCode").asString(), "variableCode"),
            () -> assertEquals(VariableStatus.DELETED.getCode(), body.path("status").asString(), "status"),
            () -> assertEquals(Long.toString(version), body.path("version").asString(), "version 为字符串"),
            () -> assertEquals(true, body.path("version").isString(), "version 类型"));
        return this;
    }

    /** 变量不存在或已删除。 */
    public VariableDeleteResponseChecker assertNotFound() {
        return assertProblem(404, VARIABLE_NOT_FOUND.getCode());
    }
}
