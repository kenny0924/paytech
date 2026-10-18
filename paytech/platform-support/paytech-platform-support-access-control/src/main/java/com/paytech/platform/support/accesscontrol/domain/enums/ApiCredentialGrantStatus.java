package com.paytech.platform.support.accesscontrol.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 授权记录按查询时刻派生的显示状态；不替换数据库生命周期。 */
@Getter
@AllArgsConstructor
public enum ApiCredentialGrantStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    REVOKED("REVOKED"),

    EXPIRED("EXPIRED"),

    PENDING("PENDING");

    private final String code;
}
