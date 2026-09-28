package com.paytech.client.contract.core.dal.dataobject;
import java.time.Instant;
/** 已成功本地命令的稳定回执索引。 */
public record ClientDraftCommandRecord(
    /** 原命令请求摘要。 */ String requestHash,
    /** 操作及原申请短引用。 */ String responseRef,
    /** CCT 命令生命周期目录值。 */ String status,
    /** 首次成功时间，重放不更新。 */ Instant completedAt) {}
