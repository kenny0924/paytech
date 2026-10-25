package com.paytech.client.master.ext.rest;

import static com.paytech.client.master.api.enums.EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND;
import static com.paytech.client.master.api.enums.EntityAdminErrorCode.OPERATING_ENTITY_NOT_FOUND;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.List;

import com.paytech.client.master.api.entityadmin.response.ClientSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntityDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntitySummaryResponseBody;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.domain.entityadmin.ClientSummaryView;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminDetail;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminView;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminDetail;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminView;
import com.paytech.client.master.domain.enums.EntitySourceRelationSourceType;
import com.paytech.client.master.domain.enums.LocalPartnerStatus;
import com.paytech.client.master.domain.enums.OperatingEntityStatus;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** OE / Local Partner 运营接口的 Path、Query 解析与响应转换；不访问数据库。 */
final class EntityAdminConverter {
    private static final DateTimeFormatter INSTANT = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
    private static final int MAX_PAGE_SIZE = 100;

    /** 工具类不创建实例。 */
    private EntityAdminConverter() {
    }

    /** OE 编号；格式非法按不存在处理。 */
    static String entityNo(String value) {
        if (value == null || !value.matches("[A-Z]{2}[0-9]{3}")) {
            throw new FailException(OPERATING_ENTITY_NOT_FOUND);
        }
        return value;
    }

    /** Local Partner ID；格式非法按不存在处理。 */
    static long localPartnerId(String value) {
        Long id = positiveId(value);
        if (id == null) {
            throw new FailException(LOCAL_PARTNER_NOT_FOUND);
        }
        return id;
    }

    /** Client ID；格式非法按不存在处理。 */
    static long clientId(String value) {
        Long id = positiveId(value);
        if (id == null) {
            throw new FailException(CheckoutCandidateErrorCode.CLIENT_NOT_FOUND);
        }
        return id;
    }

    /** 分页参数：公共规则之外每页最多 100 条。 */
    static PagingRequestBody paging(Integer page, Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        if (paging.size() > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("每页条数必须为 10、20、50 或 100");
        }
        return paging;
    }

    /** OE 状态筛选。 */
    static String entityStatus(String value) {
        return filter(value, OperatingEntityStatus.values(), "status");
    }

    /** 来源类型筛选。 */
    static String sourceType(String value) {
        return filter(value, EntitySourceRelationSourceType.values(), "sourceType");
    }

    /** Local Partner 状态筛选。 */
    static String partnerStatus(String value) {
        return filter(value, LocalPartnerStatus.values(), "status");
    }

    /** 角色筛选。 */
    static String role(String value) {
        return filter(value, PartnerRoleCode.values(), "role");
    }

    /** 经营国家筛选。 */
    static String country(String value) {
        if (value != null && !value.matches("[A-Z]{2}")) {
            throw new InvalidParameterException("country 必须为两位大写国家代码");
        }
        return value;
    }

    /** OE 列表行。 */
    static OperatingEntitySummaryResponseBody entitySummary(OperatingEntityAdminView view) {
        var provider = view.getProviderPartnerNo() == null ? null
            : new OperatingEntitySummaryResponseBody.ProviderSummary(id(view.getSourcePartnerId()), view.getProviderLegalName());
        return new OperatingEntitySummaryResponseBody(view.getEntityNo(), view.getLegalName(), view.getOperatingCountry(),
            code(view.getStatus()), code(view.getSourceType()), code(view.getAcquiringQualification()), provider,
            view.getChangeVersion());
    }

