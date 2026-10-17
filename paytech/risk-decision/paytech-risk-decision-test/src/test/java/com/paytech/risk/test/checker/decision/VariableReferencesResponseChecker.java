package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedReference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 变量引用分段扫描的 HTTP 响应 Checker
 * <li>引用按扫描阶段（规则操作数、规则币种、动作参数、变量依赖、流程连线）及阶段内行号升序返回，逐字段核对。</li>
 * <li>没有后续时续扫游标为 null；有后续时游标为非空 Base64URL 字符串。</li>
 *
 * @author Kenny
 */
public final class VariableReferencesResponseChecker
    extends AbstractHttpResponseChecker<VariableReferencesResponseChecker> {

    /** 期望引用的扫描顺序。 */
    public static final Comparator<MockedReference> SCAN_ORDER =
        Comparator.comparingInt(MockedReference::phase).thenComparingLong(MockedReference::scanId);

    private final String variableCode;

    public VariableReferencesResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId,
        String variableCode) {
        super(result, mapper, requestId);
        this.variableCode = variableCode;
    }

    @Override
    protected VariableReferencesResponseChecker self() {
        return this;
    }

    /** 成功页：引用位置、是否还有后续、本页实际扫描行数。 */
    public VariableReferencesResponseChecker assertPage(List<MockedReference> expected, boolean hasMore, int scannedRows) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("items", "nextCursor", "hasMore", "scannedRows"),
            VariableDetailResponseChecker.names(body), "响应字段集合"),
            () -> assertEquals(hasMore, body.path("hasMore").asBoolean(), "hasMore"),
            () -> assertTrue(body.path("hasMore").isBoolean(), "hasMore 类型"),
            () -> assertEquals(scannedRows, body.path("scannedRows").asInt(), "scannedRows"),
            () -> assertTrue(hasMore ? body.path("nextCursor").asString("").matches("[A-Za-z0-9_-]+")
                : body.path("nextCursor").isNull(), "nextCursor 与 hasMore 不一致：" + body.path("nextCursor")));
        JsonNode items = body.path("items");
        assertEquals(expected.size(), items.size(), "引用数量，实际：" + items);
        for (int index = 0; index < expected.size(); index++) {
            MockedReference reference = expected.get(index);
            JsonNode item = items.get(index);
            assertAll("引用 " + index, () -> assertEquals(Set.of("consumerType", "rootId", "versionId", "versionNo", "code",
                "name", "scope", "location", "variableCode"), VariableDetailResponseChecker.names(item)),
                () -> assertEquals(reference.consumerType(), item.path("consumerType").asString(), "consumerType"),
                () -> assertEquals(Long.toString(reference.rootId()), item.path("rootId").asString(), "rootId 为字符串"),
                () -> assertEquals(reference.versionId() == null ? null : reference.versionId().toString(),
                    VariableDetailResponseChecker.text(item, "versionId"), "versionId"),
                () -> assertEquals(reference.versionNo(),
                    item.path("versionNo").isNull() ? null : item.path("versionNo").asInt(), "versionNo"),
                () -> assertEquals(reference.code(), item.path("code").asString(), "code"),
                () -> assertEquals(reference.name(), item.path("name").asString(), "name"),
                () -> assertEquals(reference.scope(), item.path("scope").asString(), "scope"),
                () -> assertEquals(reference.location(), item.path("location").asString(), "location"),
                () -> assertEquals(variableCode, item.path("variableCode").asString(), "variableCode"));
        }
        return this;
    }

    /** 本页返回的续扫游标。 */
    public String nextCursor() {
        return body().path("nextCursor").asString();
    }
}
