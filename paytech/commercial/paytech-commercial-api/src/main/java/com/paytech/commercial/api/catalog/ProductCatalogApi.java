package com.paytech.commercial.api.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.commercial.api.catalog.request.CreateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.ProductCatalogTransitionRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateFeeEventCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.response.PricingDimensionListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilitySummary;
import com.paytech.commercial.api.catalog.response.ProductFeeEventListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductFeeEventSummary;
import com.paytech.commercial.api.catalog.response.ProductListResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 产品目录读取与运营维护（开通与定价模型调整 technique.md §4.1；仅私网，调用方 operations-console-bff）
 * <li>目录规模有限，列表不分页；读取不加锁、不开事务。</li>
 * <li>写入锁单一目录锁，以目录行 changeVersion 做 CAS，冲突 409；业务检查失败 400 BUSINESS_CHECK_FAILED。</li>
 */
@HttpExchange(url = "/products/v1", accept = "application/json")
public interface ProductCatalogApi {
    /** Gateway 传入、BFF 透传的操作者 Account ID 请求头。 */
    String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /**
     * 产品（L0）列表，按产品 ID 升序
     *
     * @param status 可选状态：DRAFT、ACTIVE、RETIRED
     * @return 产品列表；未知状态 400
     */
    @GetExchange
    ProductListResponseBody listProducts(@RequestParam(value = "status", required = false) String status);

    /**
     * 可开通项（L1）列表，按可开通项 ID 升序，含可配维度
     *
     * @param productCode 可选产品代码：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM
     * @param status 可选状态：DRAFT、ACTIVE、RETIRED
     * @return 可开通项列表；未知代码 400
     */
    @GetExchange("/capabilities")
    ProductCapabilityListResponseBody listCapabilities(
        @RequestParam(value = "productCode", required = false) String productCode,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 计费事件（L2）列表，按计费事件 ID 升序，含挂接的开通项种类
     *
     * @param productCode 可选产品代码
     * @param termType 可选条款类型（开放目录代码，如 CLIENT_PRICING），用于价格编辑时只列出该条款类型登记的事件
     * @return 计费事件列表；未知产品代码 400
     */
    @GetExchange("/fee-events")
    ProductFeeEventListResponseBody listFeeEvents(
        @RequestParam(value = "productCode", required = false) String productCode,
        @RequestParam(value = "termType", required = false) String termType);

    /**
     * 维度清单：代码登记的维度元数据
     *
     * @return 全部维度，按登记顺序
     */
    @GetExchange("/dimensions")
    PricingDimensionListResponseBody listDimensions();

    /**
     * 新增可开通项（ACTIVE）及其可配维度
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param body 可开通项内容
     * @return 201 与可开通项；代码已存在 409；产品非 ACTIVE、维度不可用于开通、支付方式代码与种类不符 400
     */
    @PostExchange("/capabilities")
    ResponseEntity<ProductCapabilitySummary> createCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateProductCapabilityRequestBody body);

    /**
     * 整体改写可开通项的名称、归属产品与可配维度
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param productCapabilityId 可开通项 ID
     * @param body 新内容与版本条件
     * @return 200 与可开通项；不存在 404；版本冲突 409
     */
    @PutExchange("/capabilities/{productCapabilityId}")
    ProductCapabilitySummary updateCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("productCapabilityId") @Pattern(regexp = "[1-9][0-9]{0,18}") String productCapabilityId,
        @Valid @RequestBody UpdateProductCapabilityRequestBody body);

    /**
     * 停用可开通项：ACTIVE / DRAFT → RETIRED；已开通的合同不受影响，新增开通被拒
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param productCapabilityId 可开通项 ID
     * @param body 版本条件
     * @return 200 与可开通项；已是 RETIRED 且版本为期望版本加一时按重放返回
     */
    @PostExchange("/capabilities/{productCapabilityId}/retire")
    ProductCapabilitySummary retireCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("productCapabilityId") @Pattern(regexp = "[1-9][0-9]{0,18}") String productCapabilityId,
        @Valid @RequestBody ProductCatalogTransitionRequestBody body);

    /**
     * 启用可开通项：RETIRED / DRAFT → ACTIVE
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param productCapabilityId 可开通项 ID
     * @param body 版本条件
     * @return 200 与可开通项；已是 ACTIVE 且版本为期望版本加一时按重放返回
     */
    @PostExchange("/capabilities/{productCapabilityId}/activate")
    ProductCapabilitySummary activateCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("productCapabilityId") @Pattern(regexp = "[1-9][0-9]{0,18}") String productCapabilityId,
        @Valid @RequestBody ProductCatalogTransitionRequestBody body);

    /**
     * 改写计费事件挂接的开通项种类
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param productFeeEventId 计费事件登记 ID
     * @param body 挂接内容与版本条件
     * @return 200 与计费事件；不存在 404；FEATURE 未指向同产品功能开通项 400；版本冲突 409
     */
    @PutExchange("/fee-events/{productFeeEventId}/capability")
    ProductFeeEventSummary updateFeeEventCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("productFeeEventId") @Pattern(regexp = "[1-9][0-9]{0,18}") String productFeeEventId,
        @Valid @RequestBody UpdateFeeEventCapabilityRequestBody body);
}
