package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 匹配方式（t_bank_match.match_method、t_channel_statement_line.match_method）
 * <li>银行层 AUTO 只表示系统建议，确认均为人工。</li>
 */
public enum MatchMethod implements CodeEnum {
    /** 系统匹配或建议 */
    AUTO,

    /** 人工匹配或经 RE_MATCH 处置建立 */
    MANUAL;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
