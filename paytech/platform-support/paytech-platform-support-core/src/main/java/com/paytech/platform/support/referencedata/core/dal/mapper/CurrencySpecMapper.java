package com.paytech.platform.support.referencedata.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;

/** t_currency_spec 查询与维护。 */
public interface CurrencySpecMapper {

    /**
     * 查询全部币种规则。
     *
     * @return 按币种代码升序
     */
    List<CurrencySpec> selectAll();

    /**
     * 按币种代码查询。
     *
     * @param currency ISO 4217 代码
     * @return 规则；不存在为 null
     */
    CurrencySpec selectByCurrency(@Param("currency") String currency);

    /**
     * 按币种代码读取并排他锁定当前行，须在调用方事务内执行。
     *
     * @param currency ISO 4217 代码
     * @return 锁定后的当前行；不存在为 null
     */
    CurrencySpec lockByCurrency(@Param("currency") String currency);

    /**
     * 插入新规则。
     *
     * @param row 已准备主键、版本与时间的新行
     * @return 影响行数
     */
    int insert(@Param("row") CurrencySpec row);

    /**
     * 按原版本修改小数位与舍入方式，只允许 DRAFT 行。
     *
     * @param row 已锁定的当前行，提供主键与原版本
     * @param minorUnit 目标小数位
     * @param roundingMode 目标舍入方式
     * @param updateTime 本次 UTC 微秒时间
     * @return 影响行数；版本或状态已变化为 0
     */
    int updateRule(@Param("row") CurrencySpec row, @Param("minorUnit") int minorUnit,
        @Param("roundingMode") CurrencyRoundingMode roundingMode, @Param("updateTime") Instant updateTime);

    /**
     * 按原版本与原状态切换状态。
     *
     * @param row 已锁定的当前行，提供主键、原状态与原版本
     * @param status 目标状态
     * @param updateTime 本次 UTC 微秒时间
     * @return 影响行数；版本或状态已变化为 0
     */
    int updateStatus(@Param("row") CurrencySpec row, @Param("status") CurrencySpecStatus status,
        @Param("updateTime") Instant updateTime);
}
