package com.paytech.risk.test.checker.compliance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.builder.compliance.MaterialProgramBuilder.Requirement;
import com.paytech.risk.test.mocker.compliance.MaterialProgramMocker.MockedProgram;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 通用材料目录 HTTP 响应 Checker
 * <li>协议、Request ID 与 Problem 断言复用 Foundation 基类，这里只补目录内容与禁止缓存断言。</li>
 *
 * @author Kenny
 */
public final class MaterialRequirementsResponseChecker
    extends AbstractHttpResponseChecker<MaterialRequirementsResponseChecker> {

    /** 绑定单次 HTTP 结果及其 Request ID。 */
    public MaterialRequirementsResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected MaterialRequirementsResponseChecker self() {
        return this;
    }

    /**
     * 返回该程序当前修订的完整目录
     *
     * @param program 期望命中的程序
     * @param expectedItems 期望的材料要求，按接口应输出的顺序给出
     * @return 当前 Checker
     */
    public MaterialRequirementsResponseChecker assertCatalog(MockedProgram program, List<Requirement> expectedItems) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId().assertNoStore();
        JsonNode body = body();
        JsonNode items = body.path("items");
        assertAll(() -> assertEquals(Long.toString(program.programId()), body.path("programId").asString()),
            () -> assertEquals(Long.toString(program.programVersionId()), body.path("programVersionId").asString()),
            () -> assertEquals(expectedItems.size(), items.size(), "材料要求数量不符"));
        for (int i = 0; i < expectedItems.size(); i++) {
            Requirement expected = expectedItems.get(i);
            JsonNode actual = items.get(i);
            int index = i;
            assertAll("第 " + index + " 条材料要求",
                () -> assertEquals(expected.requirementCode(), actual.path("requirementCode").asString()),
                () -> assertEquals(expected.documentType(), actual.path("documentType").asString()),
                () -> assertEquals(expected.requiredFlag() == 1, actual.path("requiredFlag").asBoolean()));
        }
        return this;
    }

    /** 目录不可用：返回 503，且不输出任何目录字段，调用方不能把它当作无需材料。 */
    public MaterialRequirementsResponseChecker assertUnavailable() {
        assertProblem(503, "SERVICE_UNAVAILABLE");
        assertFalse(body().has("items"), "不可用响应不得包含材料目录");
        return this;
    }

    /** 响应禁止缓存。 */
    public MaterialRequirementsResponseChecker assertNoStore() {
        return assertHeader("Cache-Control", "no-store");
    }
}
