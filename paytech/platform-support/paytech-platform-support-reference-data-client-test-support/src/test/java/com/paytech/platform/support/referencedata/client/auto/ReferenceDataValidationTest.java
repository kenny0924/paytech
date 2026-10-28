package com.paytech.platform.support.referencedata.client.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.referencedata.client.ReferenceData;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.ReferenceDataUnavailableException;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders.MccBuilder;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataMocker;
import com.paytech.platform.support.referencedata.client.test.base.ReferenceDataClientTestBase;

/**
 * 拉取校验：不合法响应使该种类本次失败且不装入半份数据；MCC 分页全量加载与总数核对；容忍 Platform 追加的未知字段；响应体上限。
 *
 * @see ReferenceDataRefresher#refreshNow()
 */
class ReferenceDataValidationTest extends ReferenceDataClientTestBase {

    /** 各类不合法数据：该种类刷新失败，异常为系统异常，读取仍报"未就绪"。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponses")
    void test_invalidParameter(String scenario, ReferenceDataKind kind, Consumer<ReferenceDataMocker> arrange) {
        arrange.accept(MOCKER);
        runner(kind).run(context -> {
            ReferenceDataRefresher.Result result = context.getBean(ReferenceDataRefresher.class).refreshNow().getFirst();

            assertFalse(result.succeeded(), scenario);
            assertEquals(ErrorException.class.getName(), result.failure(), scenario);
            assertThrows(ReferenceDataUnavailableException.class,
                () -> catalogList(context.getBean(ReferenceData.class), kind), scenario);
        });
    }

    /** 不合法响应的场景矩阵。 */
    static Stream<Arguments> invalidResponses() {
        return Stream.of(
            invalid("币种小数位超过 8", ReferenceDataKind.CURRENCY,
                mocker -> mocker.currency(ReferenceDataBuilders.currency("USD").minorUnit(9))),
            invalid("币种小数位为负", ReferenceDataKind.CURRENCY,
                mocker -> mocker.currency(ReferenceDataBuilders.currency("USD").minorUnit(-1))),
            invalid("币种缺少小数位", ReferenceDataKind.CURRENCY,
                mocker -> mocker.currency(ReferenceDataBuilders.currency("USD").minorUnit(null))),
            invalid("舍入方式未知", ReferenceDataKind.CURRENCY,
                mocker -> mocker.currency(ReferenceDataBuilders.currency("USD").roundingMode("HALF_EVEN"))),
            invalid("状态未知", ReferenceDataKind.CURRENCY,
                mocker -> mocker.currency(ReferenceDataBuilders.currency("USD").statusCode("RETIRED"))),
            invalid("编码为空", ReferenceDataKind.CURRENCY,
                mocker -> mocker.currency(ReferenceDataBuilders.currency(null))),
            invalid("编码格式非法", ReferenceDataKind.CURRENCY,
                mocker -> mocker.currency(ReferenceDataBuilders.currency("usd"))),
            invalid("编码重复", ReferenceDataKind.CURRENCY,
                mocker -> mocker.currency(ReferenceDataBuilders.currency("USD"), ReferenceDataBuilders.currency("USD"))),
            invalid("缺少 items", ReferenceDataKind.CURRENCY,
                mocker -> mocker.fault(ReferenceDataKind.CURRENCY, MockHttpFault.raw(200, "{}"))),
            invalid("国家法定币种格式非法", ReferenceDataKind.COUNTRY,
                mocker -> mocker.country(ReferenceDataBuilders.country("AE").defaultCurrencyCode("AEDX"))),
            invalid("国家缺少区域", ReferenceDataKind.COUNTRY,
                mocker -> mocker.country(ReferenceDataBuilders.country("AE").regionCode(null))),
            invalid("行政区与所属国家不一致", ReferenceDataKind.SUBDIVISION,
                mocker -> mocker.subdivision(ReferenceDataBuilders.subdivision("AE-DU").countryCode("SA"))),
            invalid("细类缺少上级", ReferenceDataKind.INDUSTRY,
                mocker -> mocker.industry(ReferenceDataBuilders.industry("RETAIL_APPAREL").level(2))),
            invalid("大类带上级", ReferenceDataKind.INDUSTRY,
                mocker -> mocker.industry(ReferenceDataBuilders.industry("RETAIL").parentCode("OTHER"))),
            invalid("层级非法", ReferenceDataKind.INDUSTRY,
                mocker -> mocker.industry(ReferenceDataBuilders.industry("RETAIL").level(3))),
            invalid("MCC 缺少高风险标记", ReferenceDataKind.MCC,
                mocker -> mocker.mcc(ReferenceDataBuilders.mcc("5734").networkHighRisk(null))),
            invalid("MCC 编码非四位数字", ReferenceDataKind.MCC,
                mocker -> mocker.mcc(ReferenceDataBuilders.mcc("573"))),
            invalid("MCC 总数大于实际条数", ReferenceDataKind.MCC,
                mocker -> mocker.mcc(mccs(3)).reportedMccTotal(4)),
            invalid("MCC 总数小于实际条数", ReferenceDataKind.MCC,
                mocker -> mocker.mcc(mccs(450)).reportedMccTotal(300)));
    }

