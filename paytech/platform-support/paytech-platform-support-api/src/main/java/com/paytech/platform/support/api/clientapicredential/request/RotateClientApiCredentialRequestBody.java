package com.paytech.platform.support.api.clientapicredential.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 轮换 API 凭证。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RotateClientApiCredentialRequestBody {

    /** 调用方看到的当前版本；不一致返回 409。 */
    @Positive
    private int expectedVersion;

    /** 旧凭证宽限分钟数，只能是 0（立即撤销）、60、1440 或 10080。 */
    private int graceMinutes;
}
