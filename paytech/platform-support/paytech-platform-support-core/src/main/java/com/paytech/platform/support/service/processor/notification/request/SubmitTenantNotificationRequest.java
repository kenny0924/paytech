package com.paytech.platform.support.service.processor.notification.request;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 按租户权限解析收件人的普通通知提交
 * <li>notification 携带来源、模板、语言与参数，收件人为空，由 Processor 解析后填入；其字段约束由 delivery 入口校验。</li>
 */
@Getter
@Setter
public class SubmitTenantNotificationRequest {

    /** 不含收件人的普通通知提交；来源系统取已授权的调用方服务名。 */
    @NotNull
    private SubmitNotificationRequest notification;

    /** 租户类型：CLIENT 或 PARTNER。 */
    @NotNull
    private AccessTenantType tenantType;

    /** 租户所有者引用：Client ID 或 Local Partner ID。 */
    @Positive
    private long ownerRefId;

    /** 收件人须持有的权限码。 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private String permissionCode;

    /** 全部解析出的收件人共用的语言覆盖；为空时使用默认语言。 */
    @Size(max = 35)
    private String locale;
}
