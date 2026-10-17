package com.paytech.operations.console.bff.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** 字典按 Unicode 码点约束文本，允许说明/消息换行而不接受控制字符。 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DictionaryTextValidator.class)
public @interface DictionaryText {
    /** 返回字典字段校验失败时的公共提示。 */
    String message() default "字典文本格式或长度无效";
    /** 声明适用的 Bean Validation 校验组。 */
    Class<?>[] groups() default {};
    /** 保留 Bean Validation 的约束负载扩展位。 */
    Class<? extends Payload>[] payload() default {};
    /** 返回允许的 Unicode 码点数上限。 */
    int max();
    /** 声明该字段是否允许显式 null。 */
    boolean nullable() default false;
    /** 声明是否允许文本中的换行、回车及制表符。 */
    boolean multiline() default false;
    /** 声明是否要求值已去除首尾空白。 */
    boolean trimmed() default false;
}
