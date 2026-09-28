package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.test.builder.CredentialFixture;
import com.paytech.platform.support.test.builder.CredentialScopeFixture;
import com.paytech.platform.support.test.dal.ApiCredentialTestMapper;

import lombok.RequiredArgsConstructor;

/** 真实本地数据准备和归属登记；仅回收本测试成功插入的行。 */
@TestComponent
@RequiredArgsConstructor
public class ApiCredentialMocker implements Clear {

    private final DataSource dataSource;
    private final ApiCredentialTestMapper mapper;
    private final List<Long> tenantIds = new ArrayList<>();
    private final List<Long> credentialIds = new ArrayList<>();
    private final List<String> protectedValues = new ArrayList<>();
    private final List<String> requestIds = new ArrayList<>();
    private boolean unusable;

    /** 每例检查本地连接和上一例回收结果，禁止在 BeforeEach 清理数据。 */
    public void assertEmpty() {
        assertFalse(unusable, "测试数据清理失败，当前环境不可复用");
        assertTrue(tenantIds.isEmpty() && credentialIds.isEmpty() && protectedValues.isEmpty() && requestIds.isEmpty(),
            "上一例测试状态未清理");
        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:13306/mor_platform?"),
                "PLT-I03 测试只允许仓库本地 MySQL mor_platform");
            assertEquals("mor_platform", mapper.databaseName());
            mapper.checkSchema();
            mapper.tenants(List.of(-1L));
            mapper.scopes(List.of(-1L));
            mapper.auditCount("plt-i03-schema-check");
        } catch (Exception failure) {
            throw databaseSetupFailure(failure);
        }
    }

    /** 区分认证、连通性和表结构错误，只保留安全的 SQLState/错误码，不输出连接秘密或 SQL 参数。 */
    AssertionError databaseSetupFailure(Exception failure) {
        SQLException sqlFailure = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                sqlFailure = sql;
            }
        }
        if (sqlFailure == null) {
            return new AssertionError("本地 Mock 数据库前置检查失败，异常类型=" + failure.getClass().getSimpleName());
        }
        int code = sqlFailure.getErrorCode();
        String state = sqlFailure.getSQLState();
        state = state != null && state.matches("[A-Z0-9]{5}") ? state : "UNKNOWN";
        String reason = switch (code) {
            case 1045 -> "本地 MySQL 账号认证失败；Mock 已自动读取 .env.local，请核对其中的平台密码与实例是否一致";
            case 1049 -> "本地 mor_platform 数据库不存在；请手工准备 src/test/resources/init/ddl.sql";
            case 1146, 1054 -> "本地测试表结构缺失或不匹配；请核对 src/test/resources/init/ddl.sql";
            default -> state.startsWith("08") ? "无法连接本地 MySQL，请检查 local-environment 服务和 13306 端口"
                : "本地 Mock 数据库前置检查失败";
        };
        return new AssertionError(reason + " [SQLState=" + state + ", errorCode=" + code + "]");
    }

    /** 插入独立租户和凭证；PHC 在测试侧计算，生产 verifier 仍真实执行。 */
    public CredentialFixture create(CredentialFixture fixture) {
        if (fixture.isTenantPresent()) {
            assertEquals(1, mapper.insertTenant(fixture, CredentialFixture.NOW));
            tenantIds.add(fixture.getTenantId());
        }
        String phc = fixture.getVerifierPhc();
        if (phc == null) {
            phc = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2).encode(fixture.credential());
        }
        protectedValues.add(fixture.credential());
        if (!phc.isBlank()) {
            protectedValues.add(phc);
        }
        assertEquals(1, mapper.insertCredential(fixture, phc, CredentialFixture.NOW));
        credentialIds.add(fixture.getCredentialId());
        return fixture;
    }

    /** 为已经登记的本例凭证插入授权行。 */
    public void scope(CredentialFixture fixture, CredentialScopeFixture scope) {
        assertTrue(credentialIds.contains(fixture.getCredentialId()), "不能给其他任务的凭证准备授权");
        assertEquals(1, mapper.insertScope(fixture.getCredentialId(), scope, CredentialFixture.NOW));
    }

    /** 模拟在两次认证之间已提交的撤销事实。 */
    public void revoke(CredentialFixture fixture) {
        assertTrue(credentialIds.contains(fixture.getCredentialId()), "不能修改其他任务的凭证");
        assertEquals(1, mapper.revoke(fixture.getCredentialId()));
    }

    /** 返回只读归属列表给数据库 Checker。 */
    public List<Long> tenantIds() {
        return List.copyOf(tenantIds);
    }

    /** 返回只读归属列表给数据库 Checker。 */
    public List<Long> credentialIds() {
        return List.copyOf(credentialIds);
    }

    /** 登记本例请求，负例发现意外审计后仍可安全回收。 */
    public void registerRequest(String requestId) {
        requestIds.add(requestId);
    }

    /** 有效和畸形的长凭证都作为日志 canary，短格式负例避免匹配普通日志词。 */
    public void protectInput(String credential) {
        if (credential != null && credential.length() >= 32) {
            protectedValues.add(credential);
        }
    }

    /** 不在断言消息中打印合成 Credential 或 verifier。 */
    public void assertNoSecrets(String text) {
        for (String value : protectedValues) {
            assertFalse(text.contains(value), "响应或日志包含认证材料");
        }
    }

    /** 统一 AfterEach 回收成功和失败用例的所有已登记事实；失败后阻止复用。 */
    @Override
    public void clear() {
        try {
            var actions = new ArrayList<org.junit.jupiter.api.function.Executable>();
            requestIds.forEach(id -> {
                actions.add(() -> mapper.deleteAuditAttributes(id));
                actions.add(() -> mapper.deleteAudit(id));
            });
            credentialIds.forEach(id -> {
                actions.add(() -> mapper.deleteScopes(id));
                actions.add(() -> mapper.deleteCredential(id));
            });
            tenantIds.forEach(id -> actions.add(() -> mapper.deleteTenant(id)));
            assertAll("清理全部本例数据库事实", actions);
            if (!credentialIds.isEmpty()) {
                assertTrue(mapper.credentials(credentialIds).isEmpty(), "凭证清理不完整");
                assertTrue(mapper.scopes(credentialIds).isEmpty(), "授权清理不完整");
            }
            if (!tenantIds.isEmpty()) {
                assertTrue(mapper.tenants(tenantIds).isEmpty(), "租户清理不完整");
            }
            tenantIds.clear();
            credentialIds.clear();
            protectedValues.clear();
            requestIds.clear();
        } catch (RuntimeException | AssertionError failure) {
            unusable = true;
            throw failure;
        }
    }
}
