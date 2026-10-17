package com.paytech.platform.support.api.clientapicredential.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 撤销 API 凭证。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevokeClientApiCredentialRequestBody {

    /** 调用方看到的当前版本；已撤销时不校验，直接返回原结果。 */
    @Positive
    private int expectedVersion;
}
