package com.paytech.client.master.api.onboarding.response;

import lombok.Getter;
import lombok.Setter;

/** CM-6 声明记录；只追加，同类型以最新一条为准。 */
@Getter
@Setter
public class DeclarationResponseBody {
    /** 声明记录 ID。 */
    private String declarationId;

    /** 申请 ID。 */
    private String applicationId;

    /** 声明类型。 */
    private String declarationType;

    /** 声明取值 TRUE 或 FALSE。 */
    private String declaredValue;

    /** 条款版本，可空。 */
    private String termsVersion;

    /** 声明 UTC 时间（ISO-8601，微秒）。 */
    private String declaredAt;
}
