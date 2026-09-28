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

    private final NotificationTestMapper mapper;
    private final PlatformTransactionManager transactionManager;
    private boolean used;
    private boolean reusable = true;

    /** 缺表快速失败，之后每例分别插入公共业务配置。 */
    public void prepare() {
        assertTrue(reusable, "通知测试后台未结束，环境不可复用");
        try {
            assertEquals(0, mapper.requests());
            assertEquals(0, mapper.notificationTaskCount(), "通知测试需独占空通知任务环境");
        }
        catch (org.springframework.dao.DataAccessException unavailable) {
            throw new AssertionError("请手工准备 init/ddl.sql 中的七张 Notification 表及 t_compensation_event，再运行通知测试；测试不自动建表", unavailable);
        }
        assertEquals(0, mapper.reservedConfigurationCount(), "通知测试配置身份已被占用，请核对本地数据");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            mapper.template();
            mapper.contents("通知 ${name}", "${notice}：${name}", "Hello ${name}", "${notice}: ${name}");
            mapper.provider(); mapper.route();
        });
        used = true;
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
            mapper.clearRoute(); mapper.clearProvider(); mapper.clearContents(); mapper.clearTemplate();
            used = false;
        } catch (RuntimeException failure) { reusable = false; throw failure; }
    }
}
