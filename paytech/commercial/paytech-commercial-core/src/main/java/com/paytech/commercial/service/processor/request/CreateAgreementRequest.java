package com.paytech.commercial.service.processor.request;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 创建合同的内部请求；agreementNo 是业务唯一身份。 */
@Getter
@Setter
public class CreateAgreementRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 合同编号 */
    @NotBlank
    private String agreementNo;

    /** 合同名称 */
    @NotBlank
    private String agreementName;

    /** 合同类型 */
    @NotNull
    private AgreementAgreementType agreementType;

    /** 业务模式；PARTNER 为空 */
    private BusinessMode businessMode;

    /** 资金模式；PARTNER 为空 */
    private FundMode fundMode;

    /** 定价权归属；PARTNER 为空 */
    private PricingAuthority pricingAuthority;

    /** 推荐伙伴，可空 */
    private Long referralPartnerId;

    /** CLIENT 合同的 Client */
    private Long clientId;

    /** PARTNER 合同的 Local Partner */
    private Long localPartnerId;

    /** CLIENT 合同的 OE */
    private String entityNo;

    /** PARTNER 合同覆盖的 OE；CLIENT 为空列表 */
    @NotNull
    private List<String> operatingEntityNos;

    /** 生效起点（含） */
    @NotNull
    private Instant effectiveFrom;

    /** 生效终点（不含），可空 */
    private Instant effectiveTo;

    /** 参与方角色 */
    @NotEmpty
    @Valid
    private List<Party> parties;

    /** 合同文件；可为空列表 */
    @NotNull
    @Valid
    private List<Document> documents;

    /** 参与方的一个角色 */
    @Getter
    @Setter
    public static class Party {
        /** 法律 Party ID */
        @NotNull
        @Positive
        private Long partyId;

        /** Party 资料版本 ID */
        @NotNull
        @Positive
        private Long partyProfileId;

        /** 角色 */
        @NotNull
        private AgreementPartyPartyRole partyRole;
    }

    /** 合同文件引用 */
    @Getter
    @Setter
    public static class Document {
        /** 文件用途 */
        @NotNull
        private AgreementDocumentRefDocumentType documentType;

        /** 文件 ID */
        @NotNull
        @Positive
        private Long fileId;

        /** 文件 SHA-256 */
        @NotBlank
        private String contentHash;
    }
}
