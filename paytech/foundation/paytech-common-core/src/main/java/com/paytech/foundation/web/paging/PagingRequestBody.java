package com.paytech.foundation.web.paging;

import com.paytech.foundation.common.error.InvalidParameterException;
import java.util.List;

/**
 * 已校验的分页参数，统一采用从 1 开始的页码和固定的每页条数选项。
 *
 * <p>入口通过 {@link #of(Integer, Integer)} 为未提供的参数补默认值；显式非法值直接拒绝，
 * 不截断页码、不修正条数。此类型不参与权限判断，也不执行数据查询。
 *
 * @param page 请求页码，范围为 1 到 100000
 * @param size 每页条数，仅允许 10、20、50、100 或 200
 * @author Codex
 */
public record PagingRequestBody(int page, int size) {

    /** 未提供页码时查询第一页。 */
    public static final int DEFAULT_PAGE = 1;

    /** 未提供每页条数时查询 10 条。 */
    public static final int DEFAULT_SIZE = 10;

    /** 允许的最大页码，限制深度分页及偏移量。 */
    public static final int MAX_PAGE = 100_000;

    /** 按显示顺序排列的每页条数选项，不允许调用方修改。 */
    public static final List<Integer> PAGE_SIZES = List.of(10, 20, 50, 100, 200);

    /**
     * 校验明确提供的分页参数；失败使用公共 INVALID_REQUEST 类别。
     *
     * @throws InvalidParameterException 页码越界或每页条数不在允许列表中
     */
    public PagingRequestBody {
        if (page < DEFAULT_PAGE || page > MAX_PAGE) {
            throw new InvalidParameterException("页码必须在 1 到 100000 之间");
        }
        if (!PAGE_SIZES.contains(size)) {
            throw new InvalidParameterException("每页条数必须为 10、20、50、100 或 200");
        }
    }

    /**
     * 仅为缺失参数应用默认值，再执行统一校验。
     *
     * @param page 请求页码；null 表示使用默认第一页
     * @param size 每页条数；null 表示使用默认 10 条
     * @return 已校验且包含明确页码与条数的请求
     * @throws InvalidParameterException 明确提供的参数不合法
     */
    public static PagingRequestBody of(Integer page, Integer size) {
        return new PagingRequestBody(page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
    }

    /**
     * 计算数据库查询使用的零基偏移量，保留算术溢出检查。
     *
     * @return 第一页返回 0，当前参数边界下最大为 19999800
     */
    public int offset() {
        return Math.multiplyExact(page - 1, size);
    }
}
