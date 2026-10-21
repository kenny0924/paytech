package com.paytech.reconciliation.domainservice.service;

import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.domain.ClearingAccountSettings;

/**
 * 清算账户的本地保存（UC-R01）
 * <li>调用方（Processor）已完成存在性、状态、到账银行账户与 MID 核对；这里只按版本条件写入，冲突抛 409。</li>
 */
public interface ClearingAccountService {

    /**
     * 新建清算账户
     *
     * @param account 已准备主键、ACTIVE 状态与时间的清算账户
     */
    void create(ClearingAccount account);

    /**
     * 整体替换三项配置与到账银行账户
     *
     * @param account 已读取的清算账户
     * @param settings 新配置
     */
    void updateSettings(ClearingAccount account, ClearingAccountSettings settings);

    /**
     * 挂入 MID：插入归属行并推进清算账户版本（串行化同一账户的 MID 变更）
     *
     * @param account 已读取的清算账户
     * @param mid 新归属行；MID + 币种唯一键冲突抛 409
     */
    void addMid(ClearingAccount account, ClearingAccountMid mid);

    /**
     * 摘除 MID：删除归属行并推进清算账户版本
     *
     * @param account 已读取的清算账户
     * @param mid 已读取的归属行
     */
    void removeMid(ClearingAccount account, ClearingAccountMid mid);

    /**
     * 启用或停用
     *
     * @param account 已读取的清算账户
     * @param status 目标状态
     */
    void changeStatus(ClearingAccount account, ClearingAccountStatus status);
}
