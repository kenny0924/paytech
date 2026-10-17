package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Scope 目录当前事实，编码不可修改，不承载凭证授权。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiScope {

    /** 目录 Snowflake 标识，与授权明细主键不同。 */
    private Long apiScopeId;

    /** 小写 ASCII 精确目录编码。 */
    private String scopeCode;

    /** 可修改的展示名称。 */
    private String displayName;

    /** 可选说明，null 表示未填写。 */
    private String description;

    /** 目录行并发版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;

    /** 拒绝缺失身份、版本或名称的损坏记录，不推测缺失事实。 */
    public void validateForManagement() {
        if (apiScopeId == null || apiScopeId <= 0 || version <= 0 || displayName == null) {
            throw new ErrorException("Scope 目录记录不完整");
        }
    }
}
