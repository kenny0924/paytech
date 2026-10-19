package com.paytech.commercial.api.agreement.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建 DRAFT 合同（CLIENT 或 PARTNER）。
 * <li>agreementNo 是业务唯一身份：同号同内容返回原合同，同号不同内容返回 409。</li>
 */
@Getter
@Setter
public class CreateAgreementRequestBody {
    /** 合同编号，全局唯一，1–64 位字母数字及 . _ - */
    @NotBlank(message = "agreementNo 必填")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", message = "agreementNo 只能包含字母、数字及 . _ -，最长 64 位")
    private String agreementNo;

    /** 合同展示名称，1–255 字符 */
    @NotBlank(message = "agreementName 必填")
    @Size(max = 255, message = "agreementName 最长 255 字符")
    private String agreementName;

    /** 合同类型 */
    @NotBlank(message = "agreementType 必填")
    @Pattern(regexp = "CLIENT|PARTNER", message = "agreementType 只能为 CLIENT、PARTNER")
    private String agreementType;

    /**
     * 业务模式
     * <li>CLIENT 合同必填，PARTNER 合同必须为空；创建后不可修改。</li>
     */
    @Pattern(regexp = "MOR|PSP", message = "businessMode 只能为 MOR、PSP")
    private String businessMode;

    /**
     * 资金模式
     * <li>CLIENT 合同必填，PARTNER 必须为空；本期只接受 SETTLE_THROUGH_PLATFORM。</li>
     */
    @Pattern(regexp = "SETTLE_THROUGH_PLATFORM|FEES_ONLY", message = "fundMode 只能为 SETTLE_THROUGH_PLATFORM、FEES_ONLY")
    private String fundMode;

    /**
     * 定价权归属
     * <li>CLIENT 合同必填，PARTNER 必须为空；PARTNER 时 referralPartnerId 必填；本期只接受 PLATFORM。</li>
     */
    @Pattern(regexp = "PLATFORM|PARTNER", message = "pricingAuthority 只能为 PLATFORM、PARTNER")
    private String pricingAuthority;

    /**
     * 推荐伙伴（client-master Local Partner 值引用）
     * <li>仅 CLIENT 合同可填。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "referralPartnerId 必须是规范正十进制 ID")
    private String referralPartnerId;

    /**
     * Client ID
     * <li>CLIENT 合同必填，PARTNER 必须为空。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "clientId 必须是规范正十进制 ID")
    private String clientId;

    /**
     * Local Partner ID
     * <li>PARTNER 合同必填，CLIENT 必须为空。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "localPartnerId 必须是规范正十进制 ID")
    private String localPartnerId;

    /**
     * CLIENT 合同准确绑定的 OE
     * <li>CLIENT 必填，PARTNER 必须为空。</li>
     */
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "entityNo 必须是 5 位 OE 编号")
    private String entityNo;

    /**
     * PARTNER 合同覆盖的 OE
     * <li>CLIENT 必须为空；PARTNER 提交签署前至少一个；不得重复，最多 100 个。</li>
     */
    @Size(max = 100, message = "operatingEntityNos 最多 100 个")
    private List<@NotNull(message = "operatingEntityNos 不能含空值") @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "operatingEntityNos 必须是 5 位 OE 编号") String> operatingEntityNos;

    /** 生效起点（含），带时区偏移的 ISO 8601 */
    @NotBlank(message = "effectiveFrom 必填")
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})", message = "effectiveFrom 必须是带时区偏移的 ISO 8601 时间")
    private String effectiveFrom;

    /** 生效终点（不含），可空表示未预定结束；必须晚于 effectiveFrom */
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})", message = "effectiveTo 必须是带时区偏移的 ISO 8601 时间")
    private String effectiveTo;

    /** 参与方角色，1–20 个，(partyId, partyRole) 不得重复 */
    @NotEmpty(message = "parties 至少一个")
    @Size(max = 20, message = "parties 最多 20 个")
    @Valid
    private List<AgreementPartyEntry> parties;

    /** 合同文件，最多 20 份；可空 */
    @Size(max = 20, message = "documents 最多 20 份")
    @Valid
    private List<AgreementDocumentEntry> documents;
}
