package com.paytech.reconciliation.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.channel.connector.api.channel.response.ChannelMidResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidSummaryResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * channel-connector MID 查询的 DTO Mock：按 MID 主键配置列表摘要与详情（含能力组币种），由 MockChannelMidController 经真实 HTTP 返回
 * <li>列表只在本例调用过 {@link #mockMid} 或 {@link #mockEmptyCatalog()} 后可用，按 entityNo、status、channelId、externalMidCode 前缀过滤并分页；
 * 详情未配置即为意外调用。MID 写命令一律记为意外调用。</li>
 * <li>调用记录只保存操作与 MID 主键 / 过滤条件，不保存其他内容。</li>
 */
@TestComponent
public class ChannelConnectorMocker implements Clear {
    private final Map<String, ChannelMidSummaryResponseBody> summaries = new HashMap<>();

    private final Map<String, ChannelMidResponseBody> details = new HashMap<>();

    private final Map<String, MockHttpFault> detailFaults = new HashMap<>();

    private final Map<String, String> detailProblemRequestIds = new HashMap<>();

    private final List<String> calls = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    private boolean catalogConfigured;

    private MockHttpFault listFault;

    /** 配置一个 MID：列表摘要与详情必须描述同一 MID；配置后不再修改这两个对象。 */
    public synchronized void mockMid(ChannelMidSummaryResponseBody summary, ChannelMidResponseBody detail) {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(detail, "detail");
        assertEquals(summary.getMidId(), detail.getMidId(), "摘要与详情必须是同一 MID");
        summaries.put(summary.getMidId(), summary);
        details.put(detail.getMidId(), detail);
        catalogConfigured = true;
    }

    /** 声明本例 MID 列表为空（未归属 MID 等查询的空结果场景）。 */
    public synchronized void mockEmptyCatalog() {
        catalogConfigured = true;
    }

    /** 让某个 MID 详情返回 HTTP 故障（不存在、不可用等），用真实状态与错误体验证生产客户端的错误转换。 */
    public synchronized void failMid(String midId, MockHttpFault fault) {
        detailFaults.put(midId, fault);
    }

    /**
     * 让某个 MID 详情返回正式的第一方 Problem（如 400 MID 不存在）
     * <li>Header 与 Body 必须使用同一个 Request ID，由 Mock Controller 写出 Header，生产客户端才会按业务拒绝解码。</li>
     */
    public synchronized void rejectMid(String midId, int status, ApiProblemResponse problem) {
        detailFaults.put(midId, MockHttpFault.json(status, problem));
        detailProblemRequestIds.put(midId, problem.requestId());
    }

    /** Mock Controller 读取：某 MID 详情 Problem 的 Request ID；未配置为空。 */
    public synchronized String detailProblemRequestId(String midId) {
        return detailProblemRequestIds.get(midId);
    }

    /** 让 MID 列表返回 HTTP 故障。 */
    public synchronized void failList(MockHttpFault fault) {
        listFault = fault;
        catalogConfigured = true;
    }

    /** Mock Controller 调用：按配置过滤分页返回列表摘要。 */
    public synchronized PagingResponseBody<ChannelMidSummaryResponseBody> list(String channelId, String entityNo,
        String status, String externalMidCode, Integer page, Integer size) {
        calls.add("list:" + entityNo + ":" + status);
        if (!catalogConfigured) {
            unexpected.add("list-unconfigured");
            throw new AssertionError("channel-connector MID 列表未配置");
        }
        if (listFault != null) {
            throw listFault;
        }
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        List<ChannelMidSummaryResponseBody> matched = summaries.values().stream()
            .filter(item -> channelId == null || channelId.equals(item.getChannelId()))
            .filter(item -> entityNo == null || entityNo.equals(item.getEntityNo()))
            .filter(item -> status == null || status.equals(item.getStatus()))
            .filter(item -> externalMidCode == null || item.getExternalMidCode().startsWith(externalMidCode))
            .sorted(Comparator.comparing((ChannelMidSummaryResponseBody item) -> Long.parseLong(item.getMidId())).reversed())
            .toList();
        int from = (int)Math.min(paging.offset(), matched.size());
        int to = Math.min(from + paging.size(), matched.size());
        return new PagingResponseBody<>(matched.subList(from, to), paging.page(), paging.size(), matched.size());
    }

    /** Mock Controller 调用：返回配置的 MID 详情或故障；未配置即意外调用。 */
    public synchronized ChannelMidResponseBody detail(String midId) {
        calls.add("detail:" + midId);
        MockHttpFault fault = detailFaults.get(midId);
        if (fault != null) {
            throw fault;
        }
        ChannelMidResponseBody detail = details.get(midId);
        if (detail == null) {
            unexpected.add("detail:" + midId);
            throw new AssertionError("channel-connector MID 详情未配置: " + midId);
        }
        return detail;
    }

    /** Mock Controller 调用：reconciliation 不得调用 MID 写命令。 */
    public synchronized void unexpectedCommand(String operation) {
        unexpected.add(operation);
        throw new AssertionError("reconciliation 不得调用 channel-connector MID 写命令: " + operation);
    }

    /** 本例对 channel-connector 的调用记录（操作与 MID 主键 / 过滤条件），按调用顺序。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 零调用路径断言。 */
    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty(), "不应调用 channel-connector，实际：" + calls);
    }

    /** 每例开始时必须为空基线。 */
    public synchronized void assertEmpty() {
        assertTrue(summaries.isEmpty() && details.isEmpty() && detailFaults.isEmpty() && calls.isEmpty()
            && unexpected.isEmpty() && !catalogConfigured && listFault == null, "channel-connector Mock 应处于空基线");
    }

    /** 即使生产代码捕获了 Mock 错误，也由 AfterEach 让测试失败。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在未配置的 channel-connector 调用：" + unexpected);
    }

    /** 由 Foundation TestBase 在每例结束统一调用。 */
    @Override
    public synchronized void clear() {
        summaries.clear();
        details.clear();
        detailFaults.clear();
        detailProblemRequestIds.clear();
        calls.clear();
        unexpected.clear();
        catalogConfigured = false;
        listFault = null;
    }
}
