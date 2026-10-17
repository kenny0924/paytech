package com.paytech.operations.console.bff.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleListItem;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleOverview;

import lombok.AllArgsConstructor;

/** 仅为当前角色页批量补充员工数，保留 Platform 分页与授权事实。 @author Codex */
@Component
@AllArgsConstructor
public final class RoleListProcessor {
    private final PlatformOperationsClient client;

    public PagingResponseBody<RoleListItem> list(OperatorContext operator, int page, int size) {
        PagingResponseBody<RoleOverview> roles = client.roles(operator, page, size);
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