    /** OE 详情。 */
    static OperatingEntityDetailResponseBody entityDetail(OperatingEntityAdminDetail detail) {
        var view = detail.getEntity();
        var source = view.getSourceRelationId() == null ? null
            : new OperatingEntityDetailResponseBody.SourceRelation(id(view.getSourceRelationId()), code(view.getSourceType()),
                id(view.getSourceClientId()), id(view.getSourcePartnerId()), time(view.getSourceEffectiveFrom()),
                time(view.getSourceEffectiveTo()));
        var provider = view.getProviderPartnerNo() == null ? null
            : new OperatingEntityDetailResponseBody.Provider(id(view.getSourcePartnerId()), view.getProviderPartnerNo(),
                id(view.getProviderPartyId()), view.getProviderLegalName(), code(view.getProviderStatus()),
                codes(detail.getProviderRoles()));
        var scopes = detail.getScopes().stream().map(s -> new OperatingEntityDetailResponseBody.Scope(id(s.scopeId()),
            s.consumerCountryCode(), s.currency(), s.industryCode(), s.mccCode(), code(s.status()), time(s.effectiveFrom()),
            time(s.effectiveTo()))).toList();
        var sellers = detail.getSellerDisclosures().stream().map(s -> new OperatingEntityDetailResponseBody.SellerDisclosureSummary(
            id(s.disclosureId()), s.countryCode(), s.locale(), code(s.status()), time(s.effectiveFrom()), time(s.effectiveTo())))
            .toList();
        return new OperatingEntityDetailResponseBody(view.getEntityNo(), id(view.getPartyId()), view.getLegalName(),
            view.getOperatingCountry(), code(view.getStatus()), code(view.getAcquiringQualification()), view.getChangeVersion(),
            source, provider, scopes, sellers);
    }

    /** Local Partner 列表行。 */
    static LocalPartnerSummaryResponseBody partnerSummary(LocalPartnerAdminView view) {
        return new LocalPartnerSummaryResponseBody(id(view.getLocalPartnerId()), view.getPartnerNo(), view.getLegalName(),
            code(view.getStatus()), codes(view.getRoles()), view.getProvidedEntityCount(), view.getChangeVersion());
    }

    /** Local Partner 详情。 */
    static LocalPartnerDetailResponseBody partnerDetail(LocalPartnerAdminDetail detail) {
        var view = detail.getPartner();
        var entities = detail.getProvidedEntities().stream().map(e -> new LocalPartnerDetailResponseBody.ProvidedEntity(
            e.getEntityNo(), e.getLegalName(), code(e.getEntityStatus()), id(e.getSourceRelationId()), code(e.getSourceStatus()),
            time(e.getEffectiveFrom()), time(e.getEffectiveTo()))).toList();
        return new LocalPartnerDetailResponseBody(id(view.getLocalPartnerId()), view.getPartnerNo(), id(view.getPartyId()),
            view.getLegalName(), code(view.getStatus()), codes(view.getRoles()), view.getChangeVersion(), entities);
    }

    /** Client 主体摘要。 */
    static ClientSummaryResponseBody clientSummary(ClientSummaryView view) {
        return new ClientSummaryResponseBody(id(view.getClientId()), view.getClientNo(), id(view.getPartyId()),
            code(view.getClientType()), code(view.getStatus()), view.getLegalName());
    }

    /** 可选筛选值必须是给定枚举的 Code。 */
    private static String filter(String value, CodeEnum[] allowed, String name) {
        if (value == null) {
            return null;
        }
        if (Arrays.stream(allowed).noneMatch(item -> item.getCode().equals(value))) {
            throw new InvalidParameterException(name + " 取值非法");
        }
        return value;
    }

    /** 规范正十进制 BIGINT；非法返回 null。 */
    private static Long positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            return null;
        }
    }

    /** 角色 Code 列表。 */
    private static List<String> codes(List<PartnerRoleCode> roles) {
        return roles.stream().map(PartnerRoleCode::getCode).toList();
    }

    /** ID 输出为十进制字符串，空值保持 null。 */
    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    /** UTC 微秒精度时间，空值保持 null。 */
    private static String time(Instant value) {
        return value == null ? null : INSTANT.format(value);
    }

    /** 枚举 Code，空值保持 null。 */
    private static String code(CodeEnum value) {
        return value == null ? null : value.getCode();
    }
}
