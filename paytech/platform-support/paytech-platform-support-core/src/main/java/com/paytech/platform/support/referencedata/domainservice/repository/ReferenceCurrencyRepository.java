package com.paytech.platform.support.referencedata.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;
import com.paytech.platform.support.referencedata.domain.CurrencyAttributes;

/** 币种单表仓储。 */
public interface ReferenceCurrencyRepository {

    /** 按可选状态与币种代码查询，按币种代码升序；条件为空不限制。 */
    List<ReferenceCurrency> find(ReferenceDataStatus status, List<String> codes);

    /** 按币种代码查询；不存在返回 null。 */
    ReferenceCurrency findByCurrencyCode(String currencyCode);

    /** 在调用方事务内排他锁定并读取当前行；不存在返回 null。 */
    ReferenceCurrency lockByCurrencyCode(String currencyCode);

    /**
     * 插入新币种
     *
     * @param currency 已准备主键、DRAFT 状态、版本 1 与时间的新行
     *         <li>币种代码唯一键冲突返回“已存在”业务拒绝，不当作成功。</li>
     */
    void insert(ReferenceCurrency currency);

    /**
     * 按原版本与原状态修改可维护属性，成功后同步传入对象
     *
     * @param currency 已锁定的当前行
     * @param attributes 目标属性
     * @param updateTime 本次 UTC 微秒时间
     *         <li>版本或状态已变化时返回并发冲突，对象不变。</li>
     */
    void updateAttributes(ReferenceCurrency currency, CurrencyAttributes attributes, Instant updateTime);

    /**
     * 按原版本与原状态切换状态，成功后同步传入对象
     *
     * @param currency 已锁定的当前行
     * @param status 目标状态
     * @param updateTime 本次 UTC 微秒时间
     *         <li>版本或状态已变化时返回并发冲突，对象不变。</li>
     */
    void changeStatus(ReferenceCurrency currency, CurrencySpecStatus status, Instant updateTime);
}
