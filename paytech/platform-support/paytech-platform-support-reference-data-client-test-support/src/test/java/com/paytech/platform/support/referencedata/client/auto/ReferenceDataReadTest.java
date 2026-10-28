package com.paytech.platform.support.referencedata.client.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.client.ReferenceCatalog;
import com.paytech.platform.support.referencedata.client.ReferenceCountry;
import com.paytech.platform.support.referencedata.client.ReferenceCurrency;
import com.paytech.platform.support.referencedata.client.ReferenceData;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataNotFoundException;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.ReferenceDataUnavailableException;
import com.paytech.platform.support.referencedata.client.ReferenceIndustry;
import com.paytech.platform.support.referencedata.client.ReferenceMcc;
import com.paytech.platform.support.referencedata.client.ReferenceSubdivision;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders;
import com.paytech.platform.support.referencedata.client.test.base.ReferenceDataClientTestBase;

/**
 * 读方法：五类全量加载后的取值、只加载已开启种类（AC-01）、读方法零 HTTP 与查不到报错（AC-05）、"存在即可"与"必须 ACTIVE"（AC-06）。
 *
 * @see ReferenceDataRefresher#refreshNow()
 * @see ReferenceCatalog#require(String)
 * @see ReferenceCatalog#requireActive(String)
 * @see ReferenceCatalog#find(String)
 * @see ReferenceCatalog#list()
 */
class ReferenceDataReadTest extends ReferenceDataClientTestBase {

    /** 五类全部开启：每类整体加载成功，值对象只带后端字段，list 按编码升序，健康 UP。 */
    @Test
    void test_normalFlow() {
        MOCKER.defaults();
        runner(ReferenceDataKind.values()).run(context -> {
            List<ReferenceDataRefresher.Result> results = context.getBean(ReferenceDataRefresher.class).refreshNow();

            assertEquals(List.of(new ReferenceDataRefresher.Result(ReferenceDataKind.CURRENCY, true, 4, null),
                new ReferenceDataRefresher.Result(ReferenceDataKind.COUNTRY, true, 6, null),
                new ReferenceDataRefresher.Result(ReferenceDataKind.SUBDIVISION, true, 4, null),
                new ReferenceDataRefresher.Result(ReferenceDataKind.INDUSTRY, true, 5, null),
                new ReferenceDataRefresher.Result(ReferenceDataKind.MCC, true, 5, null)), results);
            ReferenceData data = context.getBean(ReferenceData.class);
            assertEquals(new ReferenceCurrency("USD", "840", 2, CurrencyRoundingMode.HALF_UP, ReferenceDataStatus.ACTIVE),
                data.currencies().require("USD"));
            assertEquals(new ReferenceCountry("AE", "ARE", "784", "GCC", "971", "AED", ReferenceDataStatus.ACTIVE),
                data.countries().require("AE"));
            assertEquals(new ReferenceSubdivision("SA-01", "SA", null, "REGION", ReferenceDataStatus.ACTIVE),
                data.subdivisions().require("SA-01"));
            assertEquals(new ReferenceIndustry("RETAIL_APPAREL", "RETAIL", 2, "5651", ReferenceDataStatus.ACTIVE),
                data.industries().require("RETAIL_APPAREL"));
            assertEquals(new ReferenceIndustry("RETAIL", null, 1, null, ReferenceDataStatus.ACTIVE),
                data.industries().require("RETAIL"));
            assertEquals(new ReferenceMcc("7995", true, ReferenceDataStatus.ACTIVE), data.mccs().require("7995"));
            assertEquals(List.of("AED", "EGP", "SAR", "USD"),
                data.currencies().list().stream().map(ReferenceCurrency::code).toList());
            assertThrows(UnsupportedOperationException.class, () -> data.currencies().list().clear());
            Health health = health(context);
            assertEquals(Status.UP, health.getStatus());
            assertEquals(5, health.getDetails().size());
            assertEquals(6, kindDetails(health, ReferenceDataKind.COUNTRY).get("count"));
        });
    }

