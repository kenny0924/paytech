package com.paytech.client.contract.api.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** 目录可用性，不构成业务准入或提交资格。 */
public enum ClientFormCatalogStatus implements CodeEnum {
    AVAILABLE,

    COUNTRY_REQUIRED,

    UNAVAILABLE;
    /** 返回跨服务稳定代码。 */
    @Override public String getCode() { return name(); }
}
