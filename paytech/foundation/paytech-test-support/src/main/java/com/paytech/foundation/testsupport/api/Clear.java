package com.paytech.foundation.testsupport.api;

/** 测试组件的清理能力；实现只回收自己拥有的数据、配置和调用记录。 */
@FunctionalInterface
public interface Clear {

    /** 每例结束清理全部自有状态；空状态可重复清理，失败直接交由测试报告。 */
    void clear();
}
