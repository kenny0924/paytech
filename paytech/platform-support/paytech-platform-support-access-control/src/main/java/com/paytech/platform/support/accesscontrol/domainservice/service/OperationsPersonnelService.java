package com.paytech.platform.support.accesscontrol.domainservice.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.paytech.platform.support.accesscontrol.domain.operations.OperationsPersonnelCandidate;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsPersonnelInfo;

/** OPERATIONS 人员的窄查询，供工作流核对执行人、审批人与显示名；只读本库。 */
public interface OperationsPersonnelService {

    /**
     * 按账号批量读取人员信息；不存在或不是 OPERATIONS 人员账号的 ID 不在结果中。
     *
     * @param accountIds 账号
     * @return 账号到人员信息
     */
    Map<Long, OperationsPersonnelInfo> personnel(Collection<Long> accountIds);

    /**
     * 选人：ACTIVE 的 OPERATIONS 人员，按登录名排序。
     *
     * @param fragment 关键字：登录名前缀或脱敏邮箱片段；为空时不过滤
     * @param emailToken 登录邮箱稳定 Token；非空时只按它精确匹配（调用方在事务外取得）
     * @param limit 最多条数
     * @return 候选人
     */
    List<OperationsPersonnelCandidate> searchActive(String fragment, String emailToken, int limit);
}
