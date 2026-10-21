package com.paytech.platform.support.referencedata.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;

/** 币种规则单表仓储。 */
public interface CurrencySpecRepository {

    /** 全部币种规则，按币种代码升序。 */
    List<CurrencySpec> findAll();

    /** 按币种代码查询；不存在返回 null。 */
    CurrencySpec findByCurrency(String currency);

    /** 在调用方事务内排他锁定并读取当前行；不存在返回 null。 */
    CurrencySpec lockByCurrency(String currency);

    /**
     * 插入新规则
     *
     * @param spec 已准备主键、DRAFT 状态、版本 1 与时间的新行
     *         <li>币种代码唯一键冲突返回“已存在”业务拒绝，不当作成功。</li>
     */
    void insert(CurrencySpec spec);

    /**
     * 按原版本修改 DRAFT 规则的小数位与舍入方式，成功后同步传入对象
     *
     * @param spec 已锁定的当前行
     * @param minorUnit 目标小数位
     * @param roundingMode 目标舍入方式
     * @param updateTime 本次 UTC 微秒时间
     *         <li>版本或状态已变化时返回并发冲突，对象不变。</li>
     */
    void updateRule(CurrencySpec spec, int minorUnit, CurrencyRoundingMode roundingMode, Instant updateTime);

    /**
     * 按原版本与原状态切换状态，成功后同步传入对象
     *
     * @param spec 已锁定的当前行
     * @param status 目标状态
     * @param updateTime 本次 UTC 微秒时间
     *         <li>版本或状态已变化时返回并发冲突，对象不变。</li>
     */
    void changeStatus(CurrencySpec spec, CurrencySpecStatus status, Instant updateTime);
}
