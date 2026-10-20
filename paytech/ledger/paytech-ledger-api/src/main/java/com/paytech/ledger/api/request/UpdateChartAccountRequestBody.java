package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 修改科目名称、编号与上级请求；三项均按修改后的完整值提交。 */
@Getter
@Setter
public class UpdateChartAccountRequestBody {
    /** 修改后的科目名称，1—255 字；任何时候可改。 */
    @NotBlank
    @Size(max = 255)
    private String accountName;

    /**
     * 修改后的科目编号
     * <li>与当前值不同时：科目表已被账簿采用 → CHART_STRUCTURE_FROZEN；科目有下级 → CHART_PARENT_INVALID。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{3}([0-9]{2}){0,3}", message = "科目编号必须为 4、6、8 或 10 位数字")
    private String accountNo;

    /**
     * 修改后的上级科目编号
     * <li>为空（可显式为 null）表示一级科目；变化时须为同表、启用、类别相同的汇总科目且不形成循环。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{3}([0-9]{2}){0,2}", message = "上级科目编号必须为 4、6 或 8 位数字")
    private String parentAccountNo;
}
