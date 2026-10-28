package com.paytech.card.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.domain.binlibrary.BinLibrarySourceFile;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.api.files.FileAddress;

/**
 * Platform 内部文件接口与对象存储短时下载的合成 DTO（card 只读文件）；国家参考数据由参考数据测试支持模块的 Mocker 提供
 * <li>文件元数据与字节按文件 ID 配置；未配置的文件按 Platform 合同返回 404 FILE_NOT_FOUND。</li>
 * <li>记录每次元数据查询、下载授权与下载，以及调用方服务名，不记录文件内容。</li>
 */
@TestComponent
public class PlatformMocker implements Clear {
    /** 错误响应的 Request ID（Header 与 Problem 体一致）。 */
    public static final String REQUEST_ID = "platform-test";

    private final Map<String, StoredFileResponseBody> files = new HashMap<>();
    private final Map<String, byte[]> contents = new HashMap<>();
    private final Map<String, Integer> downloadCounts = new HashMap<>();
    private final Map<String, int[]> interruptions = new HashMap<>();
    private final List<String> fileQueries = new ArrayList<>();
    private final Set<String> callers = new HashSet<>();
    private final List<String> unexpected = new ArrayList<>();

    /**
     * 配置一个由 operations-console-bff 以 BIN 库导入原因上传、扫描通过的 CSV
     *
     * @param fileId 文件 ID
     * @param content 文件字节
     * @return 元数据（可在配置后改写扫描状态等字段）
     */
    public synchronized StoredFileResponseBody mockCsvFile(String fileId, byte[] content) {
        StoredFileResponseBody file = new StoredFileResponseBody(fileId, FileAddress.of(Long.parseLong(fileId)).value(), BinLibrarySourceFile.SOURCE_SYSTEM,
            BinLibrarySourceFile.REASON_CODE, "bin-" + fileId + ".csv", BinLibrarySourceFile.CSV_MIME_TYPE,
            Integer.toString(content.length), sha256(content), "AVAILABLE", "ACTIVE", "900000000000000123", null, "PRIVATE", 1, null, null, null);
        files.put(fileId, file);
        contents.put(fileId, content.clone());
        return file;
    }

    /** 改写已配置文件的扫描状态（PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED）。 */
    public synchronized void scanStatus(String fileId, String scanStatus) {
        files.get(fileId).setScanStatus(scanStatus);
    }

    /** 第 downloadNumber 次下载（从 1 起）只返回前 byteLimit 个字节，模拟读取中断。 */
    public synchronized void interruptDownload(String fileId, int downloadNumber, int byteLimit) {
        interruptions.put(fileId, new int[] {downloadNumber, byteLimit});
    }

    /** 由 Mock HTTP 调用：文件元数据；未配置按合同 404。 */
    public synchronized StoredFileResponseBody getFile(String fileId, String caller) {
        callers.add(String.valueOf(caller));
        fileQueries.add(fileId);
        StoredFileResponseBody file = files.get(fileId);
        if (file == null) {
            throw MockHttpFault.json(HttpStatus.NOT_FOUND.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
                "Not Found", FileErrorCode.FILE_NOT_FOUND.getMessage(),
                FileErrorCode.FILE_NOT_FOUND.getCode(), REQUEST_ID, "2026-10-22T00:00:00.000000Z", null));
        }
        return file;
    }

    /** 由 Mock HTTP 调用：下载授权只签发给已配置且扫描通过的文件；系统读取不带操作人与下载方式。 */
    public synchronized void authorizeDownload(String fileId, String caller, String operatorAccountId, String disposition) {
        callers.add(String.valueOf(caller));
        StoredFileResponseBody file = files.get(fileId);
        if (operatorAccountId != null || disposition != null) {
            unexpected.add("authorize-download-options:" + fileId);
            throw new AssertionError("Card 系统读取不应带操作人或下载方式: " + fileId);
        }
        if (file == null || !"AVAILABLE".equals(file.getScanStatus())) {
            unexpected.add("authorize-download:" + fileId);
            throw new AssertionError("未配置或未扫描通过的文件不应申请下载: " + fileId);
        }
    }

    /** 由 Mock HTTP 调用：返回文件字节，按配置截断某一次下载。 */
    public synchronized byte[] download(String fileId) {
        byte[] content = contents.get(fileId);
        if (content == null) {
            unexpected.add("download:" + fileId);
            throw new AssertionError("未配置的文件下载: " + fileId);
        }
        int count = downloadCounts.merge(fileId, 1, Integer::sum);
        int[] interruption = interruptions.get(fileId);
        return interruption != null && interruption[0] == count ? Arrays.copyOf(content, interruption[1]) : content;
    }

    /** 由 Mock HTTP 调用：card 不应开始或完成上传。 */
    public synchronized AssertionError unexpectedUpload() {
        unexpected.add("upload");
        return new AssertionError("card 不应调用 Platform 上传接口");
    }

    /** 某文件的元数据查询次数。 */
    public synchronized long fileQueryCount(String fileId) {
        return fileQueries.stream().filter(fileId::equals).count();
    }

    /** 全部文件的元数据查询次数。 */
    public synchronized int fileQueryTotal() {
        return fileQueries.size();
    }

    /** 某文件的下载次数。 */
    public synchronized int downloadCount(String fileId) {
        return downloadCounts.getOrDefault(fileId, 0);
    }

    /** 所有 Platform 请求都以 card-service 身份发出。 */
    public synchronized void assertCallerIsCard() {
        assertTrue(callers.isEmpty() || callers.equals(Set.of(CardApiConstants.SERVICE_ID)), "Platform 调用方不是 card-service: " + callers);
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(files.isEmpty() && contents.isEmpty() && downloadCounts.isEmpty() && interruptions.isEmpty()
            && fileQueries.isEmpty() && callers.isEmpty() && unexpected.isEmpty(), "Platform Mock 应处于空基线");
    }

    /** 即使业务捕获 HTTP 故障，也不能掩盖意外调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在意外 Platform 调用: " + unexpected);
    }

    /** 由 Foundation 的 AfterEach 统一回收。 */
    @Override
    public synchronized void clear() {
        files.clear();
        contents.clear();
        downloadCounts.clear();
        interruptions.clear();
        fileQueries.clear();
        callers.clear();
        unexpected.clear();
    }

    /** 文件字节的 SHA-256 小写十六进制。 */
    public static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
