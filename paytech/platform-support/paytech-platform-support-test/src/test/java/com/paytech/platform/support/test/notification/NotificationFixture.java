package com.paytech.platform.support.test.notification;

import java.time.Duration;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.test.dal.NotificationTestMapper;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.RequiredArgsConstructor;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** 当前用例通知 Fixture；只清理自身身份，不启动或修改 Schema。 */
@TestComponent
@Order(-100)
@RequiredArgsConstructor
public class NotificationFixture implements Clear {

    /** 认证即时发送模板主题与正文，与初始化 DML 一致。 */
    public static final String VERIFICATION_SUBJECT = "PAYTECH · 验证您的邮箱";

    public static final String VERIFICATION_BODY =
        "验证您的邮箱\n\n请打开以下链接，并在页面中确认操作：\n${link}\n\n如果不是您发起的请求，请忽略此邮件。";

    public static final String RESET_SUBJECT = "PAYTECH · 重置登录密码";

    public static final String RESET_BODY =
        "重置登录密码\n\n请打开以下链接，并在页面中确认操作：\n${link}\n\n如果不是您发起的请求，请忽略此邮件。";

    private final NotificationTestMapper mapper;
    private final PlatformTransactionManager transactionManager;
    private boolean used;
    private boolean reusable = true;

    /** 缺表快速失败，之后每例分别插入公共业务配置。 */
    public void prepare() {
        assertEmptyEnvironment();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            mapper.template();
            mapper.contents("通知 ${name}", "${notice}：${name}", "Hello ${name}", "${notice}: ${name}");
            mapper.provider(); mapper.route();
        });
        used = true;
    }

    /** 缺表快速失败；通知事实与保留配置身份必须为空。 */
    void assertEmptyEnvironment() {
        assertTrue(reusable, "通知测试后台未结束，环境不可复用");
        try {
            assertEquals(0, mapper.requests());
            assertEquals(0, mapper.notificationTaskCount(), "通知测试需独占空通知任务环境");
        }
        catch (org.springframework.dao.DataAccessException unavailable) {
            throw new AssertionError("请手工准备 init/ddl.sql 中的七张 Notification 表及 t_compensation_event，再运行通知测试；测试不自动建表", unavailable);
        }
        assertEquals(0, mapper.reservedConfigurationCount(), "通知测试配置身份已被占用，请核对本地数据");
    }

    /** 认证邮件用例：只插入 SES 测试账号与两个认证模板，不插入普通通知模板。 */
    public void prepareAuthentication() {
        assertEmptyEnvironment();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            mapper.sesProvider();
            mapper.authenticationTemplates();
            mapper.authenticationContents(VERIFICATION_SUBJECT, VERIFICATION_BODY, RESET_SUBJECT, RESET_BODY);
            mapper.authenticationRoutes();
        });
        used = true;
    }

    /**
     * 通知管理用例：普通模板（中英文）、三个有适配器的账号、一个无适配器账号，以及两个认证模板及其路由
     * <li>管理接口新建的模板编码以 NOTIFICATION_ADMIN_ 开头，统一清理</li>
     */
    public void prepareManagement() {
        assertEmptyEnvironment();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            mapper.template();
            mapper.contents("通知 ${name}", "${notice}：${name}", "Hello ${name}", "${notice}: ${name}");
            mapper.provider(); mapper.route(); mapper.fallbackProvider(); mapper.unboundProvider();
            mapper.sesProvider();
            mapper.authenticationTemplates();
            mapper.authenticationContents(VERIFICATION_SUBJECT, VERIFICATION_BODY, RESET_SUBJECT, RESET_BODY);
            mapper.authenticationRoutes();
        });
        used = true;
    }

    /** 插入一条普通通知发送记录（请求、两个投递、三次尝试），不经过发送链路。 */
    public void prepareRecord(java.time.Instant created) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            mapper.recordRequest(created); mapper.recordDeliveries(created); mapper.recordAttempts(created);
        });
    }

    /** 等待实际 Dispatcher 的写回结束，再允许断言或清理。 */
    public void awaitIdle() {
        if (!used) return;
        try { await().atMost(Duration.ofSeconds(10)).until(() -> mapper.activeTasks() == 0); }
        catch (RuntimeException | AssertionError timeout) { reusable = false; throw timeout; }
    }

    /** 背景任务未停止时阻止公共清理继续清掉正在使用的 Mock。 */
    public void assertReusable() { assertTrue(reusable, "通知测试环境不可安全清理"); }

    /** 清理只在公共 AfterEach 调用，且必须先证实没有活动任务。 */
    @Override
    public void clear() {
        if (!used) return;
        assertTrue(reusable, "后台任务未停止，拒绝清理");
        awaitIdle();
        try {
            mapper.clearEvents(); mapper.clearAttempts(); mapper.clearDeliveries(); mapper.clearRequests();
            mapper.clearAdminContents(); mapper.clearAdminRoutes(); mapper.clearAdminTemplates();
            mapper.clearRoute(); mapper.clearProvider(); mapper.clearContents(); mapper.clearTemplate();
            used = false;
        } catch (RuntimeException failure) { reusable = false; throw failure; }
    }
}
