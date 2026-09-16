// Client 入驻已批准的字段存储差异；目录、枚举与业务说明仍由 CCT 元数据拥有。
export const CLIENT_ONBOARDING_FIELD_STORAGE = Object.freeze({
  "party_profile.content_hash": {
    "type": "CHAR",
    "length": 64,
    "scale": "",
    "nullable": true
  },
  "party_profile.draft_application_id": {
    "type": "BIGINT",
    "length": "",
    "scale": "",
    "nullable": true
  },
  "party_profile.approved_time": {
    "type": "DATETIME",
    "length": "6",
    "scale": "",
    "nullable": true
  },
  "individual_profile.given_name": {
    "type": "VARCHAR",
    "length": 64,
    "scale": "",
    "nullable": true
  },
  "individual_profile.family_name": {
    "type": "VARCHAR",
    "length": 64,
    "scale": "",
    "nullable": true
  },
  "individual_profile.birth_date": {
    "type": "DATE",
    "length": "",
    "scale": "",
    "nullable": true
  },
  "individual_profile.nationality_country": {
    "type": "CHAR",
    "length": 2,
    "scale": "",
    "nullable": true
  },
  "individual_profile.residence_country": {
    "type": "CHAR",
    "length": 2,
    "scale": "",
    "nullable": true
  },
  "organization_profile.legal_name": {
    "type": "VARCHAR",
    "length": 64,
    "scale": "",
    "nullable": true
  },
  "organization_profile.incorporation_country": {
    "type": "CHAR",
    "length": 2,
    "scale": "",
    "nullable": true
  },
  "organization_profile.organization_type": {
    "type": "VARCHAR",
    "length": 64,
    "scale": "",
    "nullable": true
  },
  "organization_profile.incorporation_date": {
    "type": "DATE",
    "length": "",
    "scale": "",
    "nullable": true
  },
  "party_address.address_line1": {
    "type": "VARCHAR",
    "length": 255,
    "scale": "",
    "nullable": true
  },
  "party_address.city": {
    "type": "VARCHAR",
    "length": 128,
    "scale": "",
    "nullable": true
  },
  "party_address.country_code": {
    "type": "CHAR",
    "length": 2,
    "scale": "",
    "nullable": true
  },
  "party_profile_identifier.identity_key_id": {
    "type": "BIGINT",
    "length": "",
    "scale": "",
    "nullable": true
  },
  "party_profile_identifier.valid_from": {
    "type": "DATETIME",
    "length": 6,
    "scale": "",
    "nullable": true
  },
  "party_profile_identifier.identifier_type": {
    "type": "VARCHAR",
    "length": "64",
    "scale": "",
    "nullable": true
  },
  "party_profile_identifier.issuing_country": {
    "type": "CHAR",
    "length": "2",
    "scale": "",
    "nullable": true
  },
  "party_profile_identifier.issuer_scope_key": {
    "type": "VARCHAR",
    "length": "64",
    "scale": "",
    "nullable": true
  },
  "party_profile_identifier.identifier_value": {
    "type": "VARCHAR",
    "length": "128",
    "scale": "",
    "nullable": true
  },
  "party_relation.relation_id": {
    "type": "BIGINT",
    "length": "",
    "scale": "",
    "nullable": false
  },
  "party_relation.client_application_id": {
    "type": "BIGINT",
    "length": "",
    "scale": "",
    "nullable": true
  },
  "client_business_profile.business_model_code": {
    "type": "VARCHAR",
    "length": 64,
    "scale": "",
    "nullable": true
  },
  "client_business_profile.expected_volume": {
    "type": "DECIMAL",
    "length": 24,
    "scale": 8,
    "nullable": true
  },
  "client_business_profile.expected_ticket": {
    "type": "DECIMAL",
    "length": 24,
    "scale": 8,
    "nullable": true
  },
  "client_business_profile.currency": {
    "type": "CHAR",
    "length": 3,
    "scale": "",
    "nullable": true
  },
  "client_business_profile.content_hash": {
    "type": "CHAR",
    "length": 64,
    "scale": "",
    "nullable": true
  },
  "client_business_profile.business_description": {
    "type": "VARCHAR",
    "length": "2000",
    "scale": "",
    "nullable": true
  },
  "client_business_profile.classification_case_id": {
    "type": "BIGINT",
    "length": "",
    "scale": "",
    "nullable": true
  },
  "client_business_profile.classification_confirmed_by": {
    "type": "BIGINT",
    "length": "",
    "scale": "",
    "nullable": true
  },
  "client_business_profile.classification_confirmed_time": {
    "type": "DATETIME",
    "length": "6",
    "scale": "",
    "nullable": true
  },
  "client_application.requested_by_account_id": {
    "type": "BIGINT",
    "length": "",
    "scale": "",
    "nullable": false
  },
  "client_application.current_submission_id": {
    "type": "VARCHAR",
    "length": "64",
    "scale": "",
    "nullable": true
  }
});
