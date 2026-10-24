package com.paytech.platform.support.auto.ext.http.files;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.test.base.InternalFileApiTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

import tools.jackson.core.type.TypeReference;

/**
 * 调用方文件列表：来源固定为调用身份，按登记时间范围（必填，最长 31 天）与可选的原因、访问级别、上传人筛选并数据库分页；每条与按 ID
 * 查询的元数据相同（含上传人与公开地址）。
 * <li>文件 A：来源 file-test 的私有上传，原因 WORKFLOW_ATTACHMENT、上传人 42；文件 B：来源 file-test 的公开上传，原因
 * OPERATIONS_PUBLIC_ASSET、上传人 43，检查通过；文件 C：来源 platform-support 的回填式文件。</li>
 *
 * @see InternalFileApi#listFiles(Instant, Instant, String, String, String, Integer, Integer)
 */
class InternalCallerFilesApiTest extends InternalFileApiTestBase {

    private static final String SOURCE = StorageInstanceMocker.SOURCE;

    private static final String PEER = StorageInstanceMocker.PEER_SOURCE;

    private static final String PUBLIC_REASON = "OPERATIONS_PUBLIC_ASSET";

    private static final TypeReference<PagingResponseBody<StoredFileResponseBody>> PAGE = new TypeReference<>() {
    };

    private String publicBucket;

    private long fileA;

    private long fileB;

    private long fileC;

