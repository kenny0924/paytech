package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.candidate.response.MidCandidateBody;
import com.paytech.channel.connector.api.candidate.response.QueryMidCandidatesResponseBody;
import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ChannelOperationOutcome;
import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;
import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * channel-connector 的响应 DTO（channel-connector-api）：按用例配置 OE 下的 MID 候选与渠道操作的已映射结论，并记录每次请求。
 *
 * <p>
 * 渠道操作请求含 card.token 时，先像真实 Platform 一样经 {@link PlatformMocker} 回调 Payment 读卡接口；读卡被拒时返回 Platform
 * 初始化失败对应的 NOT_SENT（channel-connector 的组合行为）。未配置结论的执行记为意外调用。
 */
@TestComponent
@RequiredArgsConstructor
public class ChannelConnectorMocker implements Clear {
    /** 读卡被拒时真实 Platform 返回的 Problem code，channel-connector 映射为 NOT_SENT。 */
    public static final String READ_CARD_REJECTED = "INTEGRATION_INITIALIZATION_FAILED";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PlatformMocker platform;
    private final IdGenerator ids;
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<QueryMidCandidatesRequestBody> queries = new ArrayList<>();
    private final List<Call> calls = new ArrayList<>();
    private ChannelOperationResponseBody reply;
    private MockHttpFault fault;
    private int unexpected;

    /** 配置指定 OE 与币种下一个启用的 MID 候选，明确支持 PAY；三元组 ID 用正式 Snowflake 生成。 */
    public synchronized Candidate candidate(String entityNo, String currency) {
        var candidate = new Candidate(entityNo, currency, ids.nextId(), ids.nextId(), ids.nextId(),
            new LinkedHashSet<>(List.of(ChannelOperation.PAY.getCode())));
        candidates.add(candidate);
        return candidate;
    }

    /** 为已配置候选增加一个明确支持的渠道操作。 */
    public synchronized void supports(Candidate candidate, ChannelOperation operation) {
        candidate.supportedOperations().add(operation.getCode());
    }

