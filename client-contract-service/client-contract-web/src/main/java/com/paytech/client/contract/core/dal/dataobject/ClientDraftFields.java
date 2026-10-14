package com.paytech.client.contract.core.dal.dataobject;
import java.time.*;
import java.math.BigDecimal;
/** 主表单结构化查询，标识和金额保留数据库类型。 */
public record ClientDraftFields(
    /** 资料头标识，使空白草稿仍有有效查询行。 */ long profileId,
    /** 当前 individual.givenName 值。 */ String individualGivenName,
    /** 当前 individual.middleName 值。 */ String individualMiddleName,
    /** 当前 individual.familyName 值。 */ String individualFamilyName,
    /** 当前 individual.birthDate 值。 */ LocalDate individualBirthDate,
    /** 当前 individual.nationalityCountry 值。 */ String individualNationalityCountry,
    /** 当前 individual.residenceCountry 值。 */ String individualResidenceCountry,
    /** 当前 organization.legalName 值。 */ String organizationLegalName,
    /** 当前 organization.tradeName 值。 */ String organizationTradeName,
    /** 当前 organization.incorporationCountry 值。 */ String organizationIncorporationCountry,
    /** 当前 organization.organizationType 值。 */ String organizationOrganizationType,
    /** 当前 organization.incorporationDate 值。 */ LocalDate organizationIncorporationDate,
    /** 当前 address.addressLine1 值。 */ String addressAddressLine1,
    /** 当前 address.addressLine2 值。 */ String addressAddressLine2,
    /** 当前 address.city 值。 */ String addressCity,
    /** 当前 address.stateOrProvince 值。 */ String addressStateOrProvince,
    /** 当前 address.postalCode 值。 */ String addressPostalCode,
    /** 当前 address.countryCode 值。 */ String addressCountryCode,
    /** 当前 identifier.identifierType 值。 */ String identifierIdentifierType,
    /** 当前 identifier.issuingCountry 值。 */ String identifierIssuingCountry,
    /** 当前 identifier.identifierValue 值。 */ String identifierIdentifierValue,
    /** 当前 identifier.validFrom 值。 */ Instant identifierValidFrom,
    /** 当前 identifier.validTo 值。 */ Instant identifierValidTo,
    /** 当前 business.businessModelCode 值。 */ String businessBusinessModelCode,
    /** 当前 business.websiteUrl 值。 */ String businessWebsiteUrl,
    /** 当前 business.businessDescription 值。 */ String businessBusinessDescription,
    /** 当前 business.expectedVolume 值。 */ BigDecimal businessExpectedVolume,
    /** 当前 business.expectedTicket 值。 */ BigDecimal businessExpectedTicket,
    /** 当前 business.currency 值。 */ String businessCurrency) {
    @Override public String toString() { return "ClientDraftFields[redacted]"; }
}
