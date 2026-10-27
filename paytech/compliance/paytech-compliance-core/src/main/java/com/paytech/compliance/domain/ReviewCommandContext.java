package com.paytech.compliance.domain;

import java.time.Instant;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次运营审核命令的公共事实：进行中的主案件、操作人、代操作方与时间
 * <li>startReview 非空表示主案件仍为 SUBMITTED，本命令同事务把它推进为 IN_REVIEW 并追加该状态变更；否则只递增主案件版本，
 * 使同一案件的审核命令串行化。</li>
 */
@Getter
@AllArgsConstructor
public class ReviewCommandContext {
    /** 主案件，读取时版本。 */
    private final DueDiligenceCase mainCase;
    /** 操作人 Operations 账号 ID。 */
    private final long actorId;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;
    /** SUBMITTED → IN_REVIEW 的状态变更；已在审核中为空。 */
    private final CaseStateChange startReview;
    /** 命令时间。 */
    private final Instant now;
}
