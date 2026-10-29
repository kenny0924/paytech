package com.paytech.risk.control.domain.underwriting;

import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次承保状态推进的已验证上下文：提交待复核、提交即决定、复核决定或自动承保
 * <li>superseded 为批准时需同事务置为 SUPERSEDED 的上一有效版本；没有时为空。</li>
 */
@Getter
@AllArgsConstructor
public class UnderwritingDecisionContext {
    /** 目标版本行：已有的 DRAFT / SUBMITTED，或自动承保待插入的 APPROVED 新行。 */
    private final ClientUnderwriting underwriting;

    /** 同时冻结的内容；复核与自动承保（内容已在新行内）为空。 */
    private final UnderwritingContent content;

    /** 决定事实；仅提交待复核时为空。 */
    private final UnderwritingVerdict verdict;

    /** 批准时被替代的上一有效版本；没有时为空。 */
    private final ClientUnderwriting superseded;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次处理时间。 */
    private final Instant now;
}
