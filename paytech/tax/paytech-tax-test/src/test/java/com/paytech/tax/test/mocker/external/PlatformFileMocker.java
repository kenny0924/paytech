package com.paytech.tax.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;

/**
 * Platform 内部文件接口 Mock：按用例配置是否接受上传与前几次开始上传的 503 故障，记录开始上传的请求快照、直传字节与完成调用
 * <li>未调用 {@link #acceptUploads(int)} 时任何上传都记为意外调用；故障次数耗尽后接受上传。</li>
 * <li>只保存测试合成的税票字节与文件名，不含真实数据。</li>
 */
@TestComponent
public class PlatformFileMocker implements Clear {
    /** Mock 响应的 Request ID，响应头与 Problem 体一致。 */
    public static final String REQUEST_ID = "platform-test";

    private boolean accepting;

    private int failuresLeft;

    private long nextId = 7001;

    private final List<StartFileUploadRequestBody> starts = new ArrayList<>();

    private final List<String> callers = new ArrayList<>();

    private final Map<String, byte[]> contents = new HashMap<>();

    private final List<String> completions = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    /**
     * 接受上传；前 failures 次开始上传返回 503
     *
     * @param failures 前几次开始上传失败
     */
    public synchronized void acceptUploads(int failures) {
        accepting = true;
        failuresLeft = failures;
    }

    /**
     * 由 Mock Controller 调用：开始上传
     *
     * @param body 请求
     * @param caller X-Caller-Service
     * @return 上传 ID；故障或未配置时抛出
     */
    public synchronized String startUpload(StartFileUploadRequestBody body, String caller) {
        if (!accepting) {
            unexpected.add("startUpload " + body.getFilename());
            throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        }
        starts.add(body);
        callers.add(caller);
        if (failuresLeft > 0) {
            failuresLeft--;
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        }
        return Long.toString(nextId++);
    }

    /** 由直传 Controller 调用：保存字节。 */
    public synchronized void put(String uploadId, byte[] content) {
        contents.put(uploadId, content);
    }

    /**
     * 由 Mock Controller 调用：完成上传
     *
     * @param uploadId 上传 ID
     * @return 文件 ID（与上传 ID 相同）；没有直传内容时记为意外调用
     */
    public synchronized String complete(String uploadId) {
        if (!contents.containsKey(uploadId)) {
            unexpected.add("completeUpload " + uploadId);
            throw problem(HttpStatus.CONFLICT, "FILE_STATE_CONFLICT");
        }
        completions.add(uploadId);
        return uploadId;
    }

    /** 开始上传的请求快照。 */
    public synchronized List<StartFileUploadRequestBody> starts() {
        return List.copyOf(starts);
    }

    /** 开始上传请求携带的调用方。 */
    public synchronized List<String> callers() {
        return List.copyOf(callers);
    }

    /** 已完成上传的文件 ID。 */
    public synchronized List<String> completions() {
        return List.copyOf(completions);
    }

    /** 直传的字节。 */
    public synchronized byte[] content(String fileId) {
        return contents.get(fileId);
    }

    /** 记录 tax 不应发起的操作，返回 500 Problem 故障。 */
    public synchronized MockHttpFault unexpected(String operation) {
        unexpected.add(operation);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    /** 本例没有任何上传调用。 */
    public synchronized void assertNoCalls() {
        assertEquals(List.of(), starts, "不应调用 Platform 文件接口");
    }

    /** 全局 AfterEach：不允许出现未配置的调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(List.of(), unexpected, "存在未配置的 Platform 文件调用");
    }

    /** 全局 BeforeEach：上一例的配置与记录已清除。 */
    public synchronized void assertEmpty() {
        assertAll("Platform 文件 Mock 应处于空基线", () -> assertTrue(!accepting), () -> assertTrue(starts.isEmpty()),
            () -> assertTrue(contents.isEmpty()), () -> assertTrue(unexpected.isEmpty()));
    }

    /** 每例清除配置与记录。 */
    @Override
    public synchronized void clear() {
        accepting = false;
        failuresLeft = 0;
        starts.clear();
        callers.clear();
        contents.clear();
        completions.clear();
        unexpected.clear();
    }

    /** 第一方 Problem 故障。 */
    static MockHttpFault problem(HttpStatus status, String code) {
        return MockHttpFault.json(status.value(), new ApiProblemResponse(ApiProblemProfile.TYPE, status.getReasonPhrase(),
            "合成故障", code, REQUEST_ID, "2026-10-29T00:00:00.000000Z", null));
    }
}