    /** MCC 超过一页：每页 200 条翻到最后一页，三次请求装入全部 450 条。 */
    @Test
    void test_mccPaging() {
        MOCKER.mcc(mccs(450));
        runner(ReferenceDataKind.MCC).run(context -> {
            ReferenceDataRefresher.Result result = context.getBean(ReferenceDataRefresher.class).refreshNow().getFirst();

            assertEquals(new ReferenceDataRefresher.Result(ReferenceDataKind.MCC, true, 450, null), result);
            assertEquals(3, MOCKER.callCount(ReferenceDataKind.MCC));
            ReferenceData data = context.getBean(ReferenceData.class);
            assertEquals("1000", data.mccs().list().getFirst().code());
            assertEquals("1449", data.mccs().list().getLast().code());
        });
    }

    /** MCC 恰好整页：200 条一次请求即完成，空目录返回 0 条。 */
    @Test
    void test_edgeCase() {
        MOCKER.mcc(mccs(200)).empty(ReferenceDataKind.CURRENCY);
        runner(ReferenceDataKind.MCC, ReferenceDataKind.CURRENCY).run(context -> {
            List<ReferenceDataRefresher.Result> results = context.getBean(ReferenceDataRefresher.class).refreshNow();

            assertEquals(List.of(new ReferenceDataRefresher.Result(ReferenceDataKind.CURRENCY, true, 0, null),
                new ReferenceDataRefresher.Result(ReferenceDataKind.MCC, true, 200, null)), results);
            assertEquals(1, MOCKER.callCount(ReferenceDataKind.MCC));
            assertTrue(context.getBean(ReferenceData.class).currencies().list().isEmpty());
        });
    }

    /** Platform 在条目与外层追加未知字段时照常加载（宽松 JSON）。 */
    @Test
    void test_unknownFieldsTolerated() {
        MOCKER.fault(ReferenceDataKind.CURRENCY, MockHttpFault.raw(200, """
            {"items":[{"currencyCode":"USD","numericCode":"840","name":"US Dollar","symbol":"$","minorUnit":2,
            "roundingMode":"HALF_UP","status":"ACTIVE","version":1,"futureField":{"nested":true}}],"nextCursor":null}
            """));
        runner(ReferenceDataKind.CURRENCY).run(context -> {
            assertTrue(context.getBean(ReferenceDataRefresher.class).refreshNow().getFirst().succeeded());
            assertEquals(2, context.getBean(ReferenceData.class).currencies().require("USD").minorUnit());
        });
    }

    /** 响应体超过 1 MiB：该种类本次失败。 */
    @Test
    void test_responseTooLarge() {
        String padding = "x".repeat(1024 * 1024);
        MOCKER.fault(ReferenceDataKind.CURRENCY, MockHttpFault.raw(200, "{\"items\":[],\"padding\":\"" + padding + "\"}"));
        runner(ReferenceDataKind.CURRENCY).run(context -> {
            ReferenceDataRefresher.Result result = context.getBean(ReferenceDataRefresher.class).refreshNow().getFirst();

            assertFalse(result.succeeded());
            assertEquals(ErrorException.class.getName(), result.failure());
        });
    }

    /** 读取一个种类的全部条目。 */
    private static List<?> catalogList(ReferenceData data, ReferenceDataKind kind) {
        return switch (kind) {
            case CURRENCY -> data.currencies().list();
            case COUNTRY -> data.countries().list();
            case SUBDIVISION -> data.subdivisions().list();
            case INDUSTRY -> data.industries().list();
            case MCC -> data.mccs().list();
        };
    }

    /** 从 1000 开始的连续 MCC。 */
    private static MccBuilder[] mccs(int count) {
        return IntStream.range(0, count).mapToObj(index -> ReferenceDataBuilders.mcc(String.valueOf(1000 + index)))
            .toArray(MccBuilder[]::new);
    }

    /** 一个不合法场景。 */
    private static Arguments invalid(String scenario, ReferenceDataKind kind, Consumer<ReferenceDataMocker> arrange) {
        return Arguments.of(scenario, kind, arrange);
    }
}
