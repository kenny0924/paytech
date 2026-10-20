package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 在科目表下新增科目请求。 */
@Getter
@Setter
public class CreateChartAccountRequestBody {
    /**
     * 科目编号
     * <li>4-2-2-2：一级 4 位、每下一级加 2 位，最多 4 级；一级首位 1 资产、2 负债、3 权益、4 收入、5 费用。</li>
     * <li>有上级时长度为上级加 2 且以上级编号开头；无上级时必须 4 位。科目表内唯一，否则 CHART_ACCOUNT_NO_DUPLICATE。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{3}([0-9]{2}){0,3}", message = "科目编号必须为 4、6、8 或 10 位数字")
    private String accountNo;

    /**
     * 科目代码
     * <li>程序引用的稳定键，大写字母开头，科目表内唯一，否则 CHART_ACCOUNT_CODE_DUPLICATE；创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "科目代码必须为大写字母、数字与下划线，最长 64 字符")
    private String accountCode;

    /** 科目名称，1—255 字。 */
    @NotBlank
    @Size(max = 255)
    private String accountName;

    /**
     * 科目类别
     * <li>ASSET、LIABILITY、EQUITY、REVENUE 或 EXPENSE；有上级时必须与上级相同。</li>
     */
    @NotBlank
    @Pattern(regexp = "ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE", message = "科目类别非法")
    private String accountClass;

    /** 正常余额方向：DEBIT 或 CREDIT。 */
    @NotBlank
    @Pattern(regexp = "DEBIT|CREDIT", message = "正常余额方向非法")
    private String normalSide;

    /**
     * 是否末级可记账
     * <li>true 为末级科目，false 为汇总科目；创建后不可修改。</li>
     */
    @NotNull
    private Boolean postingAllowed;

    /**
     * 上级科目编号
     * <li>为空表示一级科目；必须是同表、启用、类别相同的汇总科目，否则 CHART_PARENT_INVALID。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{3}([0-9]{2}){0,2}", message = "上级科目编号必须为 4、6 或 8 位数字")
    private String parentAccountNo;
}
