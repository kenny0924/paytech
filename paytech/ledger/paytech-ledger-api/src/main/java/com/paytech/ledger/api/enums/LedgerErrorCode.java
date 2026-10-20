package com.paytech.ledger.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 调用方需要按错误码区分处理的 ledger 业务错误（HTTP 400）；其余业务拒绝使用公共 BUSINESS_CHECK_FAILED
 * <li>消费者：Operations BFF 在 HttpServiceClientFactory 显式登记这些 Code 并原样透传，Operations Web 按 Code 显示默认消息或落到字段
 * （Foundation 出站客户端不透传 BUSINESS_CHECK_FAILED 的 detail）。</li>
 * <li>按 Code 抛出时 Problem detail 为默认消息，不含单据主键等上下文。</li>
 */
public enum LedgerErrorCode implements CodeMessageEnum {
    /**
     * 人工调整涉及 Owner 控制科目（CLIENT_PAYABLE、CLIENT_SECURITY_DEPOSIT、CLIENT_RECOVERY_RECEIVABLE、PARTNER_PAYABLE、
     * PARTNER_RECOVERY_RECEIVABLE）：影响 Owner 余额的调整必须经 billing 登记 MANUAL_ADJUSTMENT 计费事实，由 account 明细账生成控制科目行
     */
    OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN("OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN",
        "人工调整不得涉及 Client 或 Local Partner 的控制科目，请经计费登记 MANUAL_ADJUSTMENT 计费事实"),

    /** 复核人与编制人相同：人工调整、期间关账 / 重开、规则版本的批准与驳回 */
    SELF_REVIEW_FORBIDDEN("SELF_REVIEW_FORBIDDEN", "制单人不能复核自己提交的单据"),

    /** 复核确认的内容摘要（人工调整 contentHash、关账 closeHash）与当前单据不一致 */
    REVIEW_CONTENT_CHANGED("REVIEW_CONTENT_CHANGED", "单据内容已变化，请刷新后重新复核"),

    /** 人工调整不是 SUBMITTED（且不是同一复核人同一决定的重放）、关账 / 重开流程不是 PENDING_CHECK */
    REVIEW_STATUS_INVALID("REVIEW_STATUS_INVALID", "单据不在待复核状态"),

    /** 人工调整借贷合计不相等或为零 */
    ADJUSTMENT_UNBALANCED("ADJUSTMENT_UNBALANCED", "分录借贷不平衡"),

    /** 人工调整的会计期间不存在或不是 OPEN */
    ADJUSTMENT_PERIOD_NOT_OPEN("ADJUSTMENT_PERIOD_NOT_OPEN", "会计期间未开放"),

    /** 人工调整明细账户不存在、不属于调整账簿或不是 ACTIVE */
    ADJUSTMENT_ACCOUNT_INVALID("ADJUSTMENT_ACCOUNT_INVALID", "账户不属于该账簿、已停用或不可记账"),

    /** 发起关账时同日历更早的期间尚未全部 CLOSED */
    PERIOD_CLOSE_ORDER_INVALID("PERIOD_CLOSE_ORDER_INVALID", "之前的期间尚未关账"),

    /** 发起关账时期间不是 OPEN */
    PERIOD_NOT_OPEN("PERIOD_NOT_OPEN", "期间不是开放状态，不能关账"),

    /** 发起重开时期间不是 CLOSED，或同日历有更晚的已关闭、关账中或重开中期间 */
    PERIOD_REOPEN_NOT_ALLOWED("PERIOD_REOPEN_NOT_ALLOWED", "只能重开最近一期已关账期间"),

    /** 新建规则版本时规则包已有 DRAFT 或 PENDING_APPROVAL 版本 */
    RULE_VERSION_IN_PROGRESS("RULE_VERSION_IN_PROGRESS", "规则包已有草稿或待审批版本"),

    /** 提交 / 作废的版本不是 DRAFT，批准 / 驳回的版本不是 PENDING_APPROVAL */
    RULE_VERSION_STATUS_INVALID("RULE_VERSION_STATUS_INVALID", "版本当前状态不允许该操作"),

    /** 提交或作废规则版本的操作人不是编制人 */
    RULE_SUBMITTER_NOT_MAKER("RULE_SUBMITTER_NOT_MAKER", "只有制单人可以提交或作废草稿"),

    /** 规则行集为空、行号非正或重复、行代码重复、代码不在目录、科目不存在 / 不可过账 / 已停用 / 为 Owner 控制科目，或提交时某指令类型缺少借方或贷方 */
    RULE_LINE_INVALID("RULE_LINE_INVALID", "规则行不合法：科目、代码或借贷配置不符合要求"),

    /** 批准时已到生效时间，但生效时间不晚于规则包当前生效版本 */
    RULE_EFFECTIVE_FROM_INVALID("RULE_EFFECTIVE_FROM_INVALID", "生效时间必须晚于当前生效版本");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    LedgerErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 稳定错误 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 可外显的默认消息。 */
    @Override
    public String getMessage() {
        return message;
    }
}
