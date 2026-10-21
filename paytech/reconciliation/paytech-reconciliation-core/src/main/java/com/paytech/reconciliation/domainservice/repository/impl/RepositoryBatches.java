package com.paytech.reconciliation.domainservice.repository.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** 仓储批量读写的分段：IN 列表与多行语句每段至多 1,000 项，控制单条 SQL 的长度与参数个数。 */
final class RepositoryBatches {
    /** 每段项数上限。 */
    static final int SIZE = 1000;

    private RepositoryBatches() {
    }

    /**
     * 按段切分（保持原顺序）
     *
     * @param items 待切分项
     * @return 每段至多 {@link #SIZE} 项；空集合返回空列表
     */
    static <T> List<List<T>> partition(Collection<T> items) {
        List<T> all = List.copyOf(items);
        List<List<T>> parts = new ArrayList<>();
        for (int from = 0; from < all.size(); from += SIZE) {
            parts.add(all.subList(from, Math.min(all.size(), from + SIZE)));
        }
        return parts;
    }

    /**
     * 分段查询并按段顺序合并结果
     *
     * @param keys 查询键
     * @param query 一段键的查询
     * @return 合并后的结果；空键集合不查询
     */
    static <K, R> List<R> query(Collection<K> keys, Function<List<K>, List<R>> query) {
        List<R> result = new ArrayList<>();
        for (List<K> part : partition(keys)) {
            result.addAll(query.apply(part));
        }
        return result;
    }
}
