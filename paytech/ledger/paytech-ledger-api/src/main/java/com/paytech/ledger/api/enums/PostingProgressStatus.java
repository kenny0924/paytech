package com.paytech.ledger.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 账簿入账进度一段或一条过账流的判定结果（{@code GET /posting-progress/v1/ledger-books/{ledgerBookId}}）
 * <li>判定与期间关账步骤 CHECK_ACCOUNT_POSTING_PROGRESS 共用同一段逻辑。</li>
 */
public enum PostingProgressStatus implements CodeEnum {
    /** 已一致：与关账判定"通过"相同 */
    CONSISTENT,

    /** 落后：与关账判定"未通过"相同 */
    BEHIND,

    /** 依赖不可用：billing 或 account 进度读取失败，段内数字为空 */
    UNAVAILABLE;

    /** 接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
