-- READ ONLY: run separately in the correct CCT database after explicit environment approval.
-- This draft does not select a schema or execute writes.
SELECT DATABASE() AS selected_database;

-- CCT: duplicate or invalid rows block conversion; grouped status counts are informational.
SELECT party_id, COUNT(*) AS profile_count FROM t_party_profile GROUP BY party_id HAVING COUNT(*) > 1;
SELECT client_id, COUNT(*) AS profile_count FROM t_client_business_profile GROUP BY client_id HAVING COUNT(*) > 1;
SELECT organization_party_id,related_party_id,relation_type,COUNT(*) AS relation_count FROM t_party_relation GROUP BY organization_party_id,related_party_id,relation_type HAVING COUNT(*) > 1;
SELECT status,COUNT(*) AS applications FROM t_client_application GROUP BY status;
SELECT requested_by_account_id,COUNT(*) AS active_applications FROM t_client_application WHERE active_slot=1 GROUP BY requested_by_account_id HAVING COUNT(*) > 1;

-- Required requester and status/slot consistency must hold before adding the account index.
SELECT application_id,status,active_slot FROM t_client_application WHERE requested_by_account_id IS NULL;
SELECT application_id,status,active_slot FROM t_client_application WHERE (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','RETURNED') AND (active_slot IS NULL OR active_slot <> 1)) OR (status IN ('APPROVED','REJECTED','WITHDRAWN') AND active_slot IS NOT NULL) OR status IS NULL OR status NOT IN ('DRAFT','SUBMITTED','UNDER_REVIEW','RETURNED','APPROVED','REJECTED','WITHDRAWN');
