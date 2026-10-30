package com.paytech.platform.support.dataquery.service.processor.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

import tools.jackson.databind.JsonNode;

/**
 * 模块内调用方按模板编码执行当前 ACTIVE 版本的请求；不含会话与 HTTP 调用方，访问控制由调用方负责。
 * <li>分页字段供 HTTP 执行入口委托时原样传入；工作流只取单行，传 null / false；联合查询只取第一页并传 rowCap。</li>
 */
@Getter
@AllArgsConstructor
public class RunDataQueryTemplateRequest {

    /** 模板编码。 */
    @NotBlank
    @Size(max = 64)
    private final String templateCode;

    /** 参数 JSON 对象，可为 null。 */
    private final JsonNode params;

    /** OFFSET 页码，可为 null（NONE 分页的模板忽略）。 */
    private final Integer page;

    /** OFFSET、CURSOR 页大小，可为 null。 */
    private final Integer size;

    /** 是否返回总数。 */
    private final boolean withTotal;

    /** CURSOR 游标，可为 null。 */
    @Size(max = 4096)
    private final String cursor;

    /**
     * 操作人账号 ID
     * <li>可为 null（工作流系统检查固定为 null，结果不随触发人变化）；模板引用 __operator.accountId 时得到 null。</li>
     */
    private final Long operatorAccountId;

    /**
     * 调用方的单次超时上限秒数
     * <li>1—8；为 null 时只取模板与系统上限。</li>
     */
    @Min(1)
    @Max(8)
    private final Integer timeoutCapSeconds;

    /** 调用方标签，只用于日志与指标，如 workflow。 */
    @NotBlank
    @Size(max = 64)
    private final String callerLabel;

    /**
     * 调用方的单次行数上限
     * <li>为 null 时不额外限制。NONE 分页取 min(模板 maxRows, 系统上限, 本值)；OFFSET / CURSOR 未传 size 时，
     * 页大小取 min(模板 maxPageSize, 系统上限, 本值)。联合查询用它把每个模板限制在单模板行数上限内。</li>
     */
    @Min(1)
    private final Integer rowCap;
}
