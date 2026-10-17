package com.paytech.commerce.client.api.v1.refund.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.paytech.commerce.client.api.v1.common.ClientApiConstraints;
import com.paytech.foundation.common.json.RejectExplicitNullDeserializer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Client 提交的全额或部分退款申请。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateRefundRequestBody {
    /** Client 命名空间内永久唯一的业务退款号；重复提交须保持原内容。 */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
    private String clientRefundId;

    /** 订单币种主单位的正数金额字符串，例如 USD "10.00" 表示退 10 美元。 */
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL)
    private String amount;

    /** 创建时确定的退款原因代码。 */
    @NotBlank
    @Pattern(regexp = "^(REQUESTED_BY_CUSTOMER|DUPLICATE|FRAUDULENT|OTHER)$")
    private String reasonCode;

    /** 可省略的非敏感说明；禁止包含凭据、卡数据或个人敏感信息。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(using = RejectExplicitNullDeserializer.class)
    @Size(max = 512)
    private String reasonSummary;
}
