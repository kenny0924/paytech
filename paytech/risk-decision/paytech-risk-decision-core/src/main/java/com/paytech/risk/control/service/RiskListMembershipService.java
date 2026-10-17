package com.paytech.risk.control.service;

import java.time.Instant;
import java.util.Optional;

import com.paytech.risk.control.domain.list.RiskListView;
import com.paytech.risk.control.domain.list.RiskSubjectType;

/** 控制模块对外发布的名单查询能力；决策模块据此创建名单命中变量并在评估时判断命中。 */
public interface RiskListMembershipService {
    /** 按 ID 读取名单定义。 */
    Optional<RiskListView> findList(long listId);

    /** 按编码读取名单定义。 */
    Optional<RiskListView> findListByCode(String listCode);

    /**
     * 判断主体在指定时刻是否命中名单
     *
     * @param listId 名单 ID
     * @param subjectType 主体类型，必须与名单支持类型一致，否则视为未命中
     * @param subjectKey 已规范化的主体键
     * @param at 评估时刻；回放按原评估时间判断
     * @return 名单启用且主体处于某成员的生效区间时为 true
     */
    boolean isMember(long listId, RiskSubjectType subjectType, String subjectKey, Instant at);
}
