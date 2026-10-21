package com.paytech.reconciliation.test.builder;

import java.util.function.Consumer;

/**
 * 本库 DataObject 测试 Builder 的公共骨架：子类 template() 给出最小合法默认对象，用例用 {@link #with} 覆盖与场景有关的字段
 * <li>只构造对象，不访问数据库、Redis、MQ 或网络；build() 不修复用例刻意设置的非法值（负例原样落库或原样提交）。</li>
 *
 * @param <T> DataObject 类型
 * @param <B> 具体 Builder 类型
 */
public abstract class DataObjectBuilder<T, B extends DataObjectBuilder<T, B>> {
    /** 构造中的对象。 */
    protected final T target;

    /** 以已填好默认值的对象构造。 */
    protected DataObjectBuilder(T target) {
        this.target = target;
    }

    /** 覆盖任意字段（可设为 null 或非法值构造负例）。 */
    public B with(Consumer<T> change) {
        change.accept(target);
        return self();
    }

    /** 返回构造结果；同一 Builder 只调用一次。 */
    public T build() {
        return target;
    }

    /** 子类返回自身，支持链式调用。 */
    protected abstract B self();
}
