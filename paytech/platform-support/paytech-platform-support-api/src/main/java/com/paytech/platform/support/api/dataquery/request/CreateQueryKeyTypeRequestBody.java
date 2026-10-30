package com.paytech.platform.support.api.dataquery.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 新建联合查询键类型：状态 ACTIVE，立即可被模板的联合查询配置引用。 */
@Getter
@Setter
public class CreateQueryKeyTypeRequestBody {

    /**
     * 键类型编码
     * <li>全局唯一，创建后不可修改，模板联合查询配置按它引用；格式 [A-Z][A-Z0-9_]{1,63}。</li>
     * <li>编码已存在返回 400。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "keyTypeCode 只能为大写字母开头的大写字母、数字与下划线，2..64 位")
    private String keyTypeCode;

    /** 键类型名称，1..128 字符，在 Operations 页面显示。 */
    @NotBlank
    @Size(max = 128)
    private String keyTypeName;

    /**
     * 业务分组显示名
     * <li>1..32 字符，如“收单”“支付”“入驻”“出款”；同名即同组，决定链路图泳道。</li>
     */
    @NotBlank
    @Size(max = 32)
    private String groupName;

    /**
     * 键值类型
     * <li>INTEGER 或 STRING；决定入口值规范化与可绑定的参数类型。</li>
     */
    @NotBlank
    @Pattern(regexp = "INTEGER|STRING", message = "valueType 只能为 INTEGER 或 STRING")
    private String valueType;

    /**
     * 默认角色
     * <li>LINK 或 DIMENSION；只作为编辑器中标注该键类型时的默认角色，列上的 keyRole 才是实际角色。</li>
     */
    @NotBlank
    @Pattern(regexp = "LINK|DIMENSION", message = "defaultRole 只能为 LINK 或 DIMENSION")
    private String defaultRole;

    /** 是否出现在联合查询入口下拉。 */
    @NotNull
    private Boolean entryAllowed;

    /**
     * 入口值校验正则
     * <li>可选，最多 256 字符；为空不校验格式。执行时整串匹配，只校验入口输入的值，不校验展开得到的值。</li>
     * <li>保存时须能被 Java 编译，且不得使用反向引用（\1、\k&lt;）、前后断言（(?=、(?!、(?&lt;=、(?&lt;!）、
     * 内联标志（如 (?i）、所有格量词与原子组（*+、(?&gt;）；不通过返回 422，field 为 valuePattern。</li>
     */
    @Size(max = 256)
    private String valuePattern;

    /** 入口输入框占位文字与格式不匹配时的提示，可选，最多 128 字符。 */
    @Size(max = 128)
    private String inputHint;

    /**
     * 业务流程顺序号
     * <li>0..9999，升序；决定入口下拉与列表顺序、链路图泳道（分组取组内最小值）与泳道内节点顺序。</li>
     */
    @NotNull
    @Min(0)
    @Max(9999)
    private Integer sortNo;

    /** 说明，可选，最多 512 字符。 */
    @Size(max = 512)
    private String description;
}
