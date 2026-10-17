package com.paytech.platform.support.test.dal;

import java.time.Instant;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 全平台参考数据的测试准备与清理；读取只来自被测查询链路。 */
public interface ReferenceDataTestMapper {

    /** 当前库是否已人工创建 t_currency_spec。 */
    @Select("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_currency_spec'")
    int currencySpecTableCount();

    /** t_currency_spec 行数。 */
    @Select("SELECT COUNT(*) FROM t_currency_spec")
    long currencySpecCount();

    /** 指定币种的行数。 */
    @Select("SELECT COUNT(*) FROM t_currency_spec WHERE currency = #{currency}")
    long currencySpecCountOf(@Param("currency") String currency);

    /** 插入一条币种规则。 */
    @Insert("""
        INSERT INTO t_currency_spec(id, currency, minor_unit, rounding_mode, status, create_time, update_time)
        VALUES(#{id}, #{currency}, #{minorUnit}, #{roundingMode}, #{status}, #{now}, #{now})
        """)
    int insertCurrencySpec(@Param("id") long id, @Param("currency") String currency, @Param("minorUnit") int minorUnit,
        @Param("roundingMode") String roundingMode, @Param("status") String status, @Param("now") Instant now);

    /** 删除本例插入的规则。 */
    @Delete("DELETE FROM t_currency_spec WHERE id = #{id}")
    int deleteCurrencySpec(@Param("id") long id);
}
