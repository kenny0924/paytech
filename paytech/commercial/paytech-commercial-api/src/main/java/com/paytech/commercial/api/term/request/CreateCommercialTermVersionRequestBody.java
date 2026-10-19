package com.paytech.commercial.api.term.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 在方案下创建 DRAFT 版本；版本号由服务端递增，可复制已有版本的全部价格、组件与档位。 */
@Getter
@Setter
public class CreateCommercialTermVersionRequestBody {
    /** 方案 BCR 根当前修订版本（CAS 令牌）；不等于当前值时 409。 */
    @NotBlank(message = "expectedChangeVersion 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "expectedChangeVersion 格式不正确")
    private String expectedChangeVersion;


    /**
     * 复制来源版本 ID
     * <li>必须属于同一方案；复制其全部价格、组件与档位，未给出的定价模型与含税口径沿用来源版本。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "copyFromVersionId 格式不正确")
    private String copyFromVersionId;

    /**
     * 版本冻结的合同 ID
     * <li>Client 条款为空或等于方案合同；Partner 条款（方案不存合同）必填，须为同一 Local Partner 的 ACTIVE 或待签 PARTNER 合同。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "agreementId 格式不正确")
    private String agreementId;

    /**
     * 定价模型
     * <li>BLENDED、INTERCHANGE_PLUS_PLUS；不复制时必填。本期排期与激活只接受 BLENDED。</li>
     */
    @Pattern(regexp = "BLENDED|INTERCHANGE_PLUS_PLUS", message = "pricingModel 只能为 BLENDED 或 INTERCHANGE_PLUS_PLUS")
    private String pricingModel;

    /**
     * 费用含税口径
     * <li>INCLUSIVE、EXCLUSIVE；不复制时必填。</li>
     */
    @Pattern(regexp = "INCLUSIVE|EXCLUSIVE", message = "feeTaxTreatment 只能为 INCLUSIVE 或 EXCLUSIVE")
    private String feeTaxTreatment;
}
