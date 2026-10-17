package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.api.enums.RiskVariableErrorCode.VARIABLE_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.builder.decision.VariableBuilder.MemberSpec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 集合成员分页与整批替换的 HTTP 响应 Checker
 * <li>成员页逐项核对规范值（JSON 类型与集合类型一致）、显示名称与展示顺序号。</li>
 * <li>替换结果核对新根版本、本次提交的成员数与更新时间。</li>
 *
 * @author Kenny
 */
public final class VariableMembersResponseChecker extends AbstractHttpResponseChecker<VariableMembersResponseChecker> {

    public VariableMembersResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected VariableMembersResponseChecker self() {
        return this;
    }

    /**
     * 成员页
     *
     * @param members 集合全部成员，按顺序号 1..N
     * @param sortOrders 本页期望返回的成员顺序号
     */
    public VariableMembersResponseChecker assertPage(String code, long version, int page, int size, long total,
        List<MemberSpec> members, int... sortOrders) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("variableCode", "version", "items", "page", "size", "total", "valuesAsOf"),
            VariableDetailResponseChecker.names(body), "响应字段集合"),
            () -> assertEquals(code, body.path("variableCode").asString(), "variableCode"),
            () -> assertEquals(Long.toString(version), body.path("version").asString(), "version 为字符串"),
            () -> assertTrue(body.path("version").isString(), "version 类型"),
            () -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"));
        JsonNode items = body.path("items");
        assertEquals(sortOrders.length, items.size(), "成员数量，实际：" + items);
        for (int index = 0; index < sortOrders.length; index++) {
            int sortOrder = sortOrders[index];
            MemberSpec member = members.get(sortOrder - 1);
            JsonNode item = items.get(index);
            assertAll("成员 " + sortOrder, () -> assertEquals(Set.of("value", "displayName", "sortOrder"),
                VariableDetailResponseChecker.names(item)),
                () -> VariableDetailResponseChecker.assertValue(member.value(), item.path("value"), "value"),
                () -> assertEquals(member.displayName(), VariableDetailResponseChecker.text(item, "displayName")),
                () -> assertEquals(sortOrder, item.path("sortOrder").asInt(), "sortOrder"));
        }
        return this;
    }

    /** 读取时刻不早于给定时刻且不晚于当前时间。 */
    public VariableMembersResponseChecker assertValuesAsOfBetween(Instant from) {
        VariableDetailResponseChecker.assertBetween("valuesAsOf", body().path("valuesAsOf").asString(), from);
        return this;
    }

    /** 整批替换成功：新根版本、提交的成员数与库中更新时间。 */
    public VariableMembersResponseChecker assertReplaced(String code, long version, int memberCount, Instant updateTime) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("variableCode", "version", "memberCount", "updateTime"),
            VariableDetailResponseChecker.names(body), "响应字段集合"),
            () -> assertEquals(code, body.path("variableCode").asString(), "variableCode"),
            () -> assertEquals(Long.toString(version), body.path("version").asString(), "version 为字符串"),
            () -> assertEquals(memberCount, body.path("memberCount").asInt(), "memberCount"),
            () -> assertEquals(VariableDetailResponseChecker.time(updateTime), body.path("updateTime").asString(),
                "updateTime"));
        return this;
    }

    /** 集合不存在或已删除。 */
    public VariableMembersResponseChecker assertNotFound() {
        return assertProblem(404, VARIABLE_NOT_FOUND.getCode());
    }
}
