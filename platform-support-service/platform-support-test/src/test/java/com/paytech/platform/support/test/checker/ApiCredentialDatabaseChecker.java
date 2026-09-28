package com.paytech.platform.support.test.checker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.test.dal.ApiCredentialTestMapper;
import com.paytech.platform.support.test.mocker.local.ApiCredentialMocker;

import lombok.AllArgsConstructor;

/** 检查认证接口的只读约束，快照仅覆盖本例拥有的数据。 */
@TestComponent
@AllArgsConstructor
public class ApiCredentialDatabaseChecker {

    private final ApiCredentialTestMapper mapper;
    private final ApiCredentialMocker mocker;

    /** 保存请求前完整数据库事实；不从响应构造预期数据。 */
    public Snapshot snapshot() {
        var ids = mocker.credentialIds();
        var tenants = mocker.tenantIds();
        return new Snapshot(tenants.isEmpty() ? List.of() : mapper.tenants(tenants),
            ids.isEmpty() ? List.of() : mapper.credentials(ids), ids.isEmpty() ? List.of() : mapper.scopes(ids));
    }

    /** 不输出完整行，避免失败诊断泄露 PHC；同时检查没有新增审计。 */
    public void assertUnchanged(Snapshot before, String requestId) {
        Snapshot after = snapshot();
        assertAll(() -> assertTrue(before.tenants.equals(after.tenants), "认证不得修改 Tenant"),
            () -> assertTrue(before.credentials.equals(after.credentials), "认证不得修改 Credential"),
            () -> assertTrue(before.scopes.equals(after.scopes), "认证不得修改 Scope"),
            () -> assertEquals(0, mapper.auditCount(requestId), "认证不得持久化审计事件"));
    }

    /** 单次请求只读证据，不自动生成含认证材料的 toString。 */
    @AllArgsConstructor
    public static final class Snapshot {
        private final List<Map<String, Object>> tenants;
        private final List<Map<String, Object>> credentials;
        private final List<Map<String, Object>> scopes;
    }
}
