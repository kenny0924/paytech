package com.paytech.channel.connector.api.externalapi;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.StandardApiResponseBody;
import com.paytech.channel.connector.api.externalapi.response.StandardApiSummary;

import jakarta.validation.Valid;

/**
 * 标准 API 的 Operations 管理契约（E01～E05）；只由 Operations BFF 经私网调用，请求幂等归 Gateway。
 * <li>2026-10-20 自 Platform 迁入 channel-connector，路径、JSON 字段与状态码不变。</li>
 * <li>写命令必带 {@value com.paytech.channel.connector.api.ChannelConnectorHeaders#ACTOR_HEADER}（规范正十进制 Account ID），查询与静态校验不带；权限由 Gateway Route 与 BFF 实时授权负责。</li>
 */
@HttpExchange("/external-api-admin/v1")
public interface StandardApiManagementApi {

    /**
     * 分页查询标准 API，按编码稳定排序。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，最大 100
     * @param keyword 可选编码或名称包含查询，1～64 字符，通配符按文字处理
     * @return 标准 API 列表项与总数
     */
    @GetExchange("/standard-apis")
    PagingResponseBody<StandardApiSummary> standardApis(
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * 读取标准 API 详情，含字段与实现方。
     *
     * @param standardApiCode 精确编码，不存在时返回 404
     * @return 标准 API 详情
     */
    @GetExchange("/standard-apis/{standardApiCode}")
    StandardApiResponseBody standardApi(
        @PathVariable("standardApiCode") String standardApiCode);

    /**
     * 创建标准 API 及其字段。
     *
     * @param actor 操作者 Account ID（{@value ChannelConnectorHeaders#ACTOR_HEADER}，规范正十进制），缺失或格式不符 400
     * @param request 定义及原因；编码重复返回 400，字段静态校验失败返回 422 及 errors[]
     * @return 编码与初始版本 1
     */
    @PostExchange("/standard-apis")
    ExternalApiMutationResponseBody createStandardApi(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actor,
        @Valid @RequestBody CreateStandardApiRequestBody request);

    /**
     * 按当前版本整体修改名称、说明与字段。
     *
     * @param actor 操作者 Account ID（{@value ChannelConnectorHeaders#ACTOR_HEADER}，规范正十进制），缺失或格式不符 400
     * @param standardApiCode 精确编码，不存在时返回 404
     * @param request 修改内容；版本不一致返回 409
     * @return 保存后的版本
     */
    @PutExchange("/standard-apis/{standardApiCode}")
    ExternalApiMutationResponseBody updateStandardApi(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actor,
        @PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody UpdateStandardApiRequestBody request);

    /**
     * 按当前版本物理删除标准 API 及其字段。
     *
     * @param actor 操作者 Account ID（{@value ChannelConnectorHeaders#ACTOR_HEADER}，规范正十进制），缺失或格式不符 400
     * @param standardApiCode 精确编码，不存在时返回 404
     * @param request 期望版本与原因；仍被接入方实现时返回 400
     * @return 删除前的版本
     */
    @DeleteExchange("/standard-apis/{standardApiCode}")
    ExternalApiMutationResponseBody deleteStandardApi(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actor,
        @PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request);
}
