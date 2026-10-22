package com.paytech.operations.console.bff.service.model.dataquery.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 出参列声明（columns_json 的一项）；没有声明的列原样返回，标题使用列名。 */
@Getter
@Setter
public class QueryTemplateColumn {

    /** 结果列名（列标签），1..128 字符，不区分大小写匹配。 */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** 显示标题，可选，最多 128 字符；为空时使用列名。 */
    @Size(max = 128)
    private String label;

    /**
     * 屏蔽规则
     * <li>NONE：原样；HIDE：不返回该列；FULL：全部替换为 *；KEEP_EDGES：保留首 keepStart 位与尾 keepEnd 位。缺省 NONE。</li>
     * <li>屏蔽只作用于非 null 值，结果以字符串返回。</li>
     */
    @Pattern(regexp = "NONE|HIDE|FULL|KEEP_EDGES", message = "mask 只能为 NONE、HIDE、FULL 或 KEEP_EDGES")
    private String mask;

    /** KEEP_EDGES 保留的开头字符数，0..64，缺省 0。 */
    @Min(0)
    private Integer keepStart;

    /** KEEP_EDGES 保留的结尾字符数，0..64，缺省 0。 */
    @Min(0)
    private Integer keepEnd;
}
