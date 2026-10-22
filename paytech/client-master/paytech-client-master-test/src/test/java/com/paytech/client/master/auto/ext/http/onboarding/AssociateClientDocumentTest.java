package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;

import tools.jackson.databind.json.JsonMapper;

/**
 * C12 关联材料按文件地址核对 Platform 文件：地址格式、扫描与对象状态、摘要、上传人、Platform 文件不存在、其他拒绝和不可用。
 * <li>申请详情中的 fileAddress 由 ClientApplicationAdminTest 覆盖：两类详情共用同一响应转换；C02 详情需 Vault Reveal，本模块无 Vault 测试端点。</li>
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDocumentApi#associate
 */
class AssociateClientDocumentTest extends AbstractSubmissionApiTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    /** 本类公共配置：三类资格的通用材料要求。 */
    @BeforeEach
    void setUpAssociateMocks() {
        mockGenericRequirements();
    }

    /** 本人上传、扫描可用的文件：回执与库中保存地址和 Platform 摘要，调用带本服务身份头。 */
    @Test
    void test_normalFlow() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        String address = "/files/v1/" + fileId;
        owners.mockFile(ACCOUNT_ID, fileId, HASH, FileScanStatus.AVAILABLE.getCode());

        var result = associate(app, address);

        assertEquals(201, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals(address, body(result).path("fileAddress").asString());
        var rows = mapper.documents(app.applicationId());
        assertEquals(1, rows.size());
        assertEquals(address, rows.getFirst().get("file_address"));
        assertEquals(HASH, rows.getFirst().get("content_hash"));
        assertEquals(List.of("client-master-service"), owners.fileCallers());
    }

    /** 地址不是 /files/v1/{正整数}：422 INVALID_DRAFT，不调用 Platform，不保存。 */
    @Test
    void test_invalidAddress() {
        var app = applications.individual(ACCOUNT_ID);

        for (String address : List.of("123", "/files/v1/0", "/files/v1/012", "/files/v2/1", "https://cdn.example.test/files/v1/1")) {
            var result = associate(app, address);

            assertEquals(422, result.getStatus().value(), address);
            assertEquals(ClientDraftErrorCode.INVALID_DRAFT.getCode(), body(result).path("code").asString(), address);
        }
        assertEquals(0, owners.count("file:"));
        assertEquals(0, mapper.documentCount(app.applicationId()));
    }

    /** 扫描未完成：409 MATERIAL_FILE_UNAVAILABLE，不保存。 */
    @Test
    void test_scanNotCompleted() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, HASH, FileScanStatus.SCANNING.getCode());

        assertRejected(app, associate(app, "/files/v1/" + fileId), 409, ClientDraftErrorCode.MATERIAL_FILE_UNAVAILABLE);
    }

    /** 对象非 ACTIVE：409 MATERIAL_FILE_UNAVAILABLE，不保存。 */
    @Test
    void test_objectNotActive() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, HASH, FileScanStatus.AVAILABLE.getCode(), FileObjectStatus.UNAVAILABLE.getCode());

        assertRejected(app, associate(app, "/files/v1/" + fileId), 409, ClientDraftErrorCode.MATERIAL_FILE_UNAVAILABLE);
    }

    /** Platform 返回的摘要不是 64 位小写十六进制：按依赖不可用 503，不保存。 */
    @Test
    void test_invalidHash() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, "A".repeat(64), FileScanStatus.AVAILABLE.getCode());

        assertRejected(app, associate(app, "/files/v1/" + fileId), 503, ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE);
    }

    /** 其他账号上传的文件：404 DOCUMENT_NOT_FOUND，与不存在不可区分，不保存。 */
    @Test
    void test_uploaderNotCurrentAccount() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID + 1, fileId, HASH, FileScanStatus.AVAILABLE.getCode());

        assertRejected(app, associate(app, "/files/v1/" + fileId), 404, ClientDraftErrorCode.DOCUMENT_NOT_FOUND);
    }

    /** Platform 404 FILE_NOT_FOUND：映射为 404 DOCUMENT_NOT_FOUND，与他人文件同码，不保存。 */
    @Test
    void test_fileNotFound() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFileRejected(fileId, 404, FileErrorCode.FILE_NOT_FOUND.getCode());

        assertRejected(app, associate(app, "/files/v1/" + fileId), 404, ClientDraftErrorCode.DOCUMENT_NOT_FOUND);
    }

    /** Platform 其他明确拒绝（409 FILE_STATE_CONFLICT）：按依赖不可用 503，不保存。 */
    @Test
    void test_otherPlatformRejection() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFileRejected(fileId, 409, FileErrorCode.FILE_STATE_CONFLICT.getCode());

        assertRejected(app, associate(app, "/files/v1/" + fileId), 503, ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE);
    }

    /** Platform 503：按依赖不可用 503，不保存。 */
    @Test
    void test_platformUnavailable() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFileUnavailable(fileId);

        assertRejected(app, associate(app, "/files/v1/" + fileId), 503, ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE);
    }

    /** 拒绝响应码与错误码，Platform 恰好查询一次，申请没有材料引用。 */
    void assertRejected(ApplicationFixture app, EntityExchangeResult<byte[]> result, int status, ClientDraftErrorCode code) {
        assertEquals(status, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals(code.getCode(), body(result).path("code").asString());
        assertEquals(1, owners.count("file:"));
        assertEquals(0, mapper.documentCount(app.applicationId()));
    }

    /** 通过真实 HTTP 调用 C12，关联个人身份证明。 */
    EntityExchangeResult<byte[]> associate(ApplicationFixture app, String fileAddress) {
        var document = Map.of("subjectPartyId", Long.toString(app.partyId()), "requirementCode", "IDENTITY",
            "documentType", "IDENTITY_DOCUMENT", "fileAddress", fileAddress);
        return nonRetrying().post().uri("/client-onboarding/v1/applications/{id}/documents", app.applicationId()).headers(this::client)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(document))
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** JDK 传输的测试客户端：默认客户端遇到 503 会自动重发 POST，使 Platform 调用次数翻倍。 */
    RestTestClient nonRetrying() {
        return RestTestClient.bindToServer(new JdkClientHttpRequestFactory()).baseUrl("http://127.0.0.1:" + port).build();
    }

    /** Client BFF 转发的身份头。 */
    void client(HttpHeaders headers) {
        headers.set("X-Caller-Service", "client-console-bff");
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID));
        headers.set("X-Gateway-Session-Id", Long.toString(SESSION_ID));
        headers.set("X-Request-Id", "cct-associate-document-test");
    }
}
