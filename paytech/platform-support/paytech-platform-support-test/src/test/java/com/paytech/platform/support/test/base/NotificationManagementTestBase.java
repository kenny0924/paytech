package com.paytech.platform.support.test.base;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.NotificationTestMapper;
import com.paytech.platform.support.test.notification.MockEmailProvider;
import com.paytech.platform.support.test.notification.NotificationFixture;
import com.paytech.platform.support.test.notification.NotificationVaultMock;

/**
 * Operations 通知管理接口测试的公共准备：普通与认证模板、四个发送账号，以及三类人员
 * <li>管理员持有全部权限；只读人员只有 NOTIFICATION_READ；运营只读人员没有通知权限</li>
 */
public abstract class NotificationManagementTestBase extends OperationsApiTestBase {

    protected static final String BASE = "/notification-admin/v1";

    @Autowired protected NotificationFixture fixture;
    @Autowired protected NotificationVaultMock vault;
    @Autowired protected MockEmailProvider provider;
    @Autowired protected NotificationTestMapper mapper;

    protected Operator admin;
    protected Operator reader;
    protected Operator viewer;

    /** 父类全局初始化后插入通知配置与三类人员。 */
    @BeforeEach
    void setUpNotificationManagementMocks() {
        fixture.prepareManagement();
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        reader = operationsMocker.operator(OperatorBuilder.administrator()
            .roles(operationsMocker.customRole("NOTIFICATION_READER", "NOTIFICATION_READ")));
        viewer = operationsMocker.operator(OperatorBuilder.viewer());
    }

    /** 异步超时后不清理仍可能被后台访问的资源。 */
    @Override
    protected void checkClearAllowed() {
        fixture.assertReusable();
    }

    /** 等待真实发送链路结束，再检查本例约定的 Provider 与 Vault 调用；Mock HTTP 由全局 AfterEach 检查。 */
    @AfterEach
    void checkNotificationManagementCalls() {
        fixture.awaitIdle();
        Assertions.assertAll(provider::assertExpected, vault::assertExpected);
    }

    /** 以人员身份 PATCH。 */
    protected PlatformResponseChecker patch(String path, Operator operator, Object body) {
        return call(HttpMethod.PATCH, path, headers(operator), body);
    }

    /** 启停请求体。 */
    protected static Map<String, Object> status(String status, int expectedVersion) {
        return Map.of("status", status, "expectedVersion", expectedVersion, "reason", "测试启停");
    }
}
