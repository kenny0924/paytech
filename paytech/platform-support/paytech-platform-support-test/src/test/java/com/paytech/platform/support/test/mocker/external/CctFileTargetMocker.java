package com.paytech.platform.support.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.contract.api.onboarding.ClientFileTargetModels.FileTargetResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * CCT 入驻文件目标授权（ClientFileTargetApi#read）的响应 DTO：按“账号 + 申请 + 主体”匹配用例登记的授权投影或 Problem。
 * <li>未登记的目标记为意外调用并返回 500，即使业务捕获该错误，全局 AfterEach 仍使测试失败。</li>
 * <li>调用记录只保存调用方与三个标识。</li>
 */
@TestComponent
public class CctFileTargetMocker implements Clear {

    /** Platform 访问 CCT 时固定的调用方。 */
    public static final String PLATFORM_CALLER = "platform-support-service";

    /** Mock 响应的 Request ID；Problem 体与 X-Request-Id 响应头必须一致。 */
    public static final String REQUEST_ID = "cct-file-target-mock";

    /** 合成 Problem 的时间戳。 */
    private static final String TIMESTAMP = "2026-10-18T00:00:00.000000Z";

    private final Map<String, Object> answers = new LinkedHashMap<>();

    private final List<Call> calls = new ArrayList<>();

    private int unexpected;

    /** 登记 CCT 已授权的 ORGANIZATION 目标：可读，是否可上传由用例指定。 */
    public synchronized void allow(long accountId, long applicationId, long subjectPartyId, long clientId,
        boolean canUpload) {
        answers.put(key(accountId, applicationId, subjectPartyId),
            new FileTargetResponseBody(Long.toString(applicationId), Long.toString(clientId),
                Long.toString(subjectPartyId), "ORGANIZATION", canUpload, true));
    }

    /** 登记 CCT 对该目标返回申请不存在（404 Problem）。 */
    public synchronized void applicationNotFound(long accountId, long applicationId, long subjectPartyId) {
        answers.put(key(accountId, applicationId, subjectPartyId),
            MockHttpFault.json(HttpStatus.NOT_FOUND.value(), problem("Not Found",
                ClientDraftErrorCode.APPLICATION_NOT_FOUND.getMessage(), ClientDraftErrorCode.APPLICATION_NOT_FOUND.getCode())));
    }

    /** 由 Mock Controller 调用：记录请求并返回登记的 DTO 或 Problem。 */
    public synchronized FileTargetResponseBody read(String caller, String applicationId, String subjectPartyId,
        String accountId) {
        calls.add(new Call(caller, accountId, applicationId, subjectPartyId));
        Object answer = answers.get(accountId + ":" + applicationId + ":" + subjectPartyId);
        if (answer instanceof MockHttpFault fault) {
            throw fault;
        }
        if (answer == null) {
            unexpected++;
            throw MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                problem("Internal Server Error", "未配置的 CCT 文件目标查询", "INTERNAL_ERROR"));
        }
        return (FileTargetResponseBody)answer;
    }

    /** 本例 CCT 查询记录。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 本例对该目标的 CCT 查询次数恰好为给定值，且每次都使用 Platform 调用方。 */
    public synchronized void assertCalls(long accountId, long applicationId, long subjectPartyId, int count) {
        var expected = new Call(PLATFORM_CALLER, Long.toString(accountId), Long.toString(applicationId),
            Long.toString(subjectPartyId));
        assertEquals(count, calls.stream().filter(expected::equals).count(), "CCT 文件目标查询次数");
        assertEquals(count, calls.size(), "不应查询其他 CCT 文件目标：" + calls);
    }

    /** 本例不应查询 CCT。 */
    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty(), "不应查询 CCT 文件目标，实际：" + calls);
    }

    /** 每例结束检查没有未登记的查询。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 CCT 文件目标查询");
    }

    /** 每例开始时没有遗留的登记与记录。 */
    public synchronized void assertEmpty() {
        assertAll("CCT 文件目标 Mock 应处于空基线", () -> assertTrue(answers.isEmpty()),
            () -> assertTrue(calls.isEmpty()), () -> assertEquals(0, unexpected));
    }

    @Override
    public synchronized void clear() {
        answers.clear();
        calls.clear();
        unexpected = 0;
    }

    /** 与 Mock Controller 收到的 Query 参数相同的匹配键。 */
    private static String key(long accountId, long applicationId, long subjectPartyId) {
        return accountId + ":" + applicationId + ":" + subjectPartyId;
    }

    /** 公共 Problem 合同的合成错误体。 */
    private static ApiProblemResponse problem(String title, String detail, String code) {
        return new ApiProblemResponse(ApiProblemProfile.TYPE, title, detail, code, REQUEST_ID, TIMESTAMP, null);
    }

    /**
     * 一次 CCT 文件目标查询。
     *
     * @param caller X-Caller-Service
     * @param accountId 原账号
     * @param applicationId 原申请
     * @param subjectPartyId 材料主体
     */
    public record Call(String caller, String accountId, String applicationId, String subjectPartyId) {
    }
}
