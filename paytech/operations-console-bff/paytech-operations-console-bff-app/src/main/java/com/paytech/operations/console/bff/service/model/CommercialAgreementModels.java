package com.paytech.operations.console.bff.service.model;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同、签署、订阅与开通项的页面模型（commercial AgreementApi 的 BFF 投影）
 * <li>请求与响应字段与 commercial 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移（502）。</li>
 * <li>BFF 只做结构性约束（必填、列表上限、ID 形状），业务规则与完整格式校验由 commercial 执行，其 422 字段路径投影回本次 Body。</li>
 * <li>页面聚合模型 {@link AgreementOverviewResponseBody} 额外携带当前与已排期条款版本（来自费率卡读取）。</li>
 *
 * @author Claude
 */
public final class CommercialAgreementModels {
    /** 规范正十进制 ID。 */
    public static final String ID = "[1-9][0-9]{0,18}";

    /** 只承载类型，禁止创建实例。 */
    private CommercialAgreementModels() {
    }

    // ---------- 请求 ----------

    /** 创建 DRAFT 合同（CLIENT 或 PARTNER）。 */
    @Getter
    @Setter
    public static class CreateAgreementRequestBody {
        /** 合同编号，全局唯一，1–64 位字母数字及 . _ - */
        @NotBlank(message = "合同编号必填")
        @Size(max = 64, message = "合同编号最长 64 位")
        private String agreementNo;

        /** 合同展示名称 */
        @NotBlank(message = "合同名称必填")
        @Size(max = 255, message = "合同名称最长 255 字符")
        private String agreementName;

        /** 合同类型：CLIENT、PARTNER */
        @NotBlank(message = "合同类型必填")
        private String agreementType;

        /** 业务模式（CLIENT 必填）：MOR、PSP */
        private String businessMode;

        /** 资金模式（CLIENT 必填）：本期只接受 SETTLE_THROUGH_PLATFORM */
        private String fundMode;

        /** 定价权归属（CLIENT 必填）：本期只接受 PLATFORM */
        private String pricingAuthority;

        /** 推荐伙伴 Local Partner ID，仅 CLIENT 合同可填 */
        private String referralPartnerId;

        /** Client ID（CLIENT 必填） */
        private String clientId;

        /** Local Partner ID（PARTNER 必填） */
        private String localPartnerId;

        /** CLIENT 合同准确绑定的 OE */
        private String entityNo;

        /** PARTNER 合同覆盖的 OE，最多 100 个 */
        @Size(max = 100, message = "覆盖 OE 最多 100 个")
        private List<String> operatingEntityNos;

        /** 生效起点（含），带时区偏移的 ISO 8601 */
        @NotBlank(message = "生效起点必填")
        private String effectiveFrom;

        /** 生效终点（不含），可空 */
        private String effectiveTo;

        /** 参与方角色，1–20 个 */
        @NotEmpty(message = "参与方至少一个")
        @Size(max = 20, message = "参与方最多 20 个")
        @Valid
        private List<AgreementPartyEntry> parties;

        /** 合同文件，最多 20 份 */
        @Size(max = 20, message = "合同文件最多 20 份")
        @Valid
        private List<AgreementDocumentEntry> documents;
    }

    /** 参与方的一个角色。 */
    @Getter
    @Setter
    public static class AgreementPartyEntry {
        /** 法律 Party ID */
        @NotBlank(message = "Party ID 必填")
        @Pattern(regexp = ID, message = "Party ID 必须是正十进制整数")
        private String partyId;

        /** Party 资料版本 ID */
        @NotBlank(message = "资料版本 ID 必填")
        @Pattern(regexp = ID, message = "资料版本 ID 必须是正十进制整数")
        private String partyProfileId;

        /** 角色：CLIENT、LOCAL_PARTNER、PLATFORM_SIGNATORY、OPERATING_ENTITY */
        @NotBlank(message = "角色必填")
        private String partyRole;
    }

    /** 创建时登记的合同文件。 */
    @Getter
    @Setter
    public static class AgreementDocumentEntry {
        /** 文件用途：SIGNING_DOCUMENT、ANNEX */
        @NotBlank(message = "文件用途必填")
        private String documentType;

        /** Platform Support 文件 ID */
        @NotBlank(message = "文件 ID 必填")
        @Pattern(regexp = ID, message = "文件 ID 必须是正十进制整数")
        private String fileId;

        /** 文件 SHA-256，64 位小写十六进制 */
        @NotBlank(message = "文件哈希必填")
        private String contentHash;
    }

    /** 签署证据或终止通知等单份文件。 */
    @Getter
    @Setter
    public static class AgreementFileEntry {
        /** Platform Support 文件 ID */
        @NotBlank(message = "文件 ID 必填")
        @Pattern(regexp = ID, message = "文件 ID 必须是正十进制整数")
        private String fileId;

