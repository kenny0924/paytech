package com.paytech.commercial.test.mocker.clientmaster;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.client.master.api.checkout.response.CheckoutCandidateVerificationResponseBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * client-master 两段式解析接口的 DTO 配置与调用记录（design/10 §2.1）。
 * <li>默认 DTO 样本 fixtures/commercial/client-master-candidates.json 由原 CCT 合成夹具的主档表转换而来：有序候选与根版本。</li>
 * <li>原 CheckoutTestMapper 对主档表的负例 UPDATE 改为在此配置 DTO 变体或 client-master 错误；未配置的调用记为意外调用。</li>
 */
@TestComponent
public class ClientMasterCandidateMocker implements Clear {
    /** 一次 HTTP 故障或断连。 */
    public record Fault(int status, ApiProblemResponse problem, boolean disconnect) {
    }

    /** Mock 响应与 Problem 的固定 Request ID。 */
    public static final String REQUEST_ID = "commercial-client-master-test";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Object candidates;
    private Object verification;
    private Object profiles;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    /** 新的默认候选响应副本；用例修改副本制造变体，不影响其他用例。 */
    public CheckoutCandidatesResponseBody sample() {
        return JSON.treeToValue(sampleTree().path("candidates"), CheckoutCandidatesResponseBody.class);
    }

    /** 默认候选与参与方资料。 */
    public synchronized void mockCandidates() {
        mockCandidates(sample());
    }

    /** 指定候选响应，并配置默认参与方资料。 */
    public synchronized void mockCandidates(CheckoutCandidatesResponseBody body) {
        candidates = body;
        profiles = JSON.treeToValue(sampleTree().path("partyProfiles"), PartyProfilesResponseBody.class);
    }

    /** 主档条件全部落选：候选为空数组，其余字段同默认样本。 */
    public synchronized void mockNoCandidates() {
        var body = sample();
        body.setCandidates(List.of());
        mockCandidates(body);
    }

    /** client-master 返回第一方 Problem。 */
    public synchronized void mockCandidatesProblem(int status, String code) {
        candidates = new Fault(status, problem(status, code), false);
    }

    /** client-master 断连。 */
    public synchronized void mockCandidatesUnavailable() {
        candidates = new Fault(0, null, true);
    }

    /** 复核结果。 */
    public synchronized void mockVerified(boolean unchanged, String... changes) {
        verification = new CheckoutCandidateVerificationResponseBody(unchanged, List.of(changes));
    }

    /** 复核时 client-master 断连。 */
    public synchronized void mockVerifyUnavailable() {
        verification = new Fault(0, null, true);
    }

    /** 候选查询；返回 DTO、Fault 或 null。 */
    public synchronized Object candidates(String clientId) {
        calls.add("candidates:" + clientId);
        return candidates;
    }

    /** 复核；返回 DTO、Fault 或 null。 */
    public synchronized Object verification(String entityNo) {
        calls.add("verify:" + entityNo);
        return verification;
    }

    /** 参与方资料；只返回请求的 ID，未配置返回 null。 */
    public synchronized PartyProfilesResponseBody profiles(List<String> ids) {
        calls.add("profiles:" + ids);
        if (!(profiles instanceof PartyProfilesResponseBody all)) {
            return null;
        }
        List<PartyProfilesResponseBody.PartyProfile> items = new ArrayList<>();
        for (var item : all.getItems()) {
            if (ids.contains(item.getProfileId())) {
                items.add(item);
            }
        }
        return new PartyProfilesResponseBody(items);
    }

    /** 某类调用次数。 */
    public long count(String prefix) {
        return calls.stream().filter(call -> call.startsWith(prefix)).count();
    }

    /** 构造项目 Problem。 */
    static ApiProblemResponse problem(int status, String code) {
        return new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.valueOf(status).getReasonPhrase(), "测试依赖拒绝", code,
            REQUEST_ID, "2026-10-17T00:00:00.000000Z", null);
    }

    /** 读取默认样本。 */
    private static JsonNode sampleTree() {
        try (InputStream input = ClientMasterCandidateMocker.class
            .getResourceAsStream("/fixtures/commercial/client-master-candidates.json")) {
            return JSON.readTree(input);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    /** 每例清空配置与记录。 */
    @Override
    public synchronized void clear() {
        candidates = null;
        verification = null;
        profiles = null;
        calls.clear();
    }
}
