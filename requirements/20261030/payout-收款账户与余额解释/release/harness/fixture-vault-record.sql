-- 演练夹具：已有 PAYOUT_BENEFICIARY_V1 记录时 90-mor_vault-payout-rollback.sql 必须拒绝（只在断网演练容器执行；密文为占位字节）。
INSERT INTO t_vault_record (record_id, token, data_type, token_mode, value_fingerprint, ciphertext, encryption_nonce, wrapped_dek, kms_key_ref,
  create_time, update_time)
VALUES (880000000903, 'vt_rehearsal_fixture_payout', 'PAYOUT_BENEFICIARY_V1', 'REFERENCE', NULL, X'00', X'000000000000000000000000', X'00',
  'rehearsal', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