    /** 本类公共：测试策略、来源 file-test 的公开路由与三个文件。 */
    @BeforeEach
    void setUpClassMocks() {
        long policyId = fileMocker.formatPolicy().policyId();
        publicBucket = tempBuckets.publicReadable();
        storageInstances.rootInstance();
        storageInstances.publicBucket(StorageInstanceMocker.ROOT_INSTANCE, publicBucket,
            tempBuckets.publicBaseUrl(publicBucket), FileStorageBucketStatus.ACTIVE);
        storageInstances.route(SOURCE, FileAccessLevel.PUBLIC, StorageInstanceMocker.ROOT_INSTANCE, publicBucket,
            FileMocker.POLICY_CODE);
        storageInstances.refresh();
        fileA = uploaded(upload(FileAccessLevel.PRIVATE.getCode(), FileBuilder.PDF, "application/pdf"), FileBuilder.PDF,
            true);
        fileB = uploaded(new StartFileUploadRequestBody(PUBLIC_REASON, "logo.png", "image/png",
            Integer.toString(FileBuilder.PNG.length), "43", FileAccessLevel.PUBLIC.getCode()), FileBuilder.PNG, false);
        fileC = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId).version("vc").build())
            .fileId();
    }

    /** 只返回调用方来源的文件，按登记时间、文件 ID 倒序；每条字段与按 ID 查询一致，公开文件带公开地址。 */
    @Test
    void test_callerScopedList() {
        var page = list(SOURCE, "");
        List<Long> expected = List.of(Math.max(fileA, fileB), Math.min(fileA, fileB));

        assertAll(() -> assertEquals(2, page.total()), () -> assertEquals(1, page.page()), () -> assertEquals(10, page.size()),
            () -> assertEquals(expected, ids(page)));
        StoredFileResponseBody a = item(page, fileA);
        StoredFileResponseBody b = item(page, fileB);
        StoredFileResponseBody described = describe(PEER, fileB);
        assertAll(() -> assertEquals(FileAddress.of(fileA).value(), a.getFileAddress()),
            () -> assertEquals(SOURCE, a.getSourceSystem()), () -> assertEquals(REASON, a.getReasonCode()),
            () -> assertEquals("42", a.getUploaderAccountId()), () -> assertEquals("application/pdf", a.getMimeType()),
            () -> assertEquals(FileAccessLevel.PRIVATE.getCode(), a.getAccessLevel()), () -> assertNull(a.getPublicUrl()),
            () -> assertEquals(PUBLIC_REASON, b.getReasonCode()), () -> assertEquals("43", b.getUploaderAccountId()),
            () -> assertEquals("logo.png", b.getDisplayName()), () -> assertEquals(available(), b.getScanStatus()),
            () -> assertEquals(described.getPublicUrl(), b.getPublicUrl()),
            () -> assertEquals(described.getVersionedUrl(), b.getVersionedUrl()),
            () -> assertTrue(b.getPublicUrl().startsWith(tempBuckets.publicBaseUrl(publicBucket) + "/")),
            () -> assertEquals(described.getContentHash(), b.getContentHash()),
            () -> assertEquals(described.getCreateTime(), b.getCreateTime()), () -> assertEquals(1, b.getVersionNo()));

        assertAll(() -> assertEquals(List.of(fileC), ids(list(FileSourceCodes.PLATFORM_SUPPORT, ""))),
            () -> assertEquals(0, list(PEER, "").total()));
    }

    /** 原因、访问级别与上传人精确筛选，可组合；范围外与越过末页为空页并保留总数。 */
    @Test
    void test_filters() {
        assertAll(() -> assertEquals(List.of(fileB), ids(list(SOURCE, "&uploaderAccountId=43"))),
            () -> assertEquals(List.of(fileA), ids(list(SOURCE, "&uploaderAccountId=42"))),
            () -> assertEquals(0, list(SOURCE, "&uploaderAccountId=44").total()),
            () -> assertEquals(List.of(fileA), ids(list(SOURCE, "&reasonCode=" + REASON))),
            () -> assertEquals(List.of(fileB), ids(list(SOURCE, "&accessLevel=PUBLIC"))),
            () -> assertEquals(List.of(fileB), ids(list(SOURCE, "&reasonCode=" + PUBLIC_REASON
                + "&accessLevel=PUBLIC&uploaderAccountId=43"))),
            () -> assertEquals(0, list(SOURCE, "&accessLevel=PRIVATE&uploaderAccountId=43").total()));
        var later = list(SOURCE, "&page=2&size=10");
        assertAll(() -> assertTrue(later.items().isEmpty()), () -> assertEquals(2, later.total()),
            () -> assertEquals(2, later.page()));
        var outside = call(HttpMethod.GET, BASE + "/files?createdFrom=" + now().plusSeconds(1) + "&createdTo="
            + now().plusSeconds(60), SOURCE, null).assertOk().as(PAGE);
        assertEquals(0, outside.total(), "时间范围外的文件不返回");
    }

    /** 时间范围缺失、颠倒或超过 31 天，条数不在 10/20/50，条件格式不合法：400。 */
    @Test
    void test_invalidParameter() {
        Instant from = now().minus(1, ChronoUnit.HOURS);
        get(SOURCE, "?createdTo=" + now()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(SOURCE, "?createdFrom=" + from).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(SOURCE, "?createdFrom=" + now() + "&createdTo=" + now()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(SOURCE, "?createdFrom=" + now().minus(32, ChronoUnit.DAYS) + "&createdTo=" + now())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        for (String invalid : List.of("&size=100", "&size=15", "&size=200", "&page=0", "&accessLevel=public",
            "&uploaderAccountId=042", "&uploaderAccountId=abc", "&reasonCode=lower")) {
            get(SOURCE, range() + invalid).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        }
    }

    /** 经内部接口上传一个文件并等待检查通过；私有对象写入仓库测试桶时登记回收。 */
    private long uploaded(StartFileUploadRequestBody body, byte[] bytes, boolean trackObject) {
        FileUploadResponseBody upload = start(SOURCE, body).assertStatus(201).as(FileUploadResponseBody.class);
        if (trackObject) {
            storage.track(objectRef(upload.getUploadId()));
        }
        put(upload, bytes);
        long fileId = Long.parseLong(complete(SOURCE, upload.getUploadId()).assertOk()
            .as(FileUploadCompletionResponseBody.class).getFileId());
        awaitVersion(fileId, 1, FileVersionStatus.CURRENT);
        return fileId;
    }

    /** 默认覆盖本例全部文件的时间范围。 */
    private String range() {
        return "?createdFrom=" + now().minus(1, ChronoUnit.HOURS) + "&createdTo=" + now().plus(1, ChronoUnit.HOURS);
    }

    /** 以调用方身份分页查询（默认时间范围加附加条件）。 */
    private PagingResponseBody<StoredFileResponseBody> list(String caller, String conditions) {
        return get(caller, range() + conditions).assertOk().as(PAGE);
    }

    /** 以调用方身份调用列表接口。 */
    private PlatformResponseChecker get(String caller, String query) {
        return call(HttpMethod.GET, BASE + "/files" + query, caller, null);
    }

    /** 当前页文件 ID，按返回顺序。 */
    private static List<Long> ids(PagingResponseBody<StoredFileResponseBody> page) {
        return page.items().stream().map(item -> Long.parseLong(item.getFileId())).toList();
    }

    /** 当前页中的指定文件；不存在则失败。 */
    private static StoredFileResponseBody item(PagingResponseBody<StoredFileResponseBody> page, long fileId) {
        return page.items().stream().filter(item -> item.getFileId().equals(Long.toString(fileId))).findFirst()
            .orElseThrow(() -> new AssertionError("列表缺少文件: " + fileId));
    }
}
