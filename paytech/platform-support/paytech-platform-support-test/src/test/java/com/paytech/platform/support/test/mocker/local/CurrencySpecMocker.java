package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.test.dal.ReferenceDataTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 币种规则前置数据：真实插入 t_currency_spec，只回收本例插入的行。
 * <li>USD（id 1001）是人工初始化的常驻参考数据，不由测试插入或删除。</li>
 * <li>其他用例从不触碰该表，因此表尚未人工创建时只有币种查询用例失败，并给出准备方法。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class CurrencySpecMocker implements Clear {

    private static final String SETUP = "；请在 mor_platform 人工执行 paytech/platform-support/docs/database/sql/platform-support-schema.sql 中的 "
        + "CREATE TABLE t_currency_spec 与 USD 种子（或 paytech-platform-support-test/src/test/resources/init/ddl.sql 中同样两条语句）";

    /** 人工初始化的常驻 USD 种子行。 */
    public static final String SEED_CURRENCY = "USD";

    private final ReferenceDataTestMapper mapper;
    private final List<Long> ids = new ArrayList<>();
    private long nextId = 990_000_001L;

    /** 表已人工创建，且只有常驻 USD 种子行（上一例插入的行已回收）。 */
    public void assertReady() {
        if (mapper.currencySpecTableCount() != 1) {
            fail("缺少表 t_currency_spec" + SETUP);
        }
        if (mapper.currencySpecCountOf(SEED_CURRENCY) != 1) {
            fail("缺少 USD 种子行" + SETUP);
        }
        assertEquals(1, mapper.currencySpecCount(), "t_currency_spec 只应有 USD 种子行：用例插入的行须已回收");
    }

    /** 插入一条规则。 */
    public void currency(String currency, int minorUnit, CurrencySpecStatus status, Instant now) {
        long id = nextId++;
        assertEquals(1, mapper.insertCurrencySpec(id, currency, minorUnit, CurrencyRoundingMode.HALF_UP.getCode(),
            status.getCode(), now));
        ids.add(id);
    }

    /** 回收本例插入的行；没有插入时不访问表。 */
    @Override
    public void clear() {
        for (Long id : ids) {
            mapper.deleteCurrencySpec(id);
        }
        ids.clear();
    }
}
