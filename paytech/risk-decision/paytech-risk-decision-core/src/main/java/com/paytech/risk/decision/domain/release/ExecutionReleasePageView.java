package com.paytech.risk.decision.domain.release;

import java.time.Instant;
import java.util.List;

/**
 * 执行发布分页列表
 *
 * @param items 当前页，创建时间、发布 ID 倒序
 * @param page 页码
 * @param size 每页条数
 * @param total 筛选后的总数
 */
public record ExecutionReleasePageView(List<Item> items, int page, int size, long total) {

    /** 复制当前页，防止调用方修改。 */
    public ExecutionReleasePageView {
        items = List.copyOf(items);
    }

    /**
     * 列表项
     *
     * @param releaseId 发布 ID
     * @param releaseNo 发布编号
     * @param pointCode 决策点编码
     * @param environment 环境 Code
     * @param status 状态 Code
     * @param active 是否当前生效
     * @param flowId 决策流 ID
     * @param flowCode 决策流编码
     * @param flowVersionId 冻结的决策流版本 ID
     * @param flowVersionNo 决策流版本号
     * @param contentHash 内容摘要
     * @param createdBy 创建人
     * @param createTime 创建时间
     * @param activatedBy 激活人；未激活为空
     * @param activatedTime 激活时间
     * @param supersededTime 被替代时间
     */
    public record Item(long releaseId, String releaseNo, String pointCode, String environment, String status,
        boolean active, long flowId, String flowCode, long flowVersionId, int flowVersionNo, String contentHash,
        long createdBy, Instant createTime, Long activatedBy, Instant activatedTime, Instant supersededTime) { }
}
