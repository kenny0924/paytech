package com.paytech.commercial.domainservice.service;

import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;

/**
 * 商业条款方案与版本的本地保存（technique.md §2.2）：每个方法一个本地事务（已有事务时加入），写结构化业务行、方案 BCR 根 CAS
 * 与审计快照
 * <li>接收已在锁内校验的 Context，不重复查询归属与关系；状态与版本由条件 SQL 与行数检查保护，任一失败整体回滚。</li>
 * <li>不访问外部服务、Redis 或 MQ。</li>
 */
public interface CommercialTermService {
    /**
     * 创建方案：插入方案、BCR 根（版本 1）与 CREATE 快照。
     *
     * @param context 含新方案与待插入根
     */
    void createPlan(CommercialTermVersionContext context);

    /**
     * 创建 DRAFT 版本：根 CAS，插入版本、BCR Child 与 CREATE 快照，以及复制来的价格、组件与档位。
     *
     * @param context 含新版本、待插入 Child 与复制行
     */
    void createVersion(CommercialTermVersionContext context);

    /**
     * 整体替换 DRAFT 价格集合：根 CAS，按档位 → 组件 → 价格删除旧行，再按价格 → 组件 → 档位插入新行，版本 Child 记 UPDATE 快照。
     *
     * @param context 含版本、旧行 ID 与新行
     */
    void replacePrices(CommercialTermVersionContext context);

    /**
     * 提交：DRAFT → PENDING_APPROVAL，快照记 Maker。
     *
     * @param context 含版本与操作人
     */
    void submit(CommercialTermVersionContext context);

    /**
     * 批准：PENDING_APPROVAL → APPROVED，快照记 Maker 与 Checker。
     *
     * @param context 含版本、Maker 与操作人
     */
    void approve(CommercialTermVersionContext context);

    /**
     * 驳回：PENDING_APPROVAL → DRAFT，快照记 Maker 与 Checker。
     *
     * @param context 含版本、Maker 与操作人
     */
    void reject(CommercialTermVersionContext context);

    /**
     * 排期：前一 ACTIVE 版本的生效终点写成新起点（状态仍 ACTIVE），本版本写入生效起点，APPROVED → SCHEDULED。
     *
     * @param context 含版本与生效起点
     */
    void schedule(CommercialTermVersionContext context);

    /**
     * 激活：先让前一 ACTIVE 释放槽位成为 SUPERSEDED 并写生效终点，再让本版本 ACTIVE；方案首次生效时 DRAFT → ACTIVE
     * <li>加入调用方已开启的事务（激活 Processor 在同一事务内登记 CommercialTermVersionActivated 发布任务）。</li>
     *
     * @param context 含版本、被替代版本与各自 Child
     */
    void activate(CommercialTermVersionContext context);

    /**
     * 版本详情。
     *
     * @param termVersionId 版本 ID
     * @return 方案、版本与方案根当前版本
     * @throws com.paytech.foundation.common.error.FailException 版本不存在 NOT_FOUND
     */
    CommercialTermVersionView getVersion(long termVersionId);
}
