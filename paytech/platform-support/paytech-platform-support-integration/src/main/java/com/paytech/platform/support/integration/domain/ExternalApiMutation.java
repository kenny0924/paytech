package com.paytech.platform.support.integration.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 外部 API 配置写入结果。 */
@Getter
@AllArgsConstructor
public class ExternalApiMutation {

    /** 对象类型，如 STANDARD_API、INTEGRATION。 */
    private final String objectType;

    /** 对象编码。 */
    private final String objectCode;

    /** 保存后的版本；删除为删除前的版本。 */
    private final int version;
}
