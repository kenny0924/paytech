package com.paytech.commerce.client.api.v1.customer.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paytech.commerce.client.api.v1.common.ClientApiConstraints;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 创建 Customer 请求。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateCustomerRequestBody {
    /** Client 命名空间内的不透明、非 PII 消费者引用；不得为 Email、Phone、证件号等个人原值。 */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = ClientApiConstraints.CONSUMER_REFERENCE)
    private String consumerReference;

    @Override
    public String toString() {
        return "CreateCustomerRequestBody[REDACTED]";
    }
}
