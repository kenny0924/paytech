package com.paytech.platform.support.referencedata.client.auto;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.referencedata.client.ReferenceData;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.ReferenceDataUnavailableException;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders;
import com.paytech.platform.support.referencedata.client.test.base.ReferenceDataClientTestBase;

/**
 * 加载与刷新：启动时 Platform 不可用与短周期重试（AC-02）、刷新失败保留旧快照（AC-03）、刷新读到新值与周期切换（AC-04）。
 *
 * @see ReferenceDataRefresher#refreshNow()
 * @see ReferenceData#currencies()
 */
class ReferenceDataRefreshTest extends ReferenceDataClientTestBase {

    /**
     * 启动时 Platform 不可用：不阻塞启动，readiness OUT_OF_SERVICE，读方法报"未就绪"；只重试未成功的种类；
     * 恢复后下一次短周期重试成功、变为 UP，此后转入 1 小时刷新周期不再请求（AC-02、AC-04）。
     */
    @Test
    void test_platformUnavailableAtStartup() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD")).unavailable(ReferenceDataKind.CURRENCY)
            .country(ReferenceDataBuilders.country("AE"));
        runner(ReferenceDataKind.CURRENCY, ReferenceDataKind.COUNTRY)
            .withPropertyValues("platform-support.reference-data.client.scheduling-enabled=true",
                "platform-support.reference-data.client.initial-retry-interval=PT1S")
            .run(context -> {
                ReferenceData data = context.getBean(ReferenceData.class);
                await().atMost(Duration.ofSeconds(10)).until(() -> MOCKER.callCount(ReferenceDataKind.CURRENCY) >= 2);

                assertEquals(Status.OUT_OF_SERVICE, health(context).getStatus());
                assertEquals("参考数据未就绪: CURRENCY",
                    assertThrows(ReferenceDataUnavailableException.class, () -> data.currencies().find("USD"))
                        .getMessage());
                assertEquals("AE", data.countries().require("AE").code());
                assertEquals(1, MOCKER.callCount(ReferenceDataKind.COUNTRY));
                Map<String, Object> currency = ReferenceDataReadTest.kindDetails(health(context),
                    ReferenceDataKind.CURRENCY);
                assertEquals(0, currency.get("count"));
                assertFalse(currency.containsKey("loadedAt"));
                assertEquals(ServiceUnavailableException.class.getName(), currency.get("lastFailure"));

                MOCKER.recover(ReferenceDataKind.CURRENCY);
                await().atMost(Duration.ofSeconds(10)).until(() -> health(context).getStatus().equals(Status.UP));

                assertEquals(2, data.currencies().require("USD").minorUnit());
                int callsWhenReady = MOCKER.totalCalls();
                await().during(Duration.ofMillis(2500)).atMost(Duration.ofSeconds(4))
                    .until(() -> MOCKER.totalCalls() == callsWhenReady);
            });
    }

    /** 已就绪后 Platform 不可用：保留旧快照继续服务，健康仍 UP 并给出最近成功时间与连续失败次数；恢复后清零（AC-03）。 */
    @Test
    void test_refreshFailureKeepsSnapshot() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD"));
        runner(ReferenceDataKind.CURRENCY).run(context -> {
            ReferenceDataRefresher refresher = context.getBean(ReferenceDataRefresher.class);
            refresher.refreshNow();
            Object loadedAt = ReferenceDataReadTest.kindDetails(health(context), ReferenceDataKind.CURRENCY)
                .get("loadedAt");
            MOCKER.unavailable(ReferenceDataKind.CURRENCY);

            assertEquals(List.of(new ReferenceDataRefresher.Result(ReferenceDataKind.CURRENCY, false, 0,
                ServiceUnavailableException.class.getName())), refresher.refreshNow());
            refresher.refreshNow();

            assertEquals(2, context.getBean(ReferenceData.class).currencies().require("USD").minorUnit());
            Health health = health(context);
            assertEquals(Status.UP, health.getStatus());
            Map<String, Object> details = ReferenceDataReadTest.kindDetails(health, ReferenceDataKind.CURRENCY);
            assertEquals(1, details.get("count"));
            assertEquals(loadedAt, details.get("loadedAt"));
            assertEquals(2, details.get("consecutiveFailures"));
            assertEquals(ServiceUnavailableException.class.getName(), details.get("lastFailure"));

            MOCKER.recover(ReferenceDataKind.CURRENCY);
            assertTrue(refresher.refreshNow().getFirst().succeeded());
            assertEquals(0, ReferenceDataReadTest.kindDetails(health(context), ReferenceDataKind.CURRENCY)
                .get("consecutiveFailures"));
        });
    }

    /** Platform 改数据后刷新：整体替换为新快照，新增、修改、删除的编码都按新值读取（AC-04）。 */
    @Test
    void test_refreshPicksUpChanges() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD"), ReferenceDataBuilders.currency("AED").numericCode("784"));
        runner(ReferenceDataKind.CURRENCY).run(context -> {
            ReferenceDataRefresher refresher = context.getBean(ReferenceDataRefresher.class);
            ReferenceData data = context.getBean(ReferenceData.class);
            refresher.refreshNow();
            MOCKER.reset(ReferenceDataKind.CURRENCY).currency(ReferenceDataBuilders.currency("USD").minorUnit(3),
                ReferenceDataBuilders.currency("JPY").numericCode("392").minorUnit(0));

            assertTrue(refresher.refreshNow().getFirst().succeeded());

            assertEquals(3, data.currencies().require("USD").minorUnit());
            assertEquals(0, data.currencies().require("JPY").minorUnit());
            assertFalse(data.currencies().find("AED").isPresent());
        });
    }

    /** 未配置刷新间隔与重试间隔时使用默认 PT1H / PT10S；调度默认开启（AC-04 默认 1 小时）。 */
    @Test
    void test_defaultIntervals() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD"));
        runner(ReferenceDataKind.CURRENCY)
            .withPropertyValues("platform-support.reference-data.client.scheduling-enabled=true")
            .run(context -> {
                var properties = context.getBean(
                    com.paytech.platform.support.referencedata.client.autoconfigure.ReferenceDataClientProperties.class);
                assertEquals(Duration.ofHours(1), properties.refreshInterval());
                assertEquals(Duration.ofSeconds(10), properties.initialRetryInterval());
                assertTrue(properties.schedulingEnabled());
                assertEquals(Duration.ofSeconds(2), properties.connectTimeout());
                assertEquals(Duration.ofSeconds(5), properties.readTimeout());
                await().atMost(Duration.ofSeconds(10)).until(() -> health(context).getStatus().equals(Status.UP));
                assertNotNull(ReferenceDataReadTest.kindDetails(health(context), ReferenceDataKind.CURRENCY)
                    .get("loadedAt"));
            });
    }

    /** 启动前未刷新：每个已开启种类均未就绪，健康 OUT_OF_SERVICE，没有失败记录。 */
    @Test
    void test_noDataFound() {
        runner(ReferenceDataKind.CURRENCY, ReferenceDataKind.MCC).run(context -> {
            Health health = health(context);

            assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
            assertNull(ReferenceDataReadTest.kindDetails(health, ReferenceDataKind.MCC).get("lastFailure"));
            assertThrows(ReferenceDataUnavailableException.class,
                () -> context.getBean(ReferenceData.class).mccs().list());
            assertEquals(0, MOCKER.totalCalls());
        });
    }

    /** 种类失败只影响该种类：币种响应不合法时其余种类照常装入（失败异常为 UPSTREAM_INVALID_RESPONSE 系统异常）。 */
    @Test
    void test_failureIsolatedPerKind() {
        MOCKER.defaults().currency(ReferenceDataBuilders.currency("KWD").numericCode("414").minorUnit(9));
        runner(ReferenceDataKind.values()).run(context -> {
            List<ReferenceDataRefresher.Result> results = context.getBean(ReferenceDataRefresher.class).refreshNow();

            assertEquals(List.of(false, true, true, true, true),
                results.stream().map(ReferenceDataRefresher.Result::succeeded).toList());
            assertEquals(ErrorException.class.getName(), results.getFirst().failure());
            assertEquals(Status.OUT_OF_SERVICE, health(context).getStatus());
            assertEquals("AE", context.getBean(ReferenceData.class).countries().require("AE").code());
        });
    }
}
