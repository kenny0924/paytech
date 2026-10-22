package com.paytech.client.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 浏览器上当前允许的文件动作；执行时 BFF 与 Platform 仍重新授权。 */
public enum OnboardingFileAction implements CodeEnum {
    /** 扫描可用且对象有效时可下载。 */
    DOWNLOAD("DOWNLOAD");

    private final String code;

    OnboardingFileAction(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
