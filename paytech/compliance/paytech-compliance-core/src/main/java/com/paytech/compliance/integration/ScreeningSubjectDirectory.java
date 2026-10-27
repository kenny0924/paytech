package com.paytech.compliance.integration;

import java.util.List;
import java.util.Map;

import com.paytech.compliance.domain.ScreeningSubject;

/** 读取筛查所需主体名称的出站端口（client-master CM-13）；只能在数据库事务外调用。 */
public interface ScreeningSubjectDirectory {
    /**
     * 按资料 ID 读取筛查主体
     *
     * @param profileIds 资料 ID
     * @return 资料 ID 到主体；不存在或缺少名称的资料不在结果中
     *         <li>依赖不可用时抛出运行时异常，由调用方重试。</li>
     */
    Map<Long, ScreeningSubject> findByProfileIds(List<Long> profileIds);
}
