package com.paytech.platform.support.accesscontrol.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** 权限停用保留引用，恢复后按当前关联重新鉴权。 */
public enum PermissionStatus implements CodeEnum {
    ACTIVE,

    INACTIVE;

    @Override
    public String getCode() { return name(); }
}
