package com.paytech.channel.connector.api.cost;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.channel.connector.api.cost.request.CancelChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostPlanRequestBody;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.ReviewChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.SubmitChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostPlanResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostPlanSummaryResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostVersionResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 预计渠道成本方案与版本管理（Maker-Checker）
 * <li>每个 Payment Channel 一个方案；版本以完整适用范围与成本组成一次创建为 DRAFT 并冻结内容摘要，提交后由 Checker（≠ Maker）
 * 确认同一摘要后批准或驳回。</li>
 * <li>批准时已到生效时间则成为 ACTIVE 并原子切换当前版本，原当前版本 INACTIVE；未到生效时间保持 APPROVED。</li>
 * <li>范围 MID 必须是本库 t_channel_mid 中归属方案 Channel 且 entity_no 一致的记录；国家、币种、支付方式与动作类型只作匹配条件保存，
 * 不向 Payment 核对。</li>
 * <li>批准后未到生效时间的 APPROVED 版本由到期切换 Job 在生效时间到达后切换为当前版本；到期前可作废为 INACTIVE，作废原因写入变更记录。</li>
 * <li>channel-connector 内部开发接口：操作人标识由调用方传入，未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/channel-connector/v1/channel-cost-plans", accept = "application/json")
public interface ChannelCostPlanApi {

    /**
     * 创建渠道成本方案（DRAFT，无当前版本）
     *
     * @param body Channel 与方案代码
     * @return 201 与方案；Channel 不存在于本库、Channel 已有方案或方案代码已存在时 400；并发重复时 409
     */
    @PostExchange
    ResponseEntity<ChannelCostPlanResponseBody> createChannelCostPlan(
        @Valid @RequestBody CreateChannelCostPlanRequestBody body);

    /**
     * 创建渠道成本版本（DRAFT）
     *
     * @param planCode 方案代码
     * @param body 编制人、生效时间与完整适用范围及成本组成
     * @return 201 与版本详情，版本号为当前最大值 + 1，含内容摘要；金额小数位超过 8 位等字段格式错误时 422
     *         VALIDATION_ERROR；组成类型、计算基础或舍入方式代码不在目录时 400 INVALID_REQUEST；方案不存在、范围 MID 不存在或
     *         不属于方案 Channel / OE、范围或组成校验失败、已有 DRAFT 或 PENDING_APPROVAL 版本时 400 BUSINESS_CHECK_FAILED；
     *         并发写入时 409
     */
    @PostExchange("/{planCode}/versions")
    ResponseEntity<ChannelCostVersionResponseBody> createChannelCostVersion(
        @PathVariable("planCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String planCode,
        @Valid @RequestBody CreateChannelCostVersionRequestBody body);

    /**
     * 提交渠道成本版本待复核
     *
     * @param planCode 方案代码
     * @param costVer 版本号
     * @param body 提交人，必须等于编制人
     * @return 200 与 PENDING_APPROVAL 版本详情；非 DRAFT 或提交人不是编制人时 400
     */
    @PostExchange("/{planCode}/versions/{costVer}/submit")
    ChannelCostVersionResponseBody submitChannelCostVersion(
        @PathVariable("planCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String planCode,
        @PathVariable("costVer") @Pattern(regexp = "[1-9][0-9]{0,8}") String costVer,
        @Valid @RequestBody SubmitChannelCostVersionRequestBody body);

    /**
     * 批准渠道成本版本
     *
     * @param planCode 方案代码
     * @param costVer 版本号
     * @param body 复核人（必须不同于编制人）与确认的内容摘要
     * @return 200 与版本详情：已到生效时间为 ACTIVE（当前版本切换），否则 APPROVED；非 PENDING_APPROVAL、同人复核、
     *         摘要不一致或生效时间不晚于原当前版本时 400
     */
    @PostExchange("/{planCode}/versions/{costVer}/approve")
    ChannelCostVersionResponseBody approveChannelCostVersion(
        @PathVariable("planCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String planCode,
        @PathVariable("costVer") @Pattern(regexp = "[1-9][0-9]{0,8}") String costVer,
        @Valid @RequestBody ReviewChannelCostVersionRequestBody body);

    /**
     * 驳回渠道成本版本
     *
     * @param planCode 方案代码
     * @param costVer 版本号
     * @param body 复核人（必须不同于编制人）与确认的内容摘要
     * @return 200 与 REJECTED 版本详情；非 PENDING_APPROVAL、同人复核或摘要不一致时 400
     */
    @PostExchange("/{planCode}/versions/{costVer}/reject")
    ChannelCostVersionResponseBody rejectChannelCostVersion(
        @PathVariable("planCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String planCode,
        @PathVariable("costVer") @Pattern(regexp = "[1-9][0-9]{0,8}") String costVer,
        @Valid @RequestBody ReviewChannelCostVersionRequestBody body);

    /**
     * 作废未到生效时间的已批准渠道成本版本
     *
     * @param planCode 方案代码
     * @param costVer 版本号
     * @param body 作废人与原因（1–512 个字符）
     * @return 200 与 INACTIVE 版本详情，失效时间等于生效时间（不参与任何时间点的命中），并写入一条 CANCEL 变更记录；
     *         方案或版本不存在、版本不是 APPROVED 或已到生效时间时 400 BUSINESS_CHECK_FAILED；字段格式错误时 422；
     *         并发写入时 409
     */
    @PostExchange("/{planCode}/versions/{costVer}/cancel")
    ChannelCostVersionResponseBody cancelChannelCostVersion(
        @PathVariable("planCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String planCode,
        @PathVariable("costVer") @Pattern(regexp = "[1-9][0-9]{0,8}") String costVer,
        @Valid @RequestBody CancelChannelCostVersionRequestBody body);

    /**
     * 分页查询渠道成本方案
     *
     * @param channelId 可选 Channel 主键，正十进制整数字符串
     * @param status 可选方案状态：DRAFT、ACTIVE、INACTIVE
     * @param page 页码，默认 1，范围 1–100000
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按方案更新时间降序、方案主键降序的分页结果，含 Channel 编码与名称、当前版本与待复核版本数；筛选或分页参数非法时
     *         400 INVALID_REQUEST
     */
    @GetExchange
    PagingResponseBody<ChannelCostPlanSummaryResponseBody> listChannelCostPlans(
        @RequestParam(value = "channelId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String channelId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询渠道成本方案
     *
     * @param planCode 方案代码
     * @return 方案 Channel、状态、当前版本号与全部版本摘要（版本号降序）；方案不存在时 400
     */
    @GetExchange("/{planCode}")
    ChannelCostPlanResponseBody getChannelCostPlan(
        @PathVariable("planCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String planCode);

    /**
     * 查询渠道成本版本详情
     *
     * @param planCode 方案代码
     * @param costVer 版本号
     * @return 版本详情及按 scopeNo 排序的适用范围、范围内按 componentCode 排序的成本组成；方案或版本不存在时 400
     */
    @GetExchange("/{planCode}/versions/{costVer}")
    ChannelCostVersionResponseBody getChannelCostVersion(
        @PathVariable("planCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String planCode,
        @PathVariable("costVer") @Pattern(regexp = "[1-9][0-9]{0,8}") String costVer);
}