        /** 文件 SHA-256，64 位小写十六进制 */
        @NotBlank(message = "文件哈希必填")
        private String contentHash;
    }

    /** 状态推进（提交签署、激活）的版本条件。 */
    @Getter
    @Setter
    public static class AgreementTransitionRequestBody {
        /** 页面读取到的合同 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;
    }

    /** 登记一个 Party 全部角色的签署结果。 */
    @Getter
    @Setter
    public static class RecordAgreementAcceptanceRequestBody {
        /** 页面读取到的合同 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 全局唯一的接受事件编号 */
        @NotBlank(message = "签署编号必填")
        @Size(max = 128, message = "签署编号最长 128 位")
        private String acceptanceReference;

        /** 签署方式：E_SIGNATURE、CLICK_ACCEPTANCE、MANUAL_SIGNATURE */
        @NotBlank(message = "签署方式必填")
        private String acceptanceMethod;

        /** 实际完成接受的外部 Actor ID（CLICK_ACCEPTANCE 必填） */
        private String acceptedByActorId;

        /** 接受事实成立时刻，带时区偏移的 ISO 8601 */
        @NotBlank(message = "签署时刻必填")
        private String acceptedAt;

        /** 最终执行文件（E_SIGNATURE / MANUAL_SIGNATURE 必填） */
        @Valid
        private AgreementFileEntry executedDocument;
    }

    /** 终止 ACTIVE 合同。 */
    @Getter
    @Setter
    public static class TerminateAgreementRequestBody {
        /** 页面读取到的合同 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 合同生效终点（不含） */
        @NotBlank(message = "生效终点必填")
        private String effectiveTo;

        /** 终止通知文件 */
        @NotNull(message = "终止通知文件必填")
        @Valid
        private AgreementFileEntry terminationNotice;
    }

    /** 整体替换订阅产品集合。 */
    @Getter
    @Setter
    public static class ReplaceAgreementProductsRequestBody {
        /** 页面读取到的合同 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 订阅的产品代码，可为空集合，最多 20 个 */
        @NotNull(message = "产品集合必填，可为空列表")
        @Size(max = 20, message = "产品最多 20 个")
        private List<String> productCodes;
    }

    /** 新增开通项。 */
    @Getter
    @Setter
    public static class AddAgreementCapabilityRequestBody {
        /** 页面读取到的合同 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 开通项适用的准确 OE */
        @NotBlank(message = "OE 必填")
        private String entityNo;

        /** 可开通项代码 */
        @NotBlank(message = "可开通项代码必填")
        private String capabilityCode;

        /** 开通生效起点（含） */
        @NotBlank(message = "生效起点必填")
        private String effectiveFrom;

        /** 开通生效终点（不含），可空 */
        private String effectiveTo;

        /** 滚动保证金比例，与天数成组为空或非空 */
        private String rollingReserveRate;

        /** 滚动保证金持有天数 */
        private Integer rollingReserveDays;

        /** 接受条件，整体提交，每个维度一项（同维度多值 OR、异维度 AND），最多 20 个维度 */
        @NotNull(message = "接受条件必填")
        @Size(max = 20, message = "接受条件最多 20 个维度")
        @Valid
        private List<CapabilityConditionEntry> conditions;

        /** 是否要求 3DS 认证 */
        @NotNull(message = "3DS 要求必填")
        private Boolean require3ds;
    }

    /** 整体改写开通项。 */
    @Getter
    @Setter
    public static class UpdateAgreementCapabilityRequestBody {
        /** 页面读取到的合同 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 合同成员资格：ACTIVE（纳入）、DISABLED（关闭）；执行状态由前置门禁与暂停 / 恢复维护 */
        @NotBlank(message = "状态必填")
        private String status;

        /** 开通生效起点（含） */
        @NotBlank(message = "生效起点必填")
        private String effectiveFrom;

        /** 开通生效终点（不含），可空 */
        private String effectiveTo;

        /** 滚动保证金比例 */
        private String rollingReserveRate;

        /** 滚动保证金持有天数 */
        private Integer rollingReserveDays;

        /** 接受条件，整体提交，每个维度一项（同维度多值 OR、异维度 AND），最多 20 个维度 */
        @NotNull(message = "接受条件必填")
        @Size(max = 20, message = "接受条件最多 20 个维度")
        @Valid
        private List<CapabilityConditionEntry> conditions;

        /** 是否要求 3DS 认证 */
        @NotNull(message = "3DS 要求必填")
        private Boolean require3ds;
    }

