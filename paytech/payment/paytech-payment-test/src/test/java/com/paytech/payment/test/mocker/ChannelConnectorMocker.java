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
 *
 * <p>
 * 3DS 全链路：原交易查单（operation=QUERY）与 PAY 分开配置与记录；查单结论是常驻配置，查单恢复任务的每次执行都返回同一结论，
 * 未配置时记为意外调用。PAY 配置为待认证或结果未知时同时配置查单“仍处理中 / 结果未知”，使 PAY 结果事务登记的查单恢复任务首轮执行
 * 不推进动作；需要推进的用例再显式改配查单结论。
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
    private final List<ExecuteChannelOperationRequestBody> queryCalls = new ArrayList<>();
    private ChannelOperationResponseBody reply;
    private ChannelOperationResponseBody queryReply;
    private MockHttpFault fault;
    private int unexpected;

    /** 渠道编码，用于拼渠道通知地址。 */
    public static final String CHANNEL_CODE = "PAYERMAX_MOCK";

    /** 配置指定 OE 与币种下一个启用的 MID 候选，明确支持 PAY、未声明 3DS 模式；三元组 ID 用正式 Snowflake 生成。 */
    public Candidate candidate(String entityNo, String currency) {
        return candidate(entityNo, currency, null);
    }

    /** 配置指定 OE 与币种下一个启用的 MID 候选，明确支持 PAY，并声明 3DS 模式（可空）。 */
    public synchronized Candidate candidate(String entityNo, String currency, String threeDsMode) {
        var candidate = new Candidate(entityNo, currency, ids.nextId(), ids.nextId(), ids.nextId(),
            new LinkedHashSet<>(List.of(ChannelOperation.PAY.getCode())), threeDsMode);
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

    /** 待认证：带 3DS 挑战跳转地址（交互类型 THREE_DS_CHALLENGE）；查单仍处理中。 */
    public void pending(String channelTransactionRef, String redirectUrl) {
        pending(channelTransactionRef, redirectUrl, "THREE_DS_CHALLENGE");
    }

    /** 待交互：带跳转地址与渠道给出的交互类型；查单仍处理中。 */
    public synchronized void pending(String channelTransactionRef, String redirectUrl, String nextActionType) {
        var body = body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.PENDING, channelTransactionRef, "PENDING");
        body.setRedirectUrl(redirectUrl);
        body.setNextActionType(nextActionType);
        respond(body);
        queryReply = body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.PENDING, channelTransactionRef, "PENDING");
    }

    /** 渠道受理后拒绝：带失败代码与说明。 */
    public void declined(String channelTransactionRef, String failureCode, String failureMessage) {
        var body = body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.FAILED, channelTransactionRef, "FAILED");
        body.setFailureCode(failureCode);
        body.setFailureMessage(failureMessage);
        respond(body);
    }

    /** 结果未知（channel-connector 把 Platform 未知错误映射为 ERROR）；查单同样没有结论。 */
    public synchronized void unknown() {
        respond(body(ChannelOperationOutcome.ERROR, null, null, null));
        queryReply = body(ChannelOperationOutcome.ERROR, null, null, null);
    }

    /** 查单：渠道确认成功，带渠道记录的金额与币种。 */
    public void querySucceeded(String channelTransactionRef, String amount, String currency) {
        var body = body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.SUCCEEDED, channelTransactionRef, "SUCCESS");
        body.setAmount(amount);
        body.setCurrency(currency);
        queried(body);
    }

    /** 查单：渠道最终拒绝，带失败代码。 */
    public void queryFailed(String channelTransactionRef, String failureCode) {
        var body = body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.FAILED, channelTransactionRef, "FAILED");
        body.setFailureCode(failureCode);
        queried(body);
    }

    /** 查单：渠道订单已关闭未支付。 */
    public void queryClosed(String channelTransactionRef) {
        queried(body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.CLOSED, channelTransactionRef, "CLOSED"));
    }

    /** 查单：渠道无此交易（FAIL + NOT_FOUND）。 */
    public void queryNotFound() {
        var body = body(ChannelOperationOutcome.FAIL, ChannelPaymentStatus.NOT_FOUND, null, null);
        body.setFailureCode("ORDER_NOT_EXIST");
        queried(body);
    }

    /** 查单：渠道仍在处理。 */
    public void queryPending(String channelTransactionRef) {
        queried(body(ChannelOperationOutcome.SUCCESS, ChannelPaymentStatus.PENDING, channelTransactionRef, "PENDING"));
    }

    /** 配置常驻的查单结论 DTO；查单恢复任务每次执行都返回它。 */
    public synchronized void queried(ChannelOperationResponseBody body) {
        queryReply = body;
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

    /** HTTP Mock 实际调用此处：查单按查单配置返回；其他操作按需先回调读卡，再记录请求并返回配置的结论。 */
    public ChannelOperationResponseBody execute(ExecuteChannelOperationRequestBody request) {
        if (ChannelOperation.QUERY.getCode().equals(request.getOperation())) {
            return query(request);
        }
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

    /** 查单：记录请求后返回常驻结论；HTTP 故障同样作用于查单；未配置为意外调用。 */
    synchronized ChannelOperationResponseBody query(ExecuteChannelOperationRequestBody request) {
        queryCalls.add(request);
        if (fault != null) {
            throw fault;
        }
        if (queryReply == null) {
            unexpected++;
            throw new AssertionError("未配置的查单");
        }
        return queryReply;
    }

    /** 本例查单请求，按到达顺序。 */
    public synchronized List<ExecuteChannelOperationRequestBody> queryCalls() {
        return List.copyOf(queryCalls);
    }

    /** 本例渠道操作请求（不含查单）。 */
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

    /** 只清空渠道操作（含查单）的结论与记录，保留候选配置（同一用例内换一种渠道结论）。 */
    public synchronized void clearOperations() {
        calls.clear();
        queryCalls.clear();
        reply = null;
        queryReply = null;
        fault = null;
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(candidates.isEmpty() && queries.isEmpty() && calls.isEmpty() && queryCalls.isEmpty() && reply == null
            && queryReply == null && fault == null && unexpected == 0);
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
     * @param threeDsMode SECURITY.3DS_MODE；未声明为 null
     */
    public record Candidate(String entityNo, String currency, long channelId, long channelAccountId, long midId,
        Set<String> supportedOperations, String threeDsMode) {
        /** 合同 DTO。 */
        MidCandidateBody body() {
            var body = new MidCandidateBody();
            body.setChannelId(Long.toString(channelId));
            body.setChannelCode(CHANNEL_CODE);
            body.setThreeDsMode(threeDsMode);
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