    /** 收款成功。 */
    public void succeeded(String channelTransactionRef) {
        respond(body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.SUCCEEDED, channelTransactionRef, "SUCCESS"));
    }

    /** 待认证：带跳转地址。 */
    public void pending(String channelTransactionRef, String redirectUrl) {
        var body = body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.PENDING, channelTransactionRef, "PENDING");
        body.setRedirectUrl(redirectUrl);
        respond(body);
    }

    /** 渠道受理后拒绝：带失败代码与说明。 */
    public void declined(String channelTransactionRef, String failureCode, String failureMessage) {
        var body = body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.FAILED, channelTransactionRef, "FAILED");
        body.setFailureCode(failureCode);
        body.setFailureMessage(failureMessage);
        respond(body);
    }

    /** 结果未知（channel-connector 把 Platform 未知错误映射为 ERROR）。 */
    public void unknown() {
        respond(body(ChannelOperationOutcome.ERROR, null, null, null));
    }

    /** 保证未发出渠道请求。 */
    public void notSent(String failureCode) {
        var body = body(ChannelOperationOutcome.NOT_SENT, null, null, null);
        body.setFailureCode(failureCode);
        respond(body);
    }

    /** 配置原样返回的结论 DTO。 */
    public synchronized void respond(ChannelOperationResponseBody body) {
        reply = body;
        fault = null;
    }

    /** 配置 HTTP 故障（channel-connector 不可用或返回 Problem）。 */
    public synchronized void fail(MockHttpFault failure) {
        fault = failure;
        reply = null;
    }

    /** HTTP Mock 实际调用此处：记录请求并返回本 OE 与币种的候选。 */
    public synchronized QueryMidCandidatesResponseBody queryMidCandidates(QueryMidCandidatesRequestBody request) {
        queries.add(request);
        var response = new QueryMidCandidatesResponseBody();
        response.setCandidates(candidates.stream()
            .filter(candidate -> candidate.entityNo().equals(request.getEntityNo())
                && candidate.currency().equals(request.getCriteria().getCurrency()))
            .map(Candidate::body).toList());
        return response;
    }

    /** HTTP Mock 实际调用此处：按需先回调读卡，再记录请求并返回配置的结论。 */
    public ChannelOperationResponseBody execute(ExecuteChannelOperationRequestBody request) {
        JsonNode input = JSON.valueToTree(request.getInput());
        String[] revealed = platform.revealCard(input);
        synchronized (this) {
            calls.add(new Call(request, input, revealed[0], revealed[1]));
            if (revealed[0] != null && revealed[0].startsWith("REJECTED")) {
                var rejected = body(ChannelOperationOutcome.NOT_SENT, null, null, null);
                rejected.setFailureCode(READ_CARD_REJECTED);
                return rejected;
            }
            if (fault != null) {
                throw fault;
            }
            if (reply == null) {
                unexpected++;
                throw new AssertionError("未配置的渠道操作");
            }
            return reply;
        }
    }

    /** 本例渠道操作请求。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 唯一的一次渠道操作请求。 */
    public synchronized Call only() {
        assertEquals(1, calls.size(), "渠道操作次数");
        return calls.getFirst();
    }

    /** 本例候选查询请求。 */
    public synchronized List<QueryMidCandidatesRequestBody> queries() {
        return List.copyOf(queries);
    }

    /** 只清空渠道操作的结论与记录，保留候选配置（同一用例内换一种渠道结论）。 */
    public synchronized void clearOperations() {
        calls.clear();
        reply = null;
        fault = null;
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(candidates.isEmpty() && queries.isEmpty() && calls.isEmpty() && reply == null && fault == null
            && unexpected == 0);
    }

    /** 每例结束检查没有未配置的渠道操作。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的渠道操作");
    }

    @Override
    public synchronized void clear() {
        candidates.clear();
        queries.clear();
        clearOperations();
        unexpected = 0;
    }

    /** 公共结论字段。 */
    static ChannelOperationResponseBody body(ChannelOperationOutcome outcome, ChannelPaymentStatus status,
        String channelTransactionRef, String channelStatus) {
        var body = new ChannelOperationResponseBody();
        body.setOutcome(outcome.getCode());
        body.setPaymentStatus(status == null ? null : status.getCode());
        body.setChannelTransactionRef(channelTransactionRef);
        body.setChannelStatus(channelStatus);
        body.setRawOutputKeys(List.of());
        return body;
    }

    /**
     * 一个 MID 候选
     *
     * @param entityNo 所属 OE
     * @param currency 能力匹配的币种
     * @param channelId 渠道 ID
     * @param channelAccountId 渠道账号 ID
     * @param midId MID ID
     * @param supportedOperations 明确支持的渠道操作代码
     */
    public record Candidate(String entityNo, String currency, long channelId, long channelAccountId, long midId,
        Set<String> supportedOperations) {
        /** 合同 DTO。 */
        MidCandidateBody body() {
            var body = new MidCandidateBody();
            body.setChannelId(Long.toString(channelId));
            body.setChannelAccountId(Long.toString(channelAccountId));
            body.setMidId(Long.toString(midId));
            body.setSupportedOperations(List.copyOf(supportedOperations));
            return body;
        }
    }

    /**
     * 一次渠道操作请求：合同 DTO、标准 API 输入（JSON）与读卡结果摘要。
     *
     * @param request 请求 DTO
     * @param input 标准 API 输入
     * @param revealed “末四位/CVV 是否存在”或 “REJECTED 状态码”；输入不含 card.token 时为 null
     * @param materialDigest 读到的卡值摘要
     */
    public record Call(ExecuteChannelOperationRequestBody request, JsonNode input, String revealed, String materialDigest) {
    }
}
