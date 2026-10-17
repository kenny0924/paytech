package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import jakarta.validation.constraints.*;

/** Scope 定义管理；登记目录不会自动授予任何凭证权限。 */
public final class ApiScopeManagementModels {
    /** 只承载类型或静态转换，禁止创建无业务意义的实例。 */
    private ApiScopeManagementModels() {}

    /** 全局 Scope 目录摘要，没有启停状态。
     * @param apiScopeId Scope 目录主键，正十进制字符串。
     * @param scopeCode 不可更改的小写 ASCII 能力编码。
     * @param displayName 可编辑展示名称，不参与认证。
     * @param description 可选业务说明，未填写时为 null。
     * @param version 行级并发版本，不表示历史业务版本。
     */
    public record ApiScopeSummary(
        String apiScopeId,
        String scopeCode,
        String displayName,
        String description,
        int version) {}
    /** Scope 定义详情，引用查询由 BFF 按权限组合。
     * @param apiScopeId Scope 目录主键，正十进制字符串。
     * @param scopeCode 不可更改的小写 ASCII 能力编码。
     * @param displayName 可编辑展示名称，不参与认证。
     * @param description 可选业务说明，未填写时为 null。
     * @param version 行级并发版本，不表示历史业务版本。
     * @param createTime UTC 微秒创建时间。
     * @param updateTime UTC 微秒更新时间。
     */
    public record ApiScopeDetailResponseBody(
        String apiScopeId,
        String scopeCode,
        String displayName,
        String description,
        int version,
        Instant createTime,
        Instant updateTime) {}
    /** 路由选择器投影，不包含凭证或引用数量。
     * @param scopeCode 选择后仍保存该精确编码。
     * @param displayName 目录当前显示名称。
     */
    public record ApiScopeOption(
        String scopeCode,
        String displayName) {}
    /** 新增全局目录定义，编码不可回收复用。
     * @param scopeCode 精确小写 ASCII 编码，不自动 trim 或改大小写。
     * @param displayName 非空展示名称，去除首尾空白。
     * @param description 可选能力说明，null 或空白表示不填写。
     * @param reason 本次新增原因。
     */
    public record CreateApiScopeRequestBody(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[a-z][a-z0-9]*(?:[.:-][a-z0-9]+)*") String scopeCode,
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 500) String reason) {}
    /** 更新名称与说明，不接受编码或状态修改。
     * @param displayName 非空展示名称，去除首尾空白。
     * @param description 可选能力说明，null 或空白表示不填写。
     * @param expectedVersion 必须匹配当前目录行版本。
     * @param reason 本次修改原因。
     */
    public record UpdateApiScopeRequestBody(
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 500) String description,
        @Positive int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}
    /** 稳定写入结果，不伪造 ACTIVE 状态。
     * @param apiScopeId 目录主键。
     * @param scopeCode 不变的目录编码。
     * @param version 当次操作结果版本。
     */
    public record ApiScopeMutationResponseBody(
        String apiScopeId,
        String scopeCode,
        int version) {}
}
