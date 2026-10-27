package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 全平台参考数据的测试准备、只读断言与清理；业务写入只来自被测链路。 */
public interface ReferenceDataTestMapper {

    /** 当前库已人工创建的参考数据表名。 */
    @Select("""
        SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()
          AND table_name IN ('t_ref_currency', 't_ref_country', 't_ref_country_subdivision', 't_ref_industry', 't_ref_mcc')
        ORDER BY table_name
        """)
    List<String> referenceTables();

    /** t_ref_country 初始化种子 ID 2001—2249 的行数。 */
    @Select("SELECT COUNT(*) FROM t_ref_country WHERE id BETWEEN 2001 AND 2249")
    long countrySeedCount();

    /** 带完整 ISO 编码与英文名的国家行数。 */
    @Select("SELECT COUNT(*) FROM t_ref_country WHERE alpha3_code IS NOT NULL AND numeric_code IS NOT NULL AND name IS NOT NULL")
    long completeCountryCount();

    /** 各参考数据表的初始化种子行数：行政区 3001 起、行业 4001 起、MCC 5001 起。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_ref_country_subdivision WHERE id BETWEEN 3001 AND 3999) AS subdivisions,
               (SELECT COUNT(*) FROM t_ref_industry WHERE id BETWEEN 4001 AND 4999) AS industries,
               (SELECT COUNT(*) FROM t_ref_mcc WHERE id BETWEEN 5001 AND 5999) AS mccs
        """)
    Map<String, Object> seedCounts();

    /** t_ref_currency 行数。 */
    @Select("SELECT COUNT(*) FROM t_ref_currency")
    long currencyCount();

    /** 初始化种子币种（固定 ID）的行数。 */
    @Select("""
        <script>SELECT COUNT(*) FROM t_ref_currency WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    long currencySeedCount(@Param("ids") List<Long> ids);

    /** 读取指定币种的行；不存在为 null。 */
    @Select("""
        SELECT id, currency_code, numeric_code, name, symbol, minor_unit, rounding_mode, logo_file_address, status,
               version, create_time, update_time
        FROM t_ref_currency WHERE currency_code = #{currencyCode}
        """)
    Map<String, Object> currency(@Param("currencyCode") String currencyCode);

    /** 插入一条币种。 */
    @Insert("""
        INSERT INTO t_ref_currency(id, currency_code, numeric_code, name, symbol, minor_unit, rounding_mode,
            logo_file_address, status, version, create_time, update_time)
        VALUES(#{id}, #{currencyCode}, #{numericCode}, #{name}, #{symbol}, #{minorUnit}, #{roundingMode},
            #{logoFileAddress}, #{status}, #{version}, #{now}, #{now})
        """)
    int insertCurrency(@Param("id") long id, @Param("currencyCode") String currencyCode,
        @Param("numericCode") String numericCode, @Param("name") String name, @Param("symbol") String symbol,
        @Param("minorUnit") int minorUnit, @Param("roundingMode") String roundingMode,
        @Param("logoFileAddress") String logoFileAddress, @Param("status") String status, @Param("version") int version,
        @Param("now") Instant now);

    /** 删除初始化种子以外的全部币种，包括被测接口新建的行。 */
    @Delete("""
        <script>DELETE FROM t_ref_currency WHERE id NOT IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    int deleteCurrenciesExcept(@Param("ids") List<Long> ids);

    /** 设置国家国旗地址，不改版本（只用于测试换取公开地址）。 */
    @Update("UPDATE t_ref_country SET logo_file_address = #{address} WHERE country_code = #{code}")
    int countryLogo(@Param("code") String code, @Param("address") String address);

    /** 设置行业图标地址，不改版本（只用于测试换取公开地址）。 */
    @Update("UPDATE t_ref_industry SET logo_file_address = #{address} WHERE industry_code = #{code}")
    int industryLogo(@Param("code") String code, @Param("address") String address);

    /** 设置行政区状态，不改版本（只用于测试状态筛选）。 */
    @Update("UPDATE t_ref_country_subdivision SET status = #{status} WHERE subdivision_code = #{code}")
    int subdivisionStatus(@Param("code") String code, @Param("status") String status);

    /** 设置 MCC 状态，不改版本（只用于测试状态筛选）。 */
    @Update("UPDATE t_ref_mcc SET status = #{status} WHERE mcc_code = #{code}")
    int mccStatus(@Param("code") String code, @Param("status") String status);

    /** 设置国家状态，不改版本（只用于测试状态筛选）。 */
    @Update("UPDATE t_ref_country SET status = #{status} WHERE country_code = #{code}")
    int countryStatus(@Param("code") String code, @Param("status") String status);

    /** 种子行中仍带测试 logo 或非 ACTIVE 状态的行数；回收后应为 0。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_ref_country WHERE logo_file_address IS NOT NULL OR status <> 'ACTIVE')
             + (SELECT COUNT(*) FROM t_ref_industry WHERE logo_file_address IS NOT NULL OR status <> 'ACTIVE')
             + (SELECT COUNT(*) FROM t_ref_country_subdivision WHERE status <> 'ACTIVE')
             + (SELECT COUNT(*) FROM t_ref_mcc WHERE status <> 'ACTIVE')
        """)
    long modifiedSeedCount();
}
