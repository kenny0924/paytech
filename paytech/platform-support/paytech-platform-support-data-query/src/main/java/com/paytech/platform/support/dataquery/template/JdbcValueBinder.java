package com.paytech.platform.support.dataquery.template;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.paytech.foundation.common.error.BizCheckFailException;

/** JDBC 值绑定：标量输出 ?，集合输出 ?, ?, ?，值只进入绑定列表。 */
public final class JdbcValueBinder implements ValueBinder {

    private static final String PLACEHOLDER = "?";

    /**
     * 输出占位符并追加绑定值。
     *
     * @param value 标量、null 或标量集合
     * @param bindings 绑定值列表
     * @return 占位符文本
     * @throws BizCheckFailException 空集合、对象或对象集合直接写入 #{}
     */
    @Override
    public String bind(Object value, List<Object> bindings) {
        if (value instanceof Collection<?> collection) {
            return bindCollection(collection, bindings);
        }
        requireScalar(value);
        bindings.add(value);
        return PLACEHOLDER;
    }

    /**
     * 集合逐个元素绑定。
     *
     * @param collection 集合
     * @param bindings 绑定值列表
     * @return 以逗号分隔的占位符
     */
    private static String bindCollection(Collection<?> collection, List<Object> bindings) {
        if (collection.isEmpty()) {
            throw new BizCheckFailException("集合为空，不能直接写入 #{}，请用 {{#if}} 包住或改用 {{#each}}");
        }
        StringJoiner placeholders = new StringJoiner(", ");
        for (Object element : collection) {
            if (element instanceof Map<?, ?>) {
                throw new BizCheckFailException("对象集合不能直接写入 #{}，请用 {{#each}} 逐个取字段");
            }
            bindings.add(element);
            placeholders.add(PLACEHOLDER);
        }
        return placeholders.toString();
    }

    /**
     * 拒绝对象与片段直接绑定。
     *
     * @param value 值
     */
    private static void requireScalar(Object value) {
        if (value instanceof Map<?, ?>) {
            throw new BizCheckFailException("对象不能直接写入 #{}，请写到具体字段");
        }
        if (value instanceof SelectedFragment) {
            throw new BizCheckFailException("片段参数不能写入 #{}，请用 @{}");
        }
    }
}
