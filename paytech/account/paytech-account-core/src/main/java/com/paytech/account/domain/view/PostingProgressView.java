package com.paytech.account.domain.view;

import java.util.List;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 主体与币种下的事实应用进度与涉及的全部入账流（事实、流两表组合）。 */
@Getter
@AllArgsConstructor
public class PostingProgressView {
    /** 查询的主体类型。 */
    private final AccountEntityType entityType;

    /** 查询的主体标识。 */
    private final String entityId;

    /** 查询的币种。 */
    private final String currency;

    /** 自身事件已到达的事实数。 */
    private final long appliedFactCount;

    /** 其中最大的 factId；没有时为空。 */
    private final Long maxAppliedFactId;

    /** 该账簿涉及的全部入账流，按主体类型、主体标识、分片号排序。 */
    private final List<AccountPostingStream> streams;
}
