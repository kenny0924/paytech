package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.test.dal.ReferenceDataTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 币种规则前置数据：真实插入 t_currency_spec；清理时回收常驻 USD 种子以外的全部行（含被测接口新建的行）。
 * <li>USD（id 1001）是人工初始化的常驻参考数据，不由测试插入、修改或删除。</li>
 * <li>其他用例从不触碰该表，因此表尚未人工创建时只有币种用例失败，并给出准备方法；本例未使用时清理不访问表。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class CurrencySpecMocker implements Clear {

    private static final String SETUP = "；请在 mor_platform 人工执行 paytech/platform-support/docs/database/sql/platform-support-schema.sql 中的 "
        + "CREATE TABLE t_currency_spec 与 USD 种子（或 paytech-platform-support-test/src/test/resources/init/ddl.sql 中同样两条语句）";

    private static final String VERSION_SETUP = "；请在 mor_platform 人工执行 "
        + "paytech/platform-support/docs/database/sql/changes/2026-10-20-currency-spec-version.sql";

    /** 人工初始化的常驻 USD 种子行。 */
    public static final String SEED_CURRENCY = "USD";

    private final ReferenceDataTestMapper mapper;
    private boolean touched;
    private long nextId = 990_000_001L;

    /** 表与 version 列已人工创建，且只有常驻 USD 种子行（上一例的行已回收）。 */
    public void assertReady() {
        touched = true;
        if (mapper.currencySpecTableCount() != 1) {
            fail("缺少表 t_currency_spec" + SETUP);
        }
        if (mapper.currencySpecVersionColumnCount() != 1) {
            fail("t_currency_spec 缺少 version 列" + VERSION_SETUP);
        }
        if (mapper.currencySpecCountOf(SEED_CURRENCY) != 1) {
            fail("缺少 USD 种子行" + SETUP);
        }
        assertEquals(1, mapper.currencySpecCount(), "t_currency_spec 只应有 USD 种子行：上一例的行须已回收");
    }

    /** 插入一条版本为 1 的规则。 */
    public void currency(String currency, int minorUnit, CurrencySpecStatus status, Instant now) {
        currency(currency, minorUnit, status, 1, now);
    }

    /** 插入一条指定版本的规则。 */
    public void currency(String currency, int minorUnit, CurrencySpecStatus status, int version, Instant now) {
        touched = true;
        assertEquals(1, mapper.insertCurrencySpec(nextId++, currency, minorUnit,
            CurrencyRoundingMode.HALF_UP.getCode(), status.getCode(), version, now));
    }

    /** 回收种子以外的全部行；本例未使用该表时不访问。 */
    @Override
    public void clear() {
        if (!touched) {
            return;
        }
        mapper.deleteCurrencySpecsExcept(SEED_CURRENCY);
        touched = false;
    }
}
