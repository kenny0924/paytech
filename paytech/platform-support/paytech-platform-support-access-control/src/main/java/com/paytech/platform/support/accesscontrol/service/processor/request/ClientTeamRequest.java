package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作区团队管理内部请求
 * <li>按操作使用其中的字段：列表用 status、paging；按 ID 操作用 targetId（成员或邀请）；发起邀请用 email、roleCode；
 * 调整角色用 targetId、roleCode。</li>
 */
@Getter
@AllArgsConstructor
public final class ClientTeamRequest {
    /** 可信账号编号。 */
    private final long accountId;
    /** 可信会话编号。 */
    private final long sessionId;
    /** 目标成员或邀请；列表与发起邀请为空。 */
    private final Long targetId;
    /** 受邀邮箱原值；只用于发起邀请。 */
    private final String email;
    /** 角色代码。 */
    private final String roleCode;
    /** 邀请列表的显示状态筛选；可空。 */
    private final String status;
    /** 分页参数；非列表为空。 */
    private final PagingRequestBody paging;

    /** 不输出邮箱或会话坐标。 */
    @Override public String toString() { return "ClientTeamRequest[REDACTED]"; }
}
