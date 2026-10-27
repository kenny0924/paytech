package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.test.dal.ReferenceDataTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 币种前置数据：真实插入 t_ref_currency；清理时回收常驻种子以外的全部行（含被测接口新建的行）。
 * <li>USD（1001）、AED（1002）、SAR（1003）、EGP（1004）是人工初始化的常驻参考数据，不由测试插入、修改或删除。</li>
 * <li>其他用例从不触碰该表，因此表尚未人工创建时只有币种用例失败，并给出准备方法；本例未使用时清理不访问表。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class CurrencySpecMocker implements Clear {

    private static final String SETUP = "；请在 mor_platform 人工执行 paytech/platform-support/docs/database/sql/changes/"
        + "2026-10-27-reference-data.sql（或 paytech-platform-support-test/src/test/resources/init/ddl.sql 中同样的建表与种子语句）";

    /** 人工初始化的常驻 USD 种子行。 */
    public static final String SEED_CURRENCY = "USD";

    /** 人工初始化的常驻种子：币种代码到固定 ID。 */
    public static final Map<String, Long> SEEDS = Map.of("USD", 1001L, "AED", 1002L, "SAR", 1003L, "EGP", 1004L);

    private final ReferenceDataTestMapper mapper;
    private boolean touched;
    private long nextId = 990_000_001L;

    /** 表已人工创建，且只有四条常驻种子行（上一例的行已回收）。 */
    public void assertReady() {
        touched = true;
        if (!mapper.referenceTables().contains("t_ref_currency")) {
            fail("缺少表 t_ref_currency" + SETUP);
        }
        if (mapper.currencySeedCount(seedIds()) != SEEDS.size()) {
            fail("缺少 USD / AED / SAR / EGP 种子行" + SETUP);
        }
        assertEquals(SEEDS.size(), mapper.currencyCount(), "t_ref_currency 只应有四条种子行：上一例的行须已回收");
    }

    /** 插入一条版本为 1、没有 logo 的币种。 */
    public void currency(String currency, int minorUnit, CurrencySpecStatus status, Instant now) {
        currency(currency, minorUnit, status, 1, now);
    }

    /** 插入一条指定版本、没有 logo 的币种。 */
    public void currency(String currency, int minorUnit, CurrencySpecStatus status, int version, Instant now) {
        currency(currency, minorUnit, status, version, null, now);
    }

    /**
     * 插入一条币种：数字代码 999、名称为“{代码} test”、符号为代码。
     *
     * @param logoFileAddress logo 文件地址，可空
     */
    public void currency(String currency, int minorUnit, CurrencySpecStatus status, int version, String logoFileAddress,
        Instant now) {
        touched = true;
        assertEquals(1, mapper.insertCurrency(nextId++, currency, numericCode(), name(currency), currency, minorUnit,
            CurrencyRoundingMode.HALF_UP.getCode(), logoFileAddress, status.getCode(), version, now));
    }

    /** {@link #currency} 插入的数字代码。 */
    public static String numericCode() {
        return "999";
    }

    /** {@link #currency} 插入的名称。 */
    public static String name(String currency) {
        return currency + " test";
    }

    /** 回收种子以外的全部行；本例未使用该表时不访问。 */
    @Override
    public void clear() {
        if (!touched) {
            return;
        }
        mapper.deleteCurrenciesExcept(seedIds());
        touched = false;
    }

    /** 种子固定 ID。 */
    private static List<Long> seedIds() {
        return List.copyOf(SEEDS.values());
    }
}