    /** 只加载配置的种类：未开启的种类不请求 Platform，读取时报"未配置"；健康明细只含已开启种类（AC-01）。 */
    @Test
    void test_onlyConfiguredKinds() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD"));
        runner(ReferenceDataKind.CURRENCY).run(context -> {
            List<ReferenceDataRefresher.Result> results = context.getBean(ReferenceDataRefresher.class).refreshNow();

            assertEquals(List.of(ReferenceDataKind.CURRENCY),
                results.stream().map(ReferenceDataRefresher.Result::kind).toList());
            assertEquals(1, MOCKER.totalCalls());
            ReferenceData data = context.getBean(ReferenceData.class);
            assertEquals(2, data.currencies().require("USD").minorUnit());
            for (ReferenceCatalog<?> catalog : List.of(data.countries(), data.subdivisions(), data.industries(),
                data.mccs())) {
                ReferenceDataUnavailableException failure = assertThrows(ReferenceDataUnavailableException.class,
                    () -> catalog.find("ANY"));
                assertTrue(failure.getMessage().startsWith("参考数据未配置: "), failure.getMessage());
                assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), failure.getCode());
            }
            assertEquals("参考数据未配置: MCC", assertThrows(ReferenceDataUnavailableException.class,
                () -> data.mccs().list()).getMessage());
            assertEquals(Map.of(ReferenceDataKind.CURRENCY.getCode(), kindDetails(health(context),
                ReferenceDataKind.CURRENCY)), health(context).getDetails());
        });
    }

    /** 读方法只读内存：大量读取（含查不到）前后 Platform 请求次数不变；查不到抛带种类和编码的系统异常（AC-05）。 */
    @Test
    void test_readsAreMemoryOnly() {
        MOCKER.defaults();
        runner(ReferenceDataKind.values()).run(context -> {
            context.getBean(ReferenceDataRefresher.class).refreshNow();
            int callsAfterLoad = MOCKER.totalCalls();
            ReferenceData data = context.getBean(ReferenceData.class);

            for (int round = 0; round < 20; round++) {
                data.currencies().require("USD");
                data.countries().requireActive("SA");
                data.subdivisions().find("EG-C");
                data.industries().list();
                data.mccs().find("0000");
                assertThrows(ReferenceDataNotFoundException.class, () -> data.currencies().require("XYZ"));
            }

            assertEquals(callsAfterLoad, MOCKER.totalCalls());
            ReferenceDataNotFoundException failure = assertThrows(ReferenceDataNotFoundException.class,
                () -> data.mccs().require("0000"));
            assertEquals("参考数据不存在: MCC 0000", failure.getMessage());
            assertEquals(ReferenceDataKind.MCC, failure.getKind());
            assertEquals("0000", failure.getReferenceCode());
            assertEquals(CommonErrorCode.INTERNAL_ERROR.getCode(), failure.getCode());
        });
    }

    /** ACTIVE / INACTIVE / DRAFT 与不存在：存在即可、必须 ACTIVE、find 三种读法的结果（AC-06）。 */
    @Test
    void test_statusReads() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD"),
            ReferenceDataBuilders.currency("AED").status(ReferenceDataStatus.INACTIVE),
            ReferenceDataBuilders.currency("SAR").status(ReferenceDataStatus.DRAFT));
        runner(ReferenceDataKind.CURRENCY).run(context -> {
            context.getBean(ReferenceDataRefresher.class).refreshNow();
            ReferenceCatalog<ReferenceCurrency> currencies = context.getBean(ReferenceData.class).currencies();

            assertEquals(ReferenceDataStatus.INACTIVE, currencies.require("AED").status());
            assertEquals(ReferenceDataStatus.DRAFT, currencies.require("SAR").status());
            assertEquals(ReferenceDataStatus.ACTIVE, currencies.requireActive("USD").status());
            assertEquals("参考数据未启用: CURRENCY AED, 状态 INACTIVE",
                assertThrows(BizCheckFailException.class, () -> currencies.requireActive("AED")).getMessage());
            assertEquals("参考数据未启用: CURRENCY SAR, 状态 DRAFT",
                assertThrows(BizCheckFailException.class, () -> currencies.requireActive("SAR")).getMessage());
            assertTrue(currencies.find("AED").isPresent());
            assertFalse(currencies.find("XYZ").isPresent());
            assertFalse(currencies.find(null).isPresent());
            assertThrows(ReferenceDataNotFoundException.class, () -> currencies.require("XYZ"));
            assertThrows(ReferenceDataNotFoundException.class, () -> currencies.requireActive("XYZ"));
            assertThrows(ReferenceDataNotFoundException.class, () -> currencies.require(null));
            assertEquals(3, currencies.list().size());
        });
    }

    /** 始终带 X-Caller-Service；宿主提供 InternalTokenSupplier 时同时附加 platform-support-service 的内部调用 Token，没有时不带。 */
    @Test
    void test_internalToken() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD"));
        runner(ReferenceDataKind.CURRENCY).run(context -> {
            context.getBean(ReferenceDataRefresher.class).refreshNow();
            assertNull(MOCKER.lastAuthorization());
            assertEquals(CALLER_SERVICE, MOCKER.lastCallerService());
        });
        runner(ReferenceDataKind.CURRENCY)
            .withBean(com.paytech.foundation.internalauth.api.InternalTokenSupplier.class,
                () -> (target, purpose) -> "token-for-" + target + "-" + purpose)
            .run(context -> {
                context.getBean(ReferenceDataRefresher.class).refreshNow();
                assertEquals("Bearer token-for-platform-support-service-GENERAL", MOCKER.lastAuthorization());
                assertEquals(CALLER_SERVICE, MOCKER.lastCallerService());
            });
    }

    /** 健康明细中一个种类的内容。 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> kindDetails(Health health, ReferenceDataKind kind) {
        return (Map<String, Object>)health.getDetails().get(kind.getCode());
    }
}
