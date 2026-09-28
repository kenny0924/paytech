package com.paytech.platform.support.ext.rest.configurationmanagement;

import java.util.List;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.Capabilities;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceView;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.service.processor.request.ChangeConsoleResourceStatusRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.CreateConsoleResourceRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetConsoleResourceRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListConsoleResourcesRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryConsoleResourceCapabilitiesRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.UpdateConsoleResourceRequest;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection;
import com.paytech.platform.support.api.consolemanagement.request.ChangeConsoleResourceStatusRequestBody;
import com.paytech.platform.support.api.consolemanagement.request.CreateConsoleResourceRequestBody;
import com.paytech.platform.support.api.consolemanagement.request.UpdateConsoleResourceRequestBody;
import com.paytech.platform.support.api.consolemanagement.response.ActionCapability;
import com.paytech.platform.support.api.consolemanagement.response.ComponentCapability;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourceCapabilitiesResponseBody;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourceResponseBody;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourcesResponseBody;
import com.paytech.platform.support.api.enums.OperationsConsoleResourceType;

/** 菜单管理的协议规范化和结果投影，不查询数据库或执行业务授权。 */
@Component
public class ConsoleResourceManagementConverter {
    /** 完整树查询只接受可信身份。 */
    public ListConsoleResourcesRequest resources(String actor, String session) {
        return new ListConsoleResourcesRequest(actor, session);
    }

    /** 规范化路径编码，保留原 NOT_FOUND 可见性边界。 */
    public GetConsoleResourceRequest resource(String actor, String session, String code) {
        return new GetConsoleResourceRequest(actor, session, ConfigurationInputs.code(code, 128));
    }

    /** 部署能力查询仍要求资源读取权限。 */
    public QueryConsoleResourceCapabilitiesRequest capabilities(String actor, String session) {
        return new QueryConsoleResourceCapabilitiesRequest(actor, session);
    }

    /** 规范化人类文本、枚举和主键，不接受浏览器指定前端路径。 */
    public CreateConsoleResourceRequest create(String actor, String session, CreateConsoleResourceRequestBody body) {
        return new CreateConsoleResourceRequest(actor, session, ConfigurationInputs.code(body.getResourceCode(), 128),
            ConfigurationInputs.enumeration(OperationsConsoleResourceType.class, body.getResourceType()),
            ConfigurationInputs.text(body.getDisplayName(), 255, "displayName"), ConfigurationInputs.id(body.getParentResourceId()),
            component(body.getComponentKey()), permissionId(body.getPermissionId()), body.getSortOrder(), reason(body.getReason()));
    }

    /** 更新只接收原合同允许的可变字段和期望版本。 */
    public UpdateConsoleResourceRequest update(String actor, String session, String code, UpdateConsoleResourceRequestBody body) {
        return new UpdateConsoleResourceRequest(actor, session, ConfigurationInputs.code(code, 128),
            ConfigurationInputs.text(body.getDisplayName(), 255, "displayName"), ConfigurationInputs.id(body.getParentResourceId()),
            component(body.getComponentKey()), permissionId(body.getPermissionId()), body.getSortOrder(), body.getExpectedVersion(),
            reason(body.getReason()));
    }

    /** 启停不接受子项或其他资源字段。 */
    public ChangeConsoleResourceStatusRequest status(String actor, String session, String code, ChangeConsoleResourceStatusRequestBody body) {
        return new ChangeConsoleResourceStatusRequest(actor, session, ConfigurationInputs.code(code, 128),
            ConfigurationInputs.enumeration(GatewayConfigurationStatus.class, body.getStatus()), body.getExpectedVersion(), reason(body.getReason()));
    }

    /** 列表沿用数据库排序和原 items envelope。 */
    public ConsoleResourcesResponseBody resources(List<ConsoleResourceView> resources) {
        return new ConsoleResourcesResponseBody(resources.stream().map(this::resource).toList());
    }

    /** 主键转为字符串，闭合枚举转为 Code，不泄露持久化对象。 */
    public ConsoleResourceResponseBody resource(ConsoleResourceView row) {
        var protection = row.getManagementProtection();
        return new ConsoleResourceResponseBody(Long.toString(row.getResourceId()), row.getResourceCode(), row.getChannel().getCode(),
            row.getResourceType().getCode(), row.getParentResourceId() == null ? null : row.getParentResourceId().toString(),
            row.getDisplayName(), row.getFrontendRoute(), row.getComponentKey(), Long.toString(row.getPermissionId()), row.getPermissionCode(),
            row.getPermissionStatus() == null ? null : row.getPermissionStatus().getCode(), row.getSortOrder(), row.getStatus().getCode(),
            row.getVersion(), new ManagementProtection(protection.editableFields(), protection.statusChangeAllowed(), protection.reason()),
            row.isSupported(), row.getCreateTime(), row.getUpdateTime());
    }

    /** 输出当前制品的部署能力，能力清单仍由原组件加载。 */
    public ConsoleResourceCapabilitiesResponseBody capabilities(Capabilities value) {
        return new ConsoleResourceCapabilitiesResponseBody(value.components().stream().map(c ->
            new ComponentCapability(c.componentKey(), c.displayName(), c.frontendRoute(), c.actions().stream()
                .map(a -> new ActionCapability(a.resourceCode(), a.displayName())).toList())).toList(),
            value.resourceTypes(), value.maxDepth(), value.maxResources());
    }

    /** 保存回执只投影当前业务结果，不查询或恢复请求重放记录。 */
    public ConfigurationMutationResponseBody mutation(ConfigurationMutation result) {
        return new ConfigurationMutationResponseBody(result.objectType(), Long.toString(result.objectId()),
            result.objectCode(), result.status(), result.version());
    }

    /** 必需权限 ID 不允许通过空值绕过引用校验。 */
    long permissionId(String value) {
        Long id = ConfigurationInputs.id(value);
        if (id == null) { throw new InvalidParameterException("permissionId 必填"); }
        return id;
    }

    /** 空白组件表示目录；非空编码保持原值。 */
    String component(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 操作原因只去除首尾空白并保持原长度限制。 */
    String reason(String value) {
        return ConfigurationInputs.text(value, 500, "reason");
    }
}
