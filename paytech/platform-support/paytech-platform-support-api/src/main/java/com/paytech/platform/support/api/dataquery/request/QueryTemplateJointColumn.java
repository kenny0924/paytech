package com.paytech.platform.support.api.dataquery.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 联合查询的列标注：结果列对应的键类型与角色；未标注的列只展示。 */
@Getter
@Setter
public class QueryTemplateJointColumn {

    /**
     * 结果列名
     * <li>1..128 字符，不区分大小写匹配；同一配置内不得重复。</li>
     * <li>LINK 列不得在 columns 声明中配置屏蔽（mask 非 NONE），否则保存返回 422。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** 键类型编码，须为 ACTIVE 键类型。 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "keyType 只能为大写字母开头的大写字母、数字与下划线，2..64 位")
    private String keyType;

    /**
     * 列角色
     * <li>LINK：按该列非空值继续展开；DIMENSION：只标注，不展开。</li>
     * <li>标为 LINK 但键类型 defaultRole=DIMENSION 时保存给出 WARNING，不阻止保存。</li>
     */
    @NotBlank
    @Pattern(regexp = "LINK|DIMENSION", message = "keyRole 只能为 LINK 或 DIMENSION")
    private String keyRole;
}
