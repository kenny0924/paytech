package com.paytech.client.master.integration.impl;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.master.domain.onboarding.pack.ReferenceDataItem;
import com.paytech.client.master.integration.ReferenceDataClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.CountrySubdivisionQueryApi;
import com.paytech.platform.support.api.referencedata.IndustryQueryApi;
import com.paytech.platform.support.api.referencedata.MccQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCountryQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;

import lombok.AllArgsConstructor;

/**
 * 调用 Platform /platform/v1/reference-data/* 并把响应转换为本服务的 ReferenceDataItem
 * <li>只用 codes 过滤，单次最多 50 个编码，超过分批；严格 JSON 与 8192 字节上限由装配方配置。</li>
 * <li>Platform 返回请求以外的编码、缺少编码或状态时视为依赖异常，失败关闭。</li>
 */
@AllArgsConstructor
public class ReferenceDataClientImpl implements ReferenceDataClient {
    /** Platform 单次 codes 上限。 */
    static final int BATCH_SIZE = 50;
    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern SUBDIVISION = Pattern.compile("[A-Z]{2}-[A-Z0-9]{1,3}");
    private static final Pattern INDUSTRY = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern MCC = Pattern.compile("[0-9]{4}");

    private final ReferenceCountryQueryApi countries;
    private final ReferenceCurrencyQueryApi currencies;
    private final CountrySubdivisionQueryApi subdivisions;
    private final IndustryQueryApi industries;
    private final MccQueryApi mccs;

    /** 国家与地区。 */
    @Override
    public Map<String, ReferenceDataItem> countries(Collection<String> codes) {
        return query(codes, COUNTRY, batch -> {
            ReferenceCountryListResponseBody body = call(() -> countries.listCountries(null, batch));
            return body == null ? null : body.getItems() == null ? null : body.getItems().stream()
                .map(item -> item == null ? null : new ReferenceDataItem(item.getCountryCode(), active(item.getStatus()), null, null))
                .toList();
        });
    }

    /** 币种。 */
    @Override
    public Map<String, ReferenceDataItem> currencies(Collection<String> codes) {
        return query(codes, CURRENCY, batch -> {
            ReferenceCurrencyListResponseBody body = call(() -> currencies.listCurrencies(null, batch));
            return body == null ? null : body.getItems() == null ? null : body.getItems().stream()
                .map(item -> item == null ? null : new ReferenceDataItem(item.getCurrencyCode(), active(item.getStatus()), null, null))
                .toList();
        });
    }

    /** 一级行政区，带所属国家。 */
    @Override
    public Map<String, ReferenceDataItem> subdivisions(Collection<String> codes) {
        return query(codes, SUBDIVISION, batch -> {
            CountrySubdivisionListResponseBody body = call(() -> subdivisions.listSubdivisions(null, null, batch));
            return body == null ? null : body.getItems() == null ? null : body.getItems().stream()
                .map(item -> item == null || item.getCountryCode() == null ? null
                    : new ReferenceDataItem(item.getSubdivisionCode(), active(item.getStatus()), item.getCountryCode(), null))
                .toList();
        });
    }

    /** 行业，带层级。 */
    @Override
    public Map<String, ReferenceDataItem> industries(Collection<String> codes) {
        return query(codes, INDUSTRY, batch -> {
            IndustryListResponseBody body = call(() -> industries.listIndustries(null, null, null, batch));
            return body == null ? null : body.getItems() == null ? null : body.getItems().stream()
                .map(item -> item == null || item.getLevel() == null ? null
                    : new ReferenceDataItem(item.getIndustryCode(), active(item.getStatus()), null, item.getLevel()))
                .toList();
        });
    }

    /** MCC；一批不超过 50 个编码，取第 1 页 50 条即可覆盖。 */
    @Override
    public Map<String, ReferenceDataItem> mccs(Collection<String> codes) {
        return query(codes, MCC, batch -> {
            PagingResponseBody<MccResponseBody> body = call(() -> mccs.searchMccs(null, batch, null, 1, BATCH_SIZE));
            return body == null ? null : body.items().stream()
                .map(item -> item == null ? null : new ReferenceDataItem(item.getMccCode(), active(item.getStatus()), null, null))
                .toList();
        });
    }

    /** 去重、过滤格式、分批查询并合并；响应含请求外编码或缺字段时失败关闭。 */
    static Map<String, ReferenceDataItem> query(Collection<String> codes, Pattern format,
        Function<String, List<ReferenceDataItem>> batchQuery) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Platform 参考数据调用不能位于数据库事务内");
        }
        List<String> valid = codes.stream().filter(Objects::nonNull).filter(code -> format.matcher(code).matches()).distinct().toList();
        Map<String, ReferenceDataItem> result = new LinkedHashMap<>();
        for (int start = 0; start < valid.size(); start += BATCH_SIZE) {
            List<String> batch = valid.subList(start, Math.min(start + BATCH_SIZE, valid.size()));
            List<ReferenceDataItem> items = batchQuery.apply(String.join(",", batch));
            if (items == null) {
                throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE, "Platform 参考数据响应不完整");
            }
            for (ReferenceDataItem item : items) {
                if (item == null || item.code() == null || !batch.contains(item.code())) {
                    throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE, "Platform 参考数据响应与查询编码不符");
                }
                result.put(item.code(), item);
            }
        }
        return result;
    }

    /** 一次远程调用；任何传输、协议或 Platform 拒绝都按依赖不可用处理。 */
    static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RuntimeException unavailable) {
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE, "Platform 参考数据暂不可用", unavailable);
        }
    }

    /** 只有 ACTIVE 可被新业务选择；缺失或未知状态失败关闭为不可用。 */
    static boolean active(String status) {
        return ReferenceDataStatus.ACTIVE.getCode().equals(status);
    }
}
