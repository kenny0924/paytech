package com.paytech.platform.support.referencedata.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;
import com.paytech.platform.support.referencedata.domain.CurrencyAttributes;

/** t_ref_currency 查询与维护。 */
public interface ReferenceCurrencyMapper {

    /**
     * 按可选状态与币种代码查询。
     *
     * @param status 状态；为空不限制
     * @param codes 币种代码；为空不限制
     * @return 按币种代码升序
     */
    List<ReferenceCurrency> selectByFilter(@Param("status") ReferenceDataStatus status,
        @Param("codes") List<String> codes);

    /**
     * 按币种代码查询。
     *
     * @param currencyCode ISO 4217 代码
     * @return 币种；不存在为 null
     */
    ReferenceCurrency selectByCurrencyCode(@Param("currencyCode") String currencyCode);

    /**
     * 按币种代码读取并排他锁定当前行，须在调用方事务内执行。
     *
     * @param currencyCode ISO 4217 代码
     * @return 锁定后的当前行；不存在为 null
     */
    ReferenceCurrency lockByCurrencyCode(@Param("currencyCode") String currencyCode);

    /**
     * 插入新币种。
     *
     * @param row 已准备主键、版本与时间的新行
     * @return 影响行数
     */
    int insert(@Param("row") ReferenceCurrency row);

    /**
     * 按原版本与原状态修改可维护属性。
     *
     * @param row 已锁定的当前行，提供主键、原状态与原版本
     * @param attributes 目标属性
     * @param updateTime 本次 UTC 微秒时间
     * @return 影响行数；版本或状态已变化为 0
     */
    int updateAttributes(@Param("row") ReferenceCurrency row, @Param("attributes") CurrencyAttributes attributes,
        @Param("updateTime") Instant updateTime);

    /**
     * 按原版本与原状态切换状态。
     *
     * @param row 已锁定的当前行，提供主键、原状态与原版本
     * @param status 目标状态
     * @param updateTime 本次 UTC 微秒时间
     * @return 影响行数；版本或状态已变化为 0
     */
    int updateStatus(@Param("row") ReferenceCurrency row, @Param("status") CurrencySpecStatus status,
        @Param("updateTime") Instant updateTime);
}
