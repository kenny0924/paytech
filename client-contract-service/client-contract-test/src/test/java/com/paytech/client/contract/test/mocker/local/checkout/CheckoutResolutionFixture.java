package com.paytech.client.contract.test.mocker.local.checkout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import com.paytech.client.contract.domain.CheckoutQuery;
import com.paytech.client.contract.domainservice.service.CheckoutQueryService;
import com.paytech.client.contract.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.foundation.testsupport.api.Clear;

/** 仅管理本例成功插入的合成数据，保留共享开发库的其他数据。 */
@TestComponent
public class CheckoutResolutionFixture implements Clear {
    private final DataSource source;
    private final DataSourceProperties properties;
    private final CheckoutQueryService service;
    private CheckoutFixture fixture;
    private List<Long> compensationBefore;
    private boolean reusable = true;

    /** 校验实际测试依赖，避免清理阶段才发现空数据库或查询组件。 */
    public CheckoutResolutionFixture(DataSource source, DataSourceProperties properties,
            CheckoutQueryService service) {
        this.source = java.util.Objects.requireNonNull(source);
        this.properties = java.util.Objects.requireNonNull(properties);
        this.service = java.util.Objects.requireNonNull(service);
    }

    /** 检查测试连接和上例清理结果，不在准备阶段删除任何数据。 */
    public void assertEmpty() {
        assertTrue(reusable, "上例清理失败，不复用污染环境");
        assertNull(fixture, "前例 Fixture 尚未清理");
        assertTrue(properties.getUrl().startsWith("jdbc:mysql://127.0.0.1:13306/mor_cct?"));
    }

    /** 复用既有测试 Repository 插入合成场景；已有同键数据时失败，不覆盖。 */
    public void install() {
        fixture = new CheckoutFixture();
        compensationBefore = fixture.compensationEventIds(source);
        fixture.install(source);
    }

    /** 只调整本例两个 OE 的合成配置，验证业务支持范围无需部署白名单同步。 */
    public void useCommercialLocation(String country, String currency, String locale) {
        try (var connection = source.getConnection()) {
            connection.setReadOnly(false);
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
                    new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            jdbc.update("UPDATE t_operating_entity_scope SET consumer_country_code=?, currency=? "
                    + "WHERE entity_no IN ('AE991', 'AE992')", country, currency);
            jdbc.update("UPDATE t_consumer_legal_document SET country_code=?, locale=? "
                    + "WHERE entity_no IN ('AE991', 'AE992')", country, locale);
            jdbc.update("UPDATE t_seller_disclosure SET country_code=?, locale=? "
                    + "WHERE entity_no IN ('AE991', 'AE992')", country, locale);
        } catch (java.sql.SQLException exception) {
            throw new AssertionError("合成商业范围准备失败", exception);
        }
    }

    /** 验证操作前后全部读取事实和补偿身份不变，即使业务断言失败仍执行检查。 */
    public void assertReadOnly(Runnable operation) {
        var before = readFacts();
        var events = fixture.compensationEventIds(source);
        assertAll(operation::run,
                () -> assertEquals(before, readFacts()),
                () -> assertEquals(events, fixture.compensationEventIds(source)));
    }

    /** 通过正式只读 Service 观察所有原始事实，检查不依赖候选筛选或响应投影。 */
    private List<?> readFacts() {
        var query = CheckoutQuery.resolve(Long.toString(CheckoutFixture.CLIENT_ID), "READ-ONLY-CHECK",
                "AE", "USD", "en-AE", CheckoutFixture.NOW.toString(), CheckoutFixture.NOW);
        var context = service.load(query);
        return List.of(
                context.client(),
                context.businessProfiles(),
                context.classifications(),
                context.affiliations(),
                context.entities(),
                context.sources(),
                context.scopes(),
                context.scopeApplications(),
                context.partners(),
                context.agreements(),
                context.agreementEntities(),
                context.agreementParties(),
                context.agreementDocuments(),
                context.partyProfiles(),
                context.termPlans(),
                context.termVersions(),
                context.termPrices(),
                context.termComponents(),
                context.termBands(),
                context.methodProfiles(),
                context.legalDocuments(),
                context.sellerDisclosures(),
                context.roots());
    }

    /** 公共 TestBase 每例执行清理，只处理本组件成功插入的行。 */
    @Override
    public void clear() {
        if (fixture == null) {
            return;
        }
        try {
            assertAll(() -> assertEquals(compensationBefore, fixture.compensationEventIds(source),
                    "Checkout 只读入口不得登记或删除补偿任务"), fixture::close);
            fixture = null;
            compensationBefore = null;
        } catch (RuntimeException | AssertionError failure) {
            reusable = false;
            throw failure;
        }
    }
}
