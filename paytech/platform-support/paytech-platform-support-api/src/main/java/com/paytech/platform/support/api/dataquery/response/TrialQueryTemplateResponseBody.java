package com.paytech.platform.support.api.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 试运行结果：与执行相同的结果，另附实际执行的 SQL 与绑定值。 */
@Getter
@Setter
public class TrialQueryTemplateResponseBody {

    /** 实际执行的模板版本号；试运行为 0。 */
    private int versionNo;

    /** 结果列，按查询列顺序，已去掉 HIDE 列。 */
    private List<DataQueryColumn> columns;

    /**
     * 数据行
     * <li>每行与 columns 一一对应；整数、DECIMAL、浮点、日期时间与其他类型为字符串，布尔为 true/false，NULL 为 null，二进制为 "[binary]"。</li>
     * <li>日期时间为不带时区的 ISO-8601，原样反映库中的值。屏蔽列按规则替换。</li>
     */
    private List<List<Object>> rows;

    /** 是否还有更多行：超出本次行数上限或页大小时为 true。 */
    private boolean hasMore;

    /** 总数；只有 OFFSET 且 withTotal=true 时返回，其余为 null。 */
    private Long total;

    /** 下一页游标；只有 CURSOR 且 hasMore=true 时返回，其余为 null。 */
    private String nextCursor;

    /** 数据库执行耗时毫秒（含计数）。 */
    private long elapsedMillis;

    /** 实际执行的主查询 SQL，值以 ? 表示，已追加分页子句。 */
    private String sql;

    /** 主查询绑定值，与 sql 中 ? 顺序一致，统一以字符串显示，null 为 null。 */
    private List<String> bindings;

    /** 计数 SQL；未计数时为 null。 */
    private String countSql;
}
