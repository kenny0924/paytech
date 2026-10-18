package com.paytech.operations.console.bff.service.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleListItem;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleOverview;
import com.paytech.operations.console.bff.service.processor.request.RoleListRequest;

import lombok.AllArgsConstructor;

/**
 * 角色列表页：为当前页角色批量补充员工数
 * <li>保留 Platform 的分页与授权事实；空页不查询员工数。</li>
 * <li>当前页角色 ID 重复，或员工数回执与当前页角色集合不一致，视为上游无效响应。</li>
 *
 * @author Codex
 */
@Component
@AllArgsConstructor
public class RoleListProcessor extends AbstractProcessor<RoleListRequest, PagingResponseBody<RoleListItem>> {
    private final PlatformOperationsClient client;

    @Override
    protected PagingResponseBody<RoleListItem> processRequest(RoleListRequest request) {
        var operator = request.getOperator();
        PagingResponseBody<RoleOverview> roles = client.roles(operator, request.getPaging().page(), request.getPaging().size());
        if (roles.items().isEmpty()) {
            return new PagingResponseBody<>(List.of(), roles.page(), roles.size(), roles.total());
        }
        List<String> ids = roles.items().stream().map(RoleOverview::roleId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        Map<String, Long> counts = client.roleEmployeeCounts(operator, ids);
        if (!counts.keySet().equals(new HashSet<>(ids))) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return roles.map(role -> new RoleListItem(role.roleId(), role.roleCode(), role.roleName(), role.systemRole(),
            role.version(), counts.get(role.roleId())));
    }
}
