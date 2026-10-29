UPDATE mor_compliance_v2e2e.t_due_diligence_case SET risk_rating = 'LOW'
 WHERE case_id = 240674554333122560 AND status = 'APPROVED' AND risk_rating IS NULL;
