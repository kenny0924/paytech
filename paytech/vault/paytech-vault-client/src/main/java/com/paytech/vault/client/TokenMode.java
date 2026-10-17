package com.paytech.vault.client;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;

/** 获批的 Token 语义，Wire 层仅传递稳定 Code。 */
@AllArgsConstructor
public enum TokenMode implements CodeEnum {
    /** 不同逻辑命令创建独立引用；同一命令重放仍返回原 Token。 */
    REFERENCE("REFERENCE"),

    /** 相同数据类型和精确原值在不同调用者之间复用同一 Token。 */
    STABLE("STABLE");

    private final String code;

    @Override
    public String getCode() {
        return code;
    }
}
