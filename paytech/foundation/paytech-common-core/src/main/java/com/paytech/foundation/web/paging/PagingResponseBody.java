package com.paytech.foundation.web.paging;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 分页查询的正常响应，JSON 字段固定为 items、page、size 和 total。
 *
 * <p>只承载列表及分页元数据，不增加成功标记或通用响应外壳。列表使用不可变副本；
 * 元数据保持提供方原值，由查询入口及消费方协议边界分别校验。
 *
 * @param items 当前页按查询顺序排列的记录，允许空列表，不允许 null 列表或元素
 * @param page 从 1 开始的请求页码，空页也保留原请求页码
 * @param size 请求的每页条数，不以当前页实际记录数代替
 * @param total 相同查询条件下的总记录数，使用 long 避免总量截断
 * @param <T> 当前页记录的类型
 * @author Codex
 */
public record PagingResponseBody<T>(List<T> items, int page, int size, long total) {

    /** 创建列表快照，防止调用方后续修改集合而改变响应内容。 */
    public PagingResponseBody {
        items = List.copyOf(items);
    }

    /**
     * 按原顺序转换每条记录，完整保留页码、每页条数和总记录数。
     *
     * @param mapper 记录转换函数，不得返回 null
     * @param <R> 转换后的记录类型
     * @return 只替换列表元素类型的新分页响应
     */
    public <R> PagingResponseBody<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "分页记录转换函数不能为空");
        return new PagingResponseBody<>(items.stream().<R>map(mapper).toList(), page, size, total);
    }
}
