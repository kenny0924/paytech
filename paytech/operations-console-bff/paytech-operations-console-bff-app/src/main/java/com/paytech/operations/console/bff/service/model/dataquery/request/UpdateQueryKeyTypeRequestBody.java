package com.paytech.operations.console.bff.service.model.dataquery.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 修改联合查询键类型；编码不可修改，状态通过启停接口修改。 */
@Getter
@Setter
public class UpdateQueryKeyTypeRequestBody {

    /** 键类型名称，1..128 字符，在 Operations 页面显示。 */
    @NotBlank
    @Size(max = 128)
    private String keyTypeName;

    /**
     * 业务分组显示名
     * <li>1..32 字符；同名即同组。修改只影响链路图展示。</li>
     */
    @NotBlank
    @Size(max = 32)
    private String groupName;

    /**
     * 键值类型
     * <li>INTEGER 或 STRING。</li>
     * <li>被 ACTIVE 模板当前版本引用时不能修改，返回 400，消息列出前 5 个模板编码。</li>
     */
    @NotBlank
    @Pattern(regexp = "INTEGER|STRING", message = "valueType 只能为 INTEGER 或 STRING")
    private String valueType;

    /**
     * 默认角色
     * <li>LINK 或 DIMENSION；只影响编辑器标注时的默认值，不改变已保存模板的列角色。</li>
     */
    @NotBlank
    @Pattern(regexp = "LINK|DIMENSION", message = "defaultRole 只能为 LINK 或 DIMENSION")
    private String defaultRole;

    /** 是否出现在联合查询入口下拉。 */
    @NotNull
    private Boolean entryAllowed;

    /**
     * 入口值校验正则
     * <li>可选，最多 256 字符；为空不校验格式。语法限制同新建，不通过返回 422，field 为 valuePattern。</li>
     */
    @Size(max = 256)
    private String valuePattern;

    /** 入口输入框占位文字与格式不匹配时的提示，可选，最多 128 字符。 */
    @Size(max = 128)
    private String inputHint;

    /**
     * 业务流程顺序号
     * <li>0..9999，升序；整体重排使用排序接口。</li>
     */
    @NotNull
    @Min(0)
    @Max(9999)
    private Integer sortNo;

    /** 说明，可选，最多 512 字符。 */
    @Size(max = 512)
    private String description;

    /**
     * 调用方读取的键类型原版本
     * <li>正整数；版本不符返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
