package com.paytech.platform.support.api.dataquery.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 页面已加载的一类对象：行主体键类型及其已取得的值，供服务端跳过“按自身 ID 查”的重复执行（technique 8.7）。 */
@Getter
@Setter
public class JointQueryLoadedSubject {

    /** 行主体键类型编码。 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "keyType 只能为大写字母开头的大写字母、数字与下划线，2..64 位")
    private String keyType;

    /**
     * 已加载的值
     * <li>字符串，1..200 项，每项 1..128 字符；按键类型的值类型规范化（INTEGER 去前导零），规范化后去重。</li>
     */
    @NotEmpty
    @Size(max = 200)
    private List<@NotBlank @Size(max = 128) String> values;
}
