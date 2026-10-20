package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 自动记账规则包与规则版本页面模型
 * <li>字段与 ledger AccountingRuleBundleApi 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>编制人与复核人不在请求体中，由 BFF 从会话填入 X-Gateway-Actor-Subject。</li>
 *
 * @author Claude
 */
public final class FinanceRuleBundleModels {

    /** 只承载类型，禁止创建实例。 */
    private FinanceRuleBundleModels() {
    }

    /** 全部规则包。 */
    @Getter
    @Setter
    public static class RuleBundleListResponseBody {
        /** 按规则包代码升序的规则包。 */
        private List<RuleBundleSummaryResponseBody> items;
    }

    /** 规则包及其当前生效版本。 */
    @Getter
    @Setter
    public static class RuleBundleSummaryResponseBody {
        /** 规则包代码。 */
        private String bundleCode;

        /** 规则包状态：DRAFT、ACTIVE 或 INACTIVE。 */
        private String status;

        /** 当前生效版本号；尚无生效版本为空。 */
        private String currentBundleVer;

        /** 当前生效版本的开始适用时间（UTC）；尚无生效版本为空。 */
        private Instant currentEffectiveFrom;
    }

    /** 规则包详情及全部版本摘要。 */
    @Getter
    @Setter
    public static class RuleBundleResponseBody {
        /** 规则包主键。 */
        private String bundleId;

        /** 规则包代码。 */
        private String bundleCode;

        /** 规则包状态。 */
        private String status;

        /** 当前生效版本号；尚无生效版本为空。 */
        private String currentBundleVer;

        /** 全部版本摘要，按版本号降序。 */
        private List<RuleBundleVersionResponseBody> versions;
    }

    /** 版本摘要。 */
    @Getter
    @Setter
    public static class RuleBundleVersionResponseBody {
        /** 版本主键。 */
        private String versionId;

        /** 版本号。 */
        private String bundleVer;

        /** 版本状态。 */
        private String status;

        /** 开始适用时间（UTC）。 */
        private Instant effectiveFrom;

        /** 停止适用时间（UTC）；未失效为空。 */
        private Instant effectiveTo;
    }

    /** 规则版本详情。 */
    @Getter
    @Setter
    public static class RuleVersionResponseBody {
        /** 版本主键。 */
        private String versionId;

        /** 规则包代码。 */
        private String bundleCode;

        /** 规则包内递增的版本号。 */
        private String bundleVer;

        /** 开始适用时间（UTC）。 */
        private Instant effectiveFrom;

        /** 停止适用时间（UTC）；未失效为空。 */
        private Instant effectiveTo;

        /** 版本状态：DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、RETIRED 或 VOIDED。 */
        private String status;

        /** 编制人 Account ID。 */
        private String makerId;

        /** 复核人 Account ID；未复核为空。 */
        private String checkerId;

        /** 批准时间（UTC）；未批准或已驳回为空。 */
        private Instant approvedTime;

        /** 按指令类型、行号升序的规则行。 */
        private List<RuleVersionLineResponseBody> lines;
    }

    /** 规则行。 */
    @Getter
    @Setter
    public static class RuleVersionLineResponseBody {
        /** 规则行主键。 */
        private String ruleLineId;

        /** 指令类型。 */
        private String instructionType;

        /** 同一指令类型内的行号。 */
        private Integer lineNo;

        /** 同一指令类型内的行代码。 */
        private String lineCode;

        /** 金额来源。 */
        private String amountSourceCode;

        /** 核算对象角色。 */
        private String ownerRole;

        /** 科目代码。 */
        private String accountCode;

        /** 余额桶。 */
        private String balanceBucket;

        /** 借贷方向。 */
        private String entrySide;

        /** 金额来源缺失时是否拒绝生成指令。 */
        private Boolean requiredFlag;
    }

    /** 新建规则版本（DRAFT）；编制人取自会话。 */
    @Getter
    @Setter
    public static class CreateRuleVersionRequestBody {
        /** 版本开始适用时间（UTC）；批准时已到则直接生效，否则保持 APPROVED 到时生效。 */
        @NotNull(message = "生效时间必填")
        private Instant effectiveFrom;

        /** 完整有序规则行集；同一指令类型内行号、行代码唯一。 */
        @NotNull(message = "规则行必填")
        @Size(max = 200, message = "规则行不超过 200 行")
        private List<@Valid @NotNull RuleLineRequestBody> lines;
    }

    /** 规则行。 */
    @Getter
    @Setter
    public static class RuleLineRequestBody {
        /** 指令类型。 */
        @NotBlank(message = "指令类型必填")
        @Size(max = 64, message = "指令类型不超过 64 个字符")
        private String instructionType;

        /** 同一指令类型内的行号。 */
        @NotNull(message = "行号必填")
        private Integer lineNo;

        /** 同一指令类型内的行代码。 */
        @NotBlank(message = "行代码必填")
        @Size(max = 64, message = "行代码不超过 64 个字符")
        private String lineCode;

        /** 金额来源。 */
        @NotBlank(message = "金额来源必填")
        @Size(max = 64, message = "金额来源不超过 64 个字符")
        private String amountSourceCode;

        /** 核算对象角色。 */
        @NotBlank(message = "核算对象角色必填")
        @Size(max = 64, message = "核算对象角色不超过 64 个字符")
        private String ownerRole;

        /** 科目代码；必须在 STANDARD 科目表中存在且允许过账。 */
        @NotBlank(message = "科目代码必填")
        @Size(max = 64, message = "科目代码不超过 64 个字符")
        private String accountCode;

        /** 余额桶：PENDING、AVAILABLE、LOCKED 仅用于 Client/Partner 应付科目，其余为 NONE。 */
        @NotBlank(message = "余额桶必填")
        @Size(max = 64, message = "余额桶不超过 64 个字符")
        private String balanceBucket;

        /** 借贷方向：DEBIT 或 CREDIT。 */
        @NotBlank(message = "借贷方向必填")
        @Size(max = 64, message = "借贷方向不超过 64 个字符")
        private String entrySide;

        /** 金额来源缺失时是否拒绝生成指令。 */
        @NotNull(message = "必需标记必填")
        private Boolean requiredFlag;
    }
}
