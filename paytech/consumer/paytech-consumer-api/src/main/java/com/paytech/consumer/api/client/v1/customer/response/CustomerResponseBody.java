package com.paytech.consumer.api.client.v1.customer.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Customer 视图。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerResponseBody {
    /** 不透明的公开 Customer 编号。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    private String customerNo;

    /** Client 传入的消费者引用。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    private String consumerReference;

    /** 状态：ACTIVE 或 DELETED。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    private String status;

    /** 创建时间。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    private Instant createdAt;

    /** 删除时间；有效时省略。 */
    private Instant deletedAt;
}
