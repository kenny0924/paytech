package com.paytech.platform.support.ext.rest.configurationmanagement;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionDetailView;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.accesscontrol.service.processor.request.*;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection;
import com.paytech.platform.support.api.permissionmanagement.request.ChangePermissionStatusRequestBody;
import com.paytech.platform.support.api.permissionmanagement.request.CreatePermissionRequestBody;
import com.paytech.platform.support.api.permissionmanagement.request.UpdatePermissionRequestBody;
import com.paytech.platform.support.api.permissionmanagement.response.*;

/** 权限管理协议转换；字段约束交由公共 Processor，业务规则留在 Owner。 */
@Component
public class PermissionManagementConverter {
    /** 解析分页默认值与闭合枚举，空白筛选保持缺失语义。 */
    public ListPermissionsRequest list(String actor, String session, Integer page, Integer size, String code,
        String resourceType, String status, String riskLevel) {
        return new ListPermissionsRequest(actor, session, PagingRequestBody.of(page, size), empty(code),
            empty(resourceType),
            status == null ? null : ConfigurationInputs.enumeration(PermissionStatus.class, status),
            riskLevel == null ? null : ConfigurationInputs.enumeration(OperationsPermissionRiskLevel.class, riskLevel));
    }

    /** 路径编码由内部 Request 注解校验。 */
    public GetPermissionRequest get(String actor, String session, String code) {
        return new GetPermissionRequest(actor, session, code);
    }

    /** 引用页只接受一个闭合类别。 */
    public ListPermissionReferencesRequest references(String actor, String session, String code, String kind,
        Integer page, Integer size) {
        return new ListPermissionReferencesRequest(actor, session, code,
            ConfigurationInputs.enumeration(ConfigurationCatalog.ReferenceKind.class, kind),
            PagingRequestBody.of(page, size));
    }

    /** 使用场景的实际权限判断由 Processor 完成。 */
    public ListPermissionOptionsRequest options(String actor, String session, String usage, Integer page, Integer size,
        String keyword) {
        return new ListPermissionOptionsRequest(actor, session, usage, PagingRequestBody.of(page, size),
            empty(text(keyword)));
    }

    /** 创建不接收状态或持有人信息。 */
    public CreatePermissionRequest create(String actor, String session, CreatePermissionRequestBody body) {
        return new CreatePermissionRequest(actor, session, body.getPermissionCode(), body.getResourceType(),
            body.getActionCode(),
            ConfigurationInputs.enumeration(OperationsPermissionRiskLevel.class, body.getRiskLevel()),
            text(body.getReason()));
    }

    /** 编辑只允许风险等级及原版本。 */
    public UpdatePermissionRequest update(String actor, String session, String code, UpdatePermissionRequestBody body) {
        return new UpdatePermissionRequest(actor, session, code,
            ConfigurationInputs.enumeration(OperationsPermissionRiskLevel.class, body.getRiskLevel()),
            body.getExpectedVersion(), text(body.getReason()));
    }

    /** 启停只转换目标状态，维护边界在当前事实读取后判断。 */
    public ChangePermissionStatusRequest status(String actor, String session, String code,
        ChangePermissionStatusRequestBody body) {
        return new ChangePermissionStatusRequest(actor, session, code,
            ConfigurationInputs.enumeration(PermissionStatus.class, body.getStatus()), body.getExpectedVersion(),
            text(body.getReason()));
    }

    /** 权限主键转换为十进制字符串，枚举输出稳定 Code。 */
    public PermissionSummary
        summary(com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionSummary value) {
        return new PermissionSummary(Long.toString(value.getPermissionId()), value.getPermissionCode(),
            value.getResourceType(), value.getActionCode(), value.getRiskLevel().getCode(), value.getStatus().getCode(),
            value.getVersion());
    }

    /** 选择器不公开状态、版本或授权对象。 */
    public PermissionOption
        option(com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionSummary value) {
        return new PermissionOption(Long.toString(value.getPermissionId()), value.getPermissionCode(),
            value.getResourceType(), value.getActionCode(), value.getRiskLevel().getCode());
    }

    /** 组合查询只投影合同字段，不向 HTTP 暴露 DataObject。 */
    public PermissionResponseBody detail(PermissionDetailView value) {
        var protection = value.getManagementProtection();
        var counts = value.getReferenceCounts();
        return new PermissionResponseBody(
            Long.toString(value.getPermissionId()), value.getPermissionCode(), value.getResourceType(),
            value.getActionCode(), value.getRiskLevel().getCode(), value.getStatus().getCode(), value.getVersion(),
            value.getCreateTime(), value.getUpdateTime(), new ManagementProtection(protection.editableFields(),
                protection.statusChangeAllowed(), protection.reason()),
            new ReferenceCounts(counts.resource(), counts.route(), counts.role()));
    }

    /** 每页返回对应的明确引用类型，保持原 JSON 字段。 */
    public PermissionReference reference(ConfigurationCatalog.Reference value) {
        return switch (value) {
            case ConfigurationCatalog.ResourceReference r -> new ResourceReference(Long.toString(r.resourceId()),
                r.resourceCode(), r.displayName(), r.channel(), r.resourceType(), r.status());
            case ConfigurationCatalog.RouteReference r -> new RouteReference(r.routeCode(), r.channel(), r.httpMethod(),
                r.externalPathTemplate(), r.targetServiceCode(), r.status());
            case ConfigurationCatalog.RoleReference r -> new RoleReference(Long.toString(r.roleId()), r.roleCode(),
                r.roleName(), r.roleStatus(), r.grantStatus(), r.scopeKey());
        };
    }

    /** 返回本次保存结果，不重新读取可变目录。 */
    public ConfigurationMutationResponseBody mutation(ConfigurationMutation result) {
        return new ConfigurationMutationResponseBody(result.objectType(), Long.toString(result.objectId()),
            result.objectCode(), result.status(), result.version());
    }

    private String empty(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String text(String value) {
        return value == null ? null : value.strip();
    }
}
