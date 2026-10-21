package com.paytech.channel.connector.auto.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.protocol.domainservice.repository.ApiFieldRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;

/**
 * 外部 API 配置数据层冒烟：真实 Mapper/XML、CodeEnum TypeHandler、唯一索引与版本 CAS。
 * 管理接口测试从 HTTP 入口覆盖完整业务矩阵，本类只锁定数据层映射；协议表由 ChannelDataMocker 整表回收。
 *
 * @see StandardApiRepository
 * @see ApiFieldRepository
 * @see IntegrationRepository
 * @see IntegrationImplementationRepository
 */
class ExternalApiRepositorySmokeTest extends ChannelConnectorMockTestBase {

    private static final long OPERATOR_ID = 900_000_000_000_000_001L;

    @Autowired
    private StandardApiRepository standardApiRepository;

    @Autowired
    private IntegrationRepository integrationRepository;

    @Autowired
    private ApiFieldRepository apiFieldRepository;

    @Autowired
    private IntegrationImplementationRepository implementationRepository;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    /** 插入、按编码读取、条件更新后版本递增，旧版本更新与删除被拒绝。 */
    @Test
    void test_standardApiVersionCas() {
        StandardApi row = standardApi();
        StandardApi stale = standardApiRepository.byCode(row.getStandardApiCode());
        assertEquals(0, stale.getVersion());
        assertEquals(row.getStandardApiName(), stale.getStandardApiName());

        standardApiRepository.update(row, "支付下单", "更新说明", OPERATOR_ID, now.plusSeconds(1));
        assertEquals(1, row.getVersion());
        StandardApi current = standardApiRepository.byCode(row.getStandardApiCode());
        assertEquals(1, current.getVersion());
        assertEquals("支付下单", current.getStandardApiName());

        assertThrows(ConcurrentRequestException.class,
            () -> standardApiRepository.update(stale, "旧版本", null, OPERATOR_ID, now));
        assertThrows(ConcurrentRequestException.class, () -> standardApiRepository.delete(stale));
        standardApiRepository.delete(current);
        assertNull(standardApiRepository.byCode(row.getStandardApiCode()));
    }

    /** 编码唯一索引转为业务异常，列表与计数使用同一筛选。 */
    @Test
    void test_duplicateCodeAndPaging() {
        StandardApi row = standardApi();
        StandardApi duplicate = new StandardApi(randomId(), row.getStandardApiCode(), "重复", null, 0,
            OPERATOR_ID, OPERATOR_ID, now, now);
        assertThrows(BizCheckFailException.class, () -> standardApiRepository.insert(duplicate));

        assertEquals(1, standardApiRepository.count(row.getStandardApiCode() + "%"));
        List<StandardApi> page = standardApiRepository.list(row.getStandardApiCode() + "%", 0, 10);
        assertEquals(List.of(row.getStandardApiId()), page.stream().map(StandardApi::getStandardApiId).toList());
        assertTrue(standardApiRepository.byIds(List.of()).isEmpty());
    }

    /** 字段按方向读取并按顺序号排序，枚举经 TypeHandler 往返。 */
    @Test
    void test_fieldsRoundTrip() {
        StandardApi api = standardApi();
        long apiId = api.getStandardApiId();
        apiFieldRepository.insertAll(List.of(
            field(apiId, ApiFieldDirection.STANDARD_API_REQUEST, "amount.value", ApiFieldDataType.DECIMAL, 2),
            field(apiId, ApiFieldDirection.STANDARD_API_REQUEST, "orderId", ApiFieldDataType.STRING, 1),
            field(apiId, ApiFieldDirection.STANDARD_API_RESPONSE, "status", ApiFieldDataType.STRING, 1)));
        apiFieldRepository.insertAll(List.of());

        List<ApiField> request = apiFieldRepository.byApis(List.of(apiId), List.of(ApiFieldDirection.STANDARD_API_REQUEST));
        assertEquals(List.of("orderId", "amount.value"), request.stream().map(ApiField::getFieldPath).toList());
        assertEquals(ApiFieldDataType.DECIMAL, request.get(1).getDataType());
        assertEquals(ApiFieldLocation.BODY, request.get(1).getFieldLocation());

        assertEquals(2, apiFieldRepository.deleteByApi(apiId, List.of(ApiFieldDirection.STANDARD_API_REQUEST)));
        assertEquals(1, apiFieldRepository.byApis(List.of(apiId), List.of(ApiFieldDirection.values())).size());
        assertTrue(apiFieldRepository.byApis(List.of(), List.of(ApiFieldDirection.values())).isEmpty());
    }

    /** 接入方出站地址 JSON 与实现流程 JSON 原样保存，同一接入方与标准 API 只允许一个实现。 */
    @Test
    void test_integrationAndImplementation() {
        StandardApi api = standardApi();
        Integration integration = new Integration(randomId(), "IT_" + randomId(), "测试渠道", null,
            "[{\"code\":\"DEFAULT\",\"baseUrl\":\"https://example.test\"}]", 0, OPERATOR_ID, OPERATOR_ID, now, now);
        integrationRepository.insert(integration);
        integrationRepository.update(integration, "测试渠道2", "说明", "[]", OPERATOR_ID, now);
        assertEquals("[]", integrationRepository.byCode(integration.getIntegrationCode()).getEndpoints());

        IntegrationImplementation implementation = new IntegrationImplementation(randomId(),
            integration.getIntegrationId(), api.getStandardApiId(), "{\"nodes\":[]}", 0, OPERATOR_ID, OPERATOR_ID, now, now);
        implementationRepository.insert(implementation);
        IntegrationImplementation duplicate = new IntegrationImplementation(randomId(),
            integration.getIntegrationId(), api.getStandardApiId(), "{}", 0, OPERATOR_ID, OPERATOR_ID, now, now);
        assertThrows(BizCheckFailException.class, () -> implementationRepository.insert(duplicate));

        implementationRepository.update(implementation, "{\"nodes\":[1]}", OPERATOR_ID, now);
        IntegrationImplementation current = implementationRepository.byKey(integration.getIntegrationId(), api.getStandardApiId());
        assertEquals(1, current.getVersion());
        assertEquals(1, implementationRepository.allByStandardApi(api.getStandardApiId()).size());
        assertEquals(1, implementationRepository.allByIntegrations(List.of(integration.getIntegrationId())).size());
    }

    /** 插入一行随机编码的标准 API，版本 0。 */
    private StandardApi standardApi() {
        StandardApi row = new StandardApi(randomId(), "SA_" + randomId(), "支付", "冒烟测试", 0,
            OPERATOR_ID, OPERATOR_ID, now, now);
        standardApiRepository.insert(row);
        return row;
    }

    /** BODY 必填字段行。 */
    private ApiField field(long apiId, ApiFieldDirection direction, String path, ApiFieldDataType type, int sortNo) {
        return new ApiField(randomId(), apiId, direction, ApiFieldLocation.BODY, path, path, type, true, sortNo, null, now, now);
    }

    /** 随机 13 位测试主键。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_000_000_000_000L);
    }
}
