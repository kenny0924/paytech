package com.paytech.risk.control.service;

import java.util.List;

import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.domain.list.AddRiskListEntryContext;
import com.paytech.risk.control.domain.list.DeactivateRiskListEntryContext;
import com.paytech.risk.control.domain.list.RiskListEntryView;
import com.paytech.risk.control.domain.list.RiskListView;

/** 名单定义与成员的本地保存及管理查询。 */
public interface RiskListService {
    /** 保存新名单。 */
    void createList(RiskList list);

    /** 在一个本地事务中结束过期占位成员并插入新成员。 */
    void addEntry(AddRiskListEntryContext context);

    /** 结束成员生效区间。 */
    void deactivateEntry(DeactivateRiskListEntryContext context);

    /** 按编码稳定排序分页读取名单及总数。 */
    Page<RiskListView> findLists(int limit, long offset);

    /**
     * 分页读取名单成员
     *
     * @param listCode 名单编码；不存在时抛出业务错误
     * @param subjectValue 可选主体原值，按名单主体类型规范化后精确匹配
     * @param status 可选状态
     * @param limit 每页条数
     * @param offset 偏移
     * @return 新成员在前的成员及总数，主体键脱敏
     */
    Page<RiskListEntryView> findEntries(String listCode, String subjectValue, RiskListEntryStatus status, int limit,
        long offset);

    /**
     * 一页查询结果
     *
     * @param items 本页条目
     * @param total 同一条件下的总数
     * @param <T> 条目类型
     */
    record Page<T>(List<T> items, long total) { }
}
