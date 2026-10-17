package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 变量目录的 HTTP 响应 Checker
 * <li>普通分页返回 page/size/total 且不返回 missingCodes；编码批取只返回 items、missingCodes 与读取时刻。</li>
 * <li>目录项逐字段核对，不含详情字段；集合预览为前三个成员。</li>
 *
 * @author Kenny
 */
public final class VariableListResponseChecker extends AbstractHttpResponseChecker<VariableListResponseChecker> {

    public VariableListResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected VariableListResponseChecker self() {
        return this;
    }

    /** 普通分页结果：分页元数据与按编码升序的目录项。 */
    public VariableListResponseChecker assertPage(int page, int size, long total, MockedVariable... items) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("items", "page", "size", "total", "valuesAsOf"),
            VariableDetailResponseChecker.names(body), "分页响应字段集合"),
            () -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"));
        return assertItems(items);
    }

    /** 编码批取结果：缺失编码按请求顺序，找到的定义按编码升序，不返回分页字段。 */
    public VariableListResponseChecker assertBatch(List<String> missingCodes, MockedVariable... items) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("items", "missingCodes", "valuesAsOf"), VariableDetailResponseChecker.names(body),
            "批取响应字段集合"),
            () -> assertEquals(missingCodes, body.path("missingCodes").valueStream().map(JsonNode::asString).toList(),
                "missingCodes"));
        return assertItems(items);
    }

    /** 读取时刻不早于给定时刻且不晚于当前时间。 */
    public VariableListResponseChecker assertValuesAsOfBetween(Instant from) {
        VariableDetailResponseChecker.assertBetween("valuesAsOf", body().path("valuesAsOf").asString(), from);
        return this;
    }

    private VariableListResponseChecker assertItems(MockedVariable... expected) {
        JsonNode items = body().path("items");
        assertEquals(expected.length, items.size(), "目录项数量，实际编码：" + items.valueStream()
            .map(item -> item.path("variableCode").asString()).toList());
        for (int index = 0; index < expected.length; index++) {
            MockedVariable variable = expected[index];
            assertAll("目录项 " + index, VariableDetailResponseChecker.summaryChecks(items.get(index), variable.code(),
                variable.spec(), VariableDetailResponseChecker.SUMMARY_FIELDS));
        }
        return this;
    }
}
