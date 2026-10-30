package com.paytech.risk.decision.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * Client 限额档位的公开业务错误；HTTP 状态由 risk-decision 与 operations-console-bff 的状态解析器映射
 * <li>消费者：operations-console-web 按 Code 显示对应提示（并发、自审、状态变化、已有待审批申请、清单未满足、需要高级审批）。</li>
 */
public enum RiskLimitTierErrorCode implements CodeMessageEnum {
    /** Client 没有档位（申请时）；HTTP 404。 */
    RISK_LIMIT_TIER_NOT_FOUND("RISK_LIMIT_TIER_NOT_FOUND", "该 Client 没有限额档位"),

    /** 档位变更申请不存在；HTTP 404。 */
    RISK_LIMIT_TIER_CHANGE_NOT_FOUND("RISK_LIMIT_TIER_CHANGE_NOT_FOUND", "档位变更申请不存在"),

    /** 不允许的档位迁移；HTTP 409。 */
    RISK_LIMIT_TIER_TRANSITION_INVALID("RISK_LIMIT_TIER_TRANSITION_INVALID", "不允许从当前档位变更到目标档位"),

    /** 该 Client 已有待审批申请；HTTP 409。 */
    RISK_LIMIT_TIER_CHANGE_PENDING_EXISTS("RISK_LIMIT_TIER_CHANGE_PENDING_EXISTS", "该 Client 已有待审批的档位变更申请"),

    /** 期望版本不符或并发修改；HTTP 409。 */
    RISK_LIMIT_TIER_VERSION_CONFLICT("RISK_LIMIT_TIER_VERSION_CONFLICT", "档位或申请已被其他人修改，请刷新后重试"),

    /** 上线复核清单未满足；HTTP 409。 */
    RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED("RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED", "上线复核清单未满足"),

    /** 高级审批级别的申请走了普通审批接口；HTTP 409。 */
    RISK_LIMIT_TIER_ELEVATION_REQUIRED("RISK_LIMIT_TIER_ELEVATION_REQUIRED", "该申请须由高级审批人处理"),

    /** 审批人是申请人；HTTP 409。 */
    RISK_LIMIT_TIER_SELF_APPROVAL("RISK_LIMIT_TIER_SELF_APPROVAL", "不能审批自己提交的档位变更申请"),

    /** 撤回人不是申请人；HTTP 409。 */
    RISK_LIMIT_TIER_NOT_REQUESTER("RISK_LIMIT_TIER_NOT_REQUESTER", "只有申请人可以撤回档位变更申请"),

    /** 申请状态不允许该操作（非 PENDING）；HTTP 409。 */
    RISK_LIMIT_TIER_CHANGE_STATE_INVALID("RISK_LIMIT_TIER_CHANGE_STATE_INVALID", "档位变更申请当前状态不允许该操作"),

    /** 存量迁移调用方不在白名单（白名单默认空，即迁移入口关闭）；HTTP 403。 */
    RISK_LIMIT_TIER_MIGRATION_FORBIDDEN("RISK_LIMIT_TIER_MIGRATION_FORBIDDEN", "调用方无权执行限额档位迁移");

    private final String code;

    private final String message;

    RiskLimitTierErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
