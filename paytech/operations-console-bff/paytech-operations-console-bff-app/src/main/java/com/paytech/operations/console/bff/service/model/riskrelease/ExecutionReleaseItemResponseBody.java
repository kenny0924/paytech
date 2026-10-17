package com.paytech.operations.console.bff.service.model.riskrelease;

import lombok.Getter;
import lombok.Setter;

/**
 * 执行发布列表项；静态检查与影子汇总见发布详情。
 */
@Getter
@Setter
public class ExecutionReleaseItemResponseBody {
    /**
     * 发布 ID
     */
    private String releaseId;

    /**
     * 发布编号
     */
    private String releaseNo;

    /**
     * 决策点编码
     */
    private String pointCode;

    /**
     * 环境
     */
    private String environment;

    /**
     * 状态
     * <li>DRAFT、VALIDATING、SHADOWING、REJECTED、ACTIVE、SUPERSEDED；未知值原样返回。</li>
     */
    private String status;

    /**
     * 是否当前生效
     */
    private boolean active;

    /**
     * 决策流 ID
     */
    private String flowId;

    /**
     * 决策流编码
     */
    private String flowCode;

    /**
     * 冻结的决策流版本 ID
     * <li>回滚时以它新建执行发布。</li>
     */
    private String flowVersionId;

    /**
     * 决策流版本号
     */
    private int flowVersionNo;

    /**
     * 发布内容摘要
     */
    private String contentHash;

    /**
     * 创建人账号 ID
     */
    private String createdBy;

    /**
     * 创建时间，UTC ISO-8601
     */
    private String createTime;

    /**
     * 激活人账号 ID
     * <li>未激活为 null。</li>
     */
    private String activatedBy;

    /**
     * 激活时间
     * <li>未激活为 null。</li>
     */
    private String activatedTime;

    /**
     * 被替代时间
     * <li>未替代为 null。</li>
     */
    private String supersededTime;
}
