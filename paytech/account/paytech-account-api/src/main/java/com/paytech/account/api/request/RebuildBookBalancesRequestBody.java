package com.paytech.account.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 发起账簿余额重建。 */
@Getter
@Setter
public class RebuildBookBalancesRequestBody {
    /** 发起重建的内部操作人，规范十进制标识；只用于日志审计。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String operatorId;
}
