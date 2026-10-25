package com.paytech.commercial.test.mocker.local.checkout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.domain.CommercialCheckoutFacts;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.foundation.testsupport.api.Clear;

/** 仅管理本例成功插入的商务合成数据（fixtures/commercial/checkout-query.json），保留其他数据。 */
@TestComponent
public class CheckoutResolutionFixture implements Clear {
    /** 合成夹具的商务查询范围：与 client-master DTO 样本的 commercialScope 一致。 */
    public static final CommercialCheckoutScope SCOPE =
        new CommercialCheckoutScope(CheckoutFixture.CLIENT_ID, List.of("AE991", "AE992"),
            List.of(CheckoutFixture.FIRST_PARTNER_ID, CheckoutFixture.SECOND_PARTNER_ID));

    private final DataSource source;
    private final DataSourceProperties properties;
    private final CheckoutQueryService service;
    private CheckoutFixture fixture;
    private List<Long> compensationBefore;
    private boolean reusable = true;

    /** 校验实际测试依赖，避免清理阶段才发现空数据库或查询组件。 */
    public CheckoutResolutionFixture(DataSource source, DataSourceProperties properties, CheckoutQueryService service) {
        this.source = java.util.Objects.requireNonNull(source);
        this.properties = java.util.Objects.requireNonNull(properties);
        this.service = java.util.Objects.requireNonNull(service);
    }

    /** 检查测试连接和上例清理结果，不在准备阶段删除任何数据。 */
    public void assertEmpty() {
        assertTrue(reusable, "上例清理失败，不复用污染环境");
        assertNull(fixture, "前例 Fixture 尚未清理");
        assertTrue(properties.getUrl().matches("jdbc:mysql://127\\.0\\.0\\.1:13306/mor_commercial(_[a-z0-9]+)?\\?.*"),
            "只允许本机 mor_commercial 或其隔离库: " + properties.getUrl());
    }

    /** 插入商务合成场景；已有同键数据时失败，不覆盖。 */
    public void install() {
        fixture = new CheckoutFixture();
        compensationBefore = fixture.compensationEventIds(source);
        fixture.install(source);
    }

    /** 验证操作前后本库全部商务事实和补偿身份不变，即使业务断言失败仍执行检查。 */
    public void assertReadOnly(Runnable operation) {
        var before = readFacts();
        var events = fixture.compensationEventIds(source);
        assertAll(operation::run, () -> assertEquals(before, readFacts()),
            () -> assertEquals(events, fixture.compensationEventIds(source)));
    }

    /** 通过正式只读 Service 观察本库所有原始事实，检查不依赖候选筛选或响应投影。 */
    public CommercialCheckoutFacts readFacts() {
        return service.load(SCOPE);
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
