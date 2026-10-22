package com.paytech.platform.support.api.dataquery.request;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 模板约束（constraints_json）；取值不得超过 data-query.limits 硬上限，保存时校验。 */
@Getter
@Setter
public class QueryTemplateConstraints {

    /**
     * 最多返回的行数
     * <li>1..max-rows（默认 10000）；NONE 方式下多取一行判断 hasMore。</li>
     */
    @NotNull
    @Min(1)
    private Integer maxRows;

    /**
     * 单次查询超时秒数
     * <li>1..max-timeout（默认 8 秒，低于 BFF 9 秒与 Gateway 10 秒的响应超时）；超时后驱动发送 KILL QUERY，返回 DATA_QUERY_TIMEOUT。</li>
     */
    @NotNull
    @Min(1)
    private Integer timeoutSeconds;

    /**
     * 分页方式
     * <li>NONE、OFFSET、CURSOR。正文不能带顶层 LIMIT，分页子句由服务追加。</li>
     */
    @NotBlank
    @Pattern(regexp = "NONE|OFFSET|CURSOR", message = "pagingMode 只能为 NONE、OFFSET 或 CURSOR")
    private String pagingMode;

    /**
     * 页大小上限
     * <li>OFFSET、CURSOR 必填，1..maxRows；NONE 时忽略。</li>
     */
    @Min(1)
    private Integer maxPageSize;

    /**
     * 是否允许返回总数
     * <li>只对 OFFSET 有效；为 true 时请求 withTotal=true 另执行一次 COUNT。缺省 false。</li>
     */
    private boolean countAllowed;

    /**
     * 游标列
     * <li>CURSOR 必填，1..8 个结果列名；下一页的 __cursor.列名 取上一页最后一行的这些列值。</li>
     */
    @Size(max = 8)
    private List<@NotBlank @Size(max = 128) String> cursorColumns;
}
