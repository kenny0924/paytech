package com.paytech.platform.support.dataquery.domainservice.service;

import com.paytech.platform.support.dataquery.domain.JointQueryGraph;
import com.paytech.platform.support.dataquery.domain.JointQuerySettings;

/**
 * 联合查询的只读加载：每次请求现读键类型、全部模板与当前版本构图，不缓存（S-04）。
 * <li>只读 mor_platform、不开事务；允许观察相邻的已提交状态（一次构图内模板与版本可能来自不同提交时点，
 * 执行时按模板编码重新读取当前 ACTIVE 版本）。</li>
 */
public interface JointQueryGraphLoader {

    /**
     * 联合查询页初始化数据。
     *
     * @return 入口键类型、泳道分组与护栏
     */
    JointQuerySettings loadSettings();

    /**
     * 当前键类型图。
     *
     * @return 图
     */
    JointQueryGraph loadGraph();
}
