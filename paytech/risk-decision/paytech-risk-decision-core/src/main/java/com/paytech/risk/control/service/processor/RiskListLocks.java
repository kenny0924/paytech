package com.paytech.risk.control.service.processor;

import java.time.Duration;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.enums.RiskListStatus;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;

/** 名单成员维护共用的资源锁与名单资格检查。 */
final class RiskListLocks {
    /** 名单维护锁等待时长。 */
    static final Duration WAIT_TIME = Duration.ofMillis(500);

    /** 名单维护锁租约。 */
    static final Duration LEASE_TIME = Duration.ofSeconds(30);

    /** 纯工具。 */
    private RiskListLocks() { }

    /** 同一名单的成员维护共用一把锁；键只含名单编码，不含主体值。 */
    static String list(String listCode) {
        return "risk:list:" + listCode;
    }

    /** 名单存在且启用才允许添加成员。 */
    static RiskList requireActiveList(RiskListRepository lists, String listCode) {
        RiskList list = lists.findByCode(listCode)
            .orElseThrow(() -> new BizCheckFailException("名单不存在: " + listCode));
        if (list.getStatus() != RiskListStatus.ACTIVE) {
            throw new BizCheckFailException("名单已停用: " + listCode);
        }
        return list;
    }
}