    /** 运营手动暂停开通项。 */
    @Getter
    @Setter
    public static class SuspendAgreementCapabilityRequestBody {
        /** 页面读取到的合同 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 暂停原因，最长 200 字 */
        @NotBlank(message = "暂停原因必填")
        @Size(max = 200, message = "暂停原因最长 200 字")
        private String reason;
    }

    /** 运营恢复手动暂停的开通项。 */
    @Getter
    @Setter
    public static class ResumeAgreementCapabilityRequestBody {
        /** 页面读取到的合同 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;
    }

    /** 开通项的一个接受条件：一个维度及其取值。 */
    @Getter
    @Setter
    public static class CapabilityConditionEntry {
        /** 维度代码，如 CURRENCY、CARD_BRAND */
        @NotBlank(message = "维度代码必填")
        private String dimensionCode;

        /** 取值，至少一个，最多 50 个 */
        @NotEmpty(message = "条件取值至少一个")
        @Size(max = 50, message = "条件取值最多 50 个")
        private List<@NotBlank(message = "条件取值不得为空") String> values;
    }

    // ---------- 响应 ----------

    /** 合同写入结果。 */
    @Getter
    @Setter
    public static class AgreementResponseBody {
        /** 合同 ID */
        private String agreementId;

        /** 合同编号 */
        private String agreementNo;

        /** 合同当前状态 */
        private String status;

        /** 合同当前 changeVersion，下一次写入的 expectedChangeVersion */
        private Long changeVersion;
    }

    /** 开通项写入结果。 */
    @Getter
    @Setter
    public static class AgreementCapabilityResponseBody {
        /** 合同 ID */
        private String agreementId;

        /** 开通项 ID */
        private String agreementCapabilityId;

        /** 合同成员资格：ACTIVE、DISABLED */
        private String status;

        /** 执行状态：PENDING_PREREQUISITES、ENABLED、SUSPENDED_BY_PREREQUISITE、SUSPENDED_MANUALLY；DISABLED 时为空 */
        private String executionStatus;

        /** 合同当前 changeVersion */
        private Long changeVersion;
    }

    /**
     * 合同开通项前置核对结果（commercial 原样）
     * <li>结论只在本次响应返回；原因码为开放字符串，页面按 commercial.enum.CapabilityPrerequisiteReason.&lt;CODE&gt; 取文案。</li>
     */
    @Getter
    @Setter
    public static class CapabilityPrerequisiteCheckResponseBody {
        /** 合同 ID */
        private String agreementId;

        /** 核对后的合同 changeVersion；本次有状态迁移时已加一 */
        private Long changeVersion;

        /** 合同全部开通项，按开通项 ID 升序 */
        private List<PrerequisiteCheckedCapability> capabilities;
    }

    /** 一个开通项的前置核对结果。 */
    @Getter
    @Setter
    public static class PrerequisiteCheckedCapability {
        /** 开通项 ID */
        private String agreementCapabilityId;

        /** 准确 OE */
        private String entityNo;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 可开通项种类 */
        private String capabilityKind;

        /** 合同成员资格：ACTIVE、DISABLED */
        private String status;

        /** 核对后的执行状态；DISABLED 时为空 */
        private String executionStatus;

        /** 逐项结论，按 QUALIFICATION、AGREEMENT_TERM、CHANNEL_IDENTITY 顺序；DISABLED 时为空数组 */
        private List<CapabilityPrerequisiteCheck> checks;
    }

    /** 一项前置的结论。 */
    @Getter
    @Setter
    public static class CapabilityPrerequisiteCheck {
        /** 前置项：QUALIFICATION、AGREEMENT_TERM、CHANNEL_IDENTITY */
        private String code;

        /** 结论：SATISFIED、UNSATISFIED、UNKNOWN、SKIPPED */
        private String result;

        /** 原因码，SATISFIED 时为空 */
        private String reasonCode;

        /** 文案参数，如 subjectType、subjectId、entityNo、termType、agreementStatus */
        private Map<String, String> params;
    }

    /** 合同详情：主档、参与方、文件、覆盖 OE、订阅产品与开通项（commercial 原样）。 */
    @Getter
    @Setter
    public static class AgreementDetail {
        /** 合同 ID */
        private String agreementId;

        /** 合同编号 */
        private String agreementNo;

        /** 合同名称 */
        private String agreementName;

        /** 合同类型：CLIENT、PARTNER */
        private String agreementType;

        /** 业务模式；PARTNER 为空 */
        private String businessMode;

        /** 资金模式；PARTNER 为空 */
        private String fundMode;

        /** 定价权归属；PARTNER 为空 */
        private String pricingAuthority;

        /** 推荐伙伴；可空 */
        private String referralPartnerId;

        /** Client ID；PARTNER 为空 */
        private String clientId;

