package com.paytech.client.master.domain.onboarding;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.PartyAttribute;
/** 草稿资料到 分区 → 字段 → 文本 的纯转换；证件只输出类型、签发国、脱敏值、检查结论与日期。 */
public final class ClientDraftValues {
    private ClientDraftValues() {}
    /** 资料字段按草稿分区输出；证件只输出非敏感项。 */
    public static Map<String,Map<String,String>> values(ClientDraftFields fields) {
        Map<String,Map<String,String>> values=new LinkedHashMap<>();
        put(values,"individual","givenName",fields.individualGivenName());
        put(values,"individual","middleName",fields.individualMiddleName());
        put(values,"individual","familyName",fields.individualFamilyName());
        put(values,"individual","birthDate",fields.individualBirthDate());
        put(values,"individual","nationalityCountry",fields.individualNationalityCountry());
        put(values,"individual","residenceCountry",fields.individualResidenceCountry());
        put(values,"individual","birthPlace",fields.individualBirthPlace());
        put(values,"individual","birthCountry",fields.individualBirthCountry());
        put(values,"individual","gender",fields.individualGender());
        put(values,"individual","sourceOfFundsCode",fields.individualSourceOfFundsCode());
        put(values,"individual","pepSelfDeclared",fields.individualPepSelfDeclared());
        put(values,"organization","legalName",fields.organizationLegalName());
        put(values,"organization","tradeName",fields.organizationTradeName());
        put(values,"organization","incorporationCountry",fields.organizationIncorporationCountry());
        put(values,"organization","organizationType",fields.organizationOrganizationType());
        put(values,"organization","incorporationDate",fields.organizationIncorporationDate());
        put(values,"organization","registrationAuthorityCode",fields.organizationRegistrationAuthorityCode());
        put(values,"organization","licenseExpiryDate",fields.organizationLicenseExpiryDate());
        put(values,"organization","listedCompany",fields.organizationListedCompany());
        put(values,"address","addressLine1",fields.addressAddressLine1());
        put(values,"address","addressLine2",fields.addressAddressLine2());
        put(values,"address","city",fields.addressCity());
        put(values,"address","stateOrProvince",fields.addressStateOrProvince());
        put(values,"address","postalCode",fields.addressPostalCode());
        put(values,"address","countryCode",fields.addressCountryCode());
        put(values,"address","buildingNumber",fields.addressBuildingNumber());
        put(values,"address","additionalNumber",fields.addressAdditionalNumber());
        put(values,"address","district",fields.addressDistrict());
        put(values,"address","poBox",fields.addressPoBox());
        put(values,"identifier","identifierType",fields.identifierIdentifierType());
        put(values,"identifier","issuingCountry",fields.identifierIssuingCountry());
        put(values,"identifier","identifierMasked",fields.identifierMaskedValue());
        put(values,"identifier","identifierCheckResult",fields.identifierCheckResult());
        put(values,"identifier","issueDate",fields.identifierIssueDate());
        put(values,"identifier","expiryDate",fields.identifierExpiryDate());
        put(values,"business","businessModelCode",fields.businessBusinessModelCode());
        put(values,"business","websiteUrl",fields.businessWebsiteUrl());
        put(values,"business","businessDescription",fields.businessBusinessDescription());
        put(values,"business","expectedVolume",fields.businessExpectedVolume());
        put(values,"business","expectedTicket",fields.businessExpectedTicket());
        put(values,"business","currency",fields.businessCurrency());
        put(values,"business","expectedPaymentMethods",fields.businessExpectedPaymentMethods());
        put(values,"business","refundPolicyUrl",fields.businessRefundPolicyUrl());
        put(values,"business","fulfillmentType",fields.businessFulfillmentType());
        put(values,"business","fulfillmentDays",fields.businessFulfillmentDays());
        return values;
    }
    /** 某份资料的扩展属性：属性代码到文本。 */
    public static Map<String,String> attributes(List<PartyAttribute> rows,Long profileId) {
        Map<String,String> result=new LinkedHashMap<>();
        rows.stream().filter(row -> Objects.equals(row.getProfileId(),profileId))
            .forEach(row -> result.put(row.getAttributeCode(),attributeText(row)));
        return result;
    }
    /** 扩展属性的文本形式。 */
    public static String attributeText(PartyAttribute row) {
        if (row.getValueDate()!=null) {
            return row.getValueDate().toString();
        }
        return row.getValueDecimal()!=null?row.getValueDecimal().stripTrailingZeros().toPlainString():row.getValueText();
    }
    private static void put(Map<String,Map<String,String>> values,String group,String field,Object value) {
        String text=switch(value) {
            case null -> null;
            case Instant time -> time.atOffset(ZoneOffset.UTC).toLocalDate().toString();
            case BigDecimal amount -> amount.stripTrailingZeros().toPlainString();
            default -> value.toString();
        };
        values.computeIfAbsent(group,ignored -> new LinkedHashMap<>()).put(field,text);
    }
}
