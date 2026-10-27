package com.paytech.compliance.domainservice.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.paytech.compliance.domain.ResolvedProgram;

/** 尽调程序的解析与读取（只读，不开启事务）。 */
public interface DueDiligenceProgramService {
    /**
     * 解析当前生效程序
     * <li>Owner 定制程序优先于平台程序，辖区精确匹配优先于全局程序；同一优先级多于一个、为空或版本未批准时抛出 503。</li>
     *
     * @param partyType ORGANIZATION 或 INDIVIDUAL
     * @param roleType CLIENT 或 RELATED_PERSON
     * @param tier 档次
     * @param jurisdictionCode 辖区代码
     * @param responsiblePartnerId 责任 PayFac；平台自营为空
     * @param now 当前时间
     * @return 程序版本及其模板
     */
    ResolvedProgram resolve(String partyType, String roleType, String tier, String jurisdictionCode, Long responsiblePartnerId,
        Instant now);

    /**
     * 按版本 ID 读取案件冻结的程序（不限当前状态）
     *
     * @param versionIds 版本 ID
     * @return 版本 ID 到程序；缺失的版本不在结果中
     */
    Map<Long, ResolvedProgram> load(Collection<Long> versionIds);

    /**
     * 运营程序目录（全部版本，不限状态）
     *
     * @param jurisdictionCode 可选辖区代码
     * @param status 可选程序状态
     * @return 程序版本及其模板，按辖区、程序代码、版本号倒序
     */
    List<ResolvedProgram> listCatalog(String jurisdictionCode, String status);
}
