-- READ ONLY. Review against the selected Owner database; no cross-database queries.

-- Missing tables/columns are a baseline mismatch, not zero rows. Stop on any SQL error.

SELECT DATABASE() AS selected_database;

SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('t_party_profile','t_individual_profile','t_organization_profile','t_party_address','t_party_contact','t_party_profile_identifier','t_party_identity_key','t_party_relation','t_client_business_profile','t_client_application','t_party','t_client','t_client_business_classification','t_partner_application','t_operating_entity_application','t_agreement_party') ORDER BY TABLE_NAME,ORDINAL_POSITION;

SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('t_party_profile','t_individual_profile','t_organization_profile','t_party_address','t_party_contact','t_party_profile_identifier','t_party_identity_key','t_party_relation','t_client_business_profile','t_client_application','t_party','t_client','t_client_business_classification','t_partner_application','t_operating_entity_application','t_agreement_party') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;

-- Exact row counts for the conservative empty-data gate.

SELECT 't_party_profile' AS table_name, COUNT(*) AS row_count FROM `t_party_profile`;

SELECT 't_individual_profile' AS table_name, COUNT(*) AS row_count FROM `t_individual_profile`;

SELECT 't_organization_profile' AS table_name, COUNT(*) AS row_count FROM `t_organization_profile`;

SELECT 't_party_address' AS table_name, COUNT(*) AS row_count FROM `t_party_address`;

SELECT 't_party_contact' AS table_name, COUNT(*) AS row_count FROM `t_party_contact`;

SELECT 't_party_profile_identifier' AS table_name, COUNT(*) AS row_count FROM `t_party_profile_identifier`;

SELECT 't_party_identity_key' AS table_name, COUNT(*) AS row_count FROM `t_party_identity_key`;

SELECT 't_party_relation' AS table_name, COUNT(*) AS row_count FROM `t_party_relation`;

SELECT 't_client_business_profile' AS table_name, COUNT(*) AS row_count FROM `t_client_business_profile`;

SELECT 't_client_application' AS table_name, COUNT(*) AS row_count FROM `t_client_application`;

SELECT 't_party' AS table_name, COUNT(*) AS row_count FROM `t_party`;

SELECT 't_client' AS table_name, COUNT(*) AS row_count FROM `t_client`;

SELECT 't_client_business_classification' AS table_name, COUNT(*) AS row_count FROM `t_client_business_classification`;

SELECT 't_partner_application' AS table_name, COUNT(*) AS row_count FROM `t_partner_application`;

SELECT 't_operating_entity_application' AS table_name, COUNT(*) AS row_count FROM `t_operating_entity_application`;

SELECT 't_agreement_party' AS table_name, COUNT(*) AS row_count FROM `t_agreement_party`;

SELECT 't_individual_profile.profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_individual_profile` child LEFT JOIN `t_party_profile` parent ON child.`profile_id`=parent.`profile_id` WHERE child.`profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_organization_profile.profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_organization_profile` child LEFT JOIN `t_party_profile` parent ON child.`profile_id`=parent.`profile_id` WHERE child.`profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_party_address.profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_party_address` child LEFT JOIN `t_party_profile` parent ON child.`profile_id`=parent.`profile_id` WHERE child.`profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_party_contact.profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_party_contact` child LEFT JOIN `t_party_profile` parent ON child.`profile_id`=parent.`profile_id` WHERE child.`profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_party_profile_identifier.profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_party_profile_identifier` child LEFT JOIN `t_party_profile` parent ON child.`profile_id`=parent.`profile_id` WHERE child.`profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_party_profile_identifier.identity_key_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_party_profile_identifier` child LEFT JOIN `t_party_identity_key` parent ON child.`identity_key_id`=parent.`identity_key_id` WHERE child.`identity_key_id` IS NOT NULL AND parent.`identity_key_id` IS NULL;

SELECT 't_client_application.base_party_profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_client_application` child LEFT JOIN `t_party_profile` parent ON child.`base_party_profile_id`=parent.`profile_id` WHERE child.`base_party_profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_client_application.target_party_profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_client_application` child LEFT JOIN `t_party_profile` parent ON child.`target_party_profile_id`=parent.`profile_id` WHERE child.`target_party_profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_client_application.base_business_profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_client_application` child LEFT JOIN `t_client_business_profile` parent ON child.`base_business_profile_id`=parent.`business_profile_id` WHERE child.`base_business_profile_id` IS NOT NULL AND parent.`business_profile_id` IS NULL;

SELECT 't_client_application.target_business_profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_client_application` child LEFT JOIN `t_client_business_profile` parent ON child.`target_business_profile_id`=parent.`business_profile_id` WHERE child.`target_business_profile_id` IS NOT NULL AND parent.`business_profile_id` IS NULL;

SELECT 't_client_business_classification.business_profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_client_business_classification` child LEFT JOIN `t_client_business_profile` parent ON child.`business_profile_id`=parent.`business_profile_id` WHERE child.`business_profile_id` IS NOT NULL AND parent.`business_profile_id` IS NULL;

SELECT 't_partner_application.target_party_profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_partner_application` child LEFT JOIN `t_party_profile` parent ON child.`target_party_profile_id`=parent.`profile_id` WHERE child.`target_party_profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_operating_entity_application.party_profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_operating_entity_application` child LEFT JOIN `t_party_profile` parent ON child.`party_profile_id`=parent.`profile_id` WHERE child.`party_profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;

SELECT 't_agreement_party.party_profile_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_agreement_party` child LEFT JOIN `t_party_profile` parent ON child.`party_profile_id`=parent.`profile_id` WHERE child.`party_profile_id` IS NOT NULL AND parent.`profile_id` IS NULL;
