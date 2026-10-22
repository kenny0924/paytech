package com.paytech.platform.support.test.base;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.AuditTestMapper;
import com.paytech.platform.support.test.dal.FileStorageTestMapper;
import com.paytech.platform.support.test.mocker.external.StorageVaultMocker;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.mocker.local.StorageObjectMocker;
import com.paytech.platform.support.test.mocker.local.TempBucketMocker;

/**
 * 存储管理接口测试的公共调用：共享 Mock 上下文（files.enabled 关闭也可维护存储），真实 Vault 测试接口与本机 MinIO
 * <li>不含 {@code @Test}；本类 BeforeEach 检查存储相关 Mock 空基线，AfterEach 检查未配置的 Vault 调用。</li>
 */
public abstract class FileStorageManagementTestBase extends OperationsApiTestBase {

    /** 存储管理接口前缀。 */
    protected static final String BASE = "/storage-admin/v1";

    /** 默认操作原因。 */
    protected static final String REASON = "存储管理测试";

    @Autowired
    protected StorageVaultMocker storageVault;

    @Autowired
    protected StorageObjectMocker storage;

    @Autowired
    protected StorageInstanceMocker storageInstances;

    @Autowired
    protected TempBucketMocker tempBuckets;

    @Autowired
    protected FileStorageTestMapper storageData;

    @Autowired
    protected FileMocker fileMocker;

    @Autowired
    protected AuditTestMapper auditData;

    /** 存储相关 Mock 的空基线。 */
    @BeforeEach
    void setUpStorageManagementBase() {
        storageVault.assertEmpty();
        tempBuckets.assertEmpty();
        fileMocker.assertEmpty();
    }

    /** 清理前检查未配置的 Vault 调用。 */
    @AfterEach
    void tearDownStorageManagementBase() {
        storageVault.assertNoUnexpectedCalls();
    }

    /** 以人员身份 PATCH。 */
    protected PlatformResponseChecker patch(String path, Operator operator, Object body) {
        return call(HttpMethod.PATCH, path, headers(operator), body);
    }

    /** 以人员身份带 Body 的 DELETE。 */
    protected PlatformResponseChecker delete(String path, Operator operator, Object body) {
        return call(HttpMethod.DELETE, path, headers(operator), body);
    }

    /** 状态切换请求。 */
    protected static ChangeStorageStatusRequestBody status(String status, int expectedVersion) {
        return new ChangeStorageStatusRequestBody(status, expectedVersion, REASON);
    }

    /** 审计属性键值。 */
    protected Map<String, String> attributes(Map<String, Object> event) {
        var result = new HashMap<String, String>();
        for (var attribute : auditData.attributes(((Number) event.get("audit_event_id")).longValue())) {
            result.put((String) attribute.get("attr_key"), (String) attribute.get("value_text"));
        }
        return result;
    }

    /** 数值列转 int。 */
    protected static int intValue(Object value) {
        return ((Number) value).intValue();
    }
}
