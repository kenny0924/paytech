package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 多语言导入文件的上传用途；Code 是开始上传请求 purpose 的合同值，BFF 据此固定 Platform 文件原因码
 * <li>Platform 导入接口按原因码核对文件来源：key 导入只接受 I18N_KEY_IMPORT，翻译导入只接受 I18N_TRANSLATION_IMPORT。</li>
 */
public enum LocalizationUploadPurpose implements CodeEnum {
    /** key 导入文件（code-mor-i18n-source/1） */
    KEY_IMPORT("KEY_IMPORT", "I18N_KEY_IMPORT"),

    /** 一个语言的翻译导入文件 */
    TRANSLATION_IMPORT("TRANSLATION_IMPORT", "I18N_TRANSLATION_IMPORT");

    private final String code;

    private final String reasonCode;

    LocalizationUploadPurpose(String code, String reasonCode) {
        this.code = code;
        this.reasonCode = reasonCode;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** Platform 文件上传原因码。 */
    public String getReasonCode() {
        return reasonCode;
    }
}
