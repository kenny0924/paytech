package com.paytech.platform.support.dataquery.template;

import java.util.List;

/** 引擎对渲染结果的校验（JDBC 为只读 SQL 校验），保存时对每个展开组合调用，不访问数据源。 */
@FunctionalInterface
public interface RenderedQueryCheck {

    /**
     * 校验渲染结果。
     *
     * @param rendered 渲染结果
     * @return 拒绝原因；通过时为空列表
     */
    List<RenderedQueryViolation> inspect(RenderedQuery rendered);
}
