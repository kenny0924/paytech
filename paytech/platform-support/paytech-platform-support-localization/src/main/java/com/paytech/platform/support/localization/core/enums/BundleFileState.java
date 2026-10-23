package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 语言包文件在文件服务中的检查结论；补偿任务据此决定上线、失败或重试，不持久化。 */
@Getter
@RequiredArgsConstructor
public enum BundleFileState implements CodeEnum {

    /** 检查通过且对象可用。 */
    AVAILABLE("AVAILABLE"),

    /** 仍在检查。 */
    PENDING("PENDING"),

    /** 检查拒绝、隔离、对象不可用或文件不存在。 */
    REJECTED("REJECTED");

    private final String code;
}
