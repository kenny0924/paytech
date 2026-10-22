package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 全平台参考数据的测试准备、只读断言与清理；业务写入只来自被测链路。 */
public interface ReferenceDataTestMapper {

    /** 当前库是否已人工创建 t_currency_spec。 */
    @Select("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_currency_spec'")
    int currencySpecTableCount();

    /** 当前库的 t_currency_spec 是否已人工增加 version 列。 */
    @Select("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 't_currency_spec' AND column_name = 'version'
        """)
    int currencySpecVersionColumnCount();

    /** 当前库是否已人工创建 t_country_region。 */
    @Select("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_country_region'")
    int countryRegionTableCount();

    /** t_country_region 初始化种子 ID 2001—2249 的行数。 */
    @Select("SELECT COUNT(*) FROM t_country_region WHERE id BETWEEN 2001 AND 2249")
    long countryRegionSeedCount();

    /** t_currency_spec 行数。 */
    @Select("SELECT COUNT(*) FROM t_currency_spec")
    long currencySpecCount();

    /** 指定币种的行数。 */
    @Select("SELECT COUNT(*) FROM t_currency_spec WHERE currency = #{currency}")
    long currencySpecCountOf(@Param("currency") String currency);

    /** 读取指定币种的规则行；不存在为 null。 */
    @Select("""
        SELECT id, currency, minor_unit, rounding_mode, status, version, create_time, update_time
        FROM t_currency_spec WHERE currency = #{currency}
        """)
    Map<String, Object> currencySpec(@Param("currency") String currency);

    /** 插入一条币种规则。 */
    @Insert("""
        INSERT INTO t_currency_spec(id, currency, minor_unit, rounding_mode, status, version, create_time, update_time)
        VALUES(#{id}, #{currency}, #{minorUnit}, #{roundingMode}, #{status}, #{version}, #{now}, #{now})
        """)
    int insertCurrencySpec(@Param("id") long id, @Param("currency") String currency, @Param("minorUnit") int minorUnit,
        @Param("roundingMode") String roundingMode, @Param("status") String status, @Param("version") int version,
        @Param("now") Instant now);

    /** 删除常驻种子以外的全部规则，包括被测接口新建的行。 */
    @Delete("DELETE FROM t_currency_spec WHERE currency <> #{seedCurrency}")
    int deleteCurrencySpecsExcept(@Param("seedCurrency") String seedCurrency);
}