        /** Local Partner ID；CLIENT 为空 */
        private String localPartnerId;

        /** CLIENT 合同 OE；PARTNER 为空 */
        private String entityNo;

        /** PARTNER 合同覆盖 OE，升序 */
        private List<String> operatingEntityNos;

        /** 合同状态 */
        private String status;

        /** 生效起点（含） */
        private String effectiveFrom;

        /** 生效终点（不含），可空 */
        private String effectiveTo;

        /** 签署完成时刻，可空 */
        private String signedTime;

        /** 激活时刻，可空 */
        private String activatedTime;

        /** 合同当前 changeVersion */
        private Long changeVersion;

        /** 参与方角色 */
        private List<AgreementPartyDetail> parties;

        /** 合同文件 */
        private List<AgreementDocumentDetail> documents;

        /** 订阅的产品代码 */
        private List<String> productCodes;

        /** 开通项 */
        private List<AgreementCapabilityDetail> capabilities;
    }

    /** 参与方角色及其签署结果。 */
    @Getter
    @Setter
    public static class AgreementPartyDetail {
        /** Party 角色行 ID */
        private String agreementPartyId;

        /** 法律 Party ID */
        private String partyId;

        /** Party 资料版本 ID */
        private String partyProfileId;

        /** 角色 */
        private String partyRole;

        /** 签署方式；未签为空 */
        private String acceptanceMethod;

        /** 实际接受的外部 Actor；可空 */
        private String acceptedByActorId;

        /** 接受时刻；未签为空 */
        private String acceptedAt;

        /** 合同接受事件编号；未签为空 */
        private String acceptanceReference;

        /** 签署证据文件引用 ID；未签为空 */
        private String evidenceDocumentId;
    }

    /** 合同文件引用。 */
    @Getter
    @Setter
    public static class AgreementDocumentDetail {
        /** 文件引用 ID */
        private String agreementDocumentId;

        /** 文件用途 */
        private String documentType;

        /** Platform Support 文件 ID */
        private String fileId;

        /** 文件 SHA-256 */
        private String contentHash;
    }

    /** 开通项及其接受条件。 */
    @Getter
    @Setter
    public static class AgreementCapabilityDetail {
        /** 开通项 ID */
        private String agreementCapabilityId;

        /** 准确 OE */
        private String entityNo;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 合同成员资格：ACTIVE、DISABLED */
        private String status;

        /** 执行状态：PENDING_PREREQUISITES、ENABLED、SUSPENDED_BY_PREREQUISITE、SUSPENDED_MANUALLY；DISABLED 时为空 */
        private String executionStatus;

        /** 生效起点（含） */
        private String effectiveFrom;

        /** 生效终点（不含），可空 */
        private String effectiveTo;

        /** 滚动保证金比例，可空 */
        private String rollingReserveRate;

        /** 滚动保证金天数，可空 */
        private Integer rollingReserveDays;

        /** 接受条件，按维度代码排序 */
        private List<CapabilityConditionDetail> conditions;

        /** 是否要求 3DS 认证 */
        private Boolean require3ds;
    }

    /** 开通项的一个接受条件。 */
    @Getter
    @Setter
    public static class CapabilityConditionDetail {
        /** 维度代码 */
        private String dimensionCode;

        /** 取值，按字典序 */
        private List<String> values;
    }

    /**
     * 合同详情页聚合：合同主档与当前 / 已排期条款版本
     * <li>termVersionsStatus：AVAILABLE（已读取）、UNAVAILABLE（费率卡读取失败或超时，不伪造空数据）、
     * NOT_APPLICABLE（PARTNER 合同或非 ACTIVE 合同，费率卡不覆盖）。</li>
     */
    @Getter
    @Setter
    public static class AgreementOverviewResponseBody {
        /** 合同详情 */
        private AgreementDetail agreement;

        /** 条款版本部分的可用性 */
        private String termVersionsStatus;

        /** 本合同的当前与已排期条款版本（仅 Client 条款），按方案、生效起点排序 */
        private List<AgreementTermVersion> termVersions;
    }

    /** 合同下的一个当前或已排期条款版本（费率卡投影，不含价格）。 */
    @Getter
    @Setter
    public static class AgreementTermVersion {
        /** 方案 ID */
        private String planId;

        /** 条款类型 */
        private String termType;

        /** 版本 ID */
        private String versionId;

        /** 版本号 */
        private String versionNo;

        /** 版本状态 */
        private String status;

        /** CURRENT 或 SCHEDULED */
        private String timing;

        /** 生效起点 */
        private String effectiveFrom;

        /** 生效终点，可空 */
        private String effectiveTo;

        /** 定价模型 */
        private String pricingModel;

        /** 费用含税口径 */
        private String feeTaxTreatment;
    }
}
