package com.paytech.operations.console.bff.ext.rest;

/** 多语言浏览器入口共用的路径前缀与路径变量格式（与 Platform 合同的请求体约束一致）。 */
final class LocalizationPaths {
    /** 管理接口前缀，对应 Platform /internal/localization/v1。 */
    static final String ADMIN = "/localization-admin/v1";

    /** BCP 47 语言代码，如 en、zh-CN。 */
    static final String LOCALE = "[a-z]{2,3}(-[A-Z][a-z]{3})?(-([A-Z]{2}|[0-9]{3}))?";

    /** 应用编码。 */
    static final String APPLICATION = "[a-z][a-z0-9-]{1,63}";

    /** 命名空间编码。 */
    static final String NAMESPACE = "[a-z][a-z0-9-]{0,63}";

    /** 十进制字符串 ID。 */
    static final String ID = "[1-9][0-9]{0,18}";

    private LocalizationPaths() { }
}
