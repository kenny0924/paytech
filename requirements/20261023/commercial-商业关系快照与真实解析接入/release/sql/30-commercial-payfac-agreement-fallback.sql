-- 发布副本（**兜底，默认不执行**）：paytech/commercial/docs/database/sql/changes/2026-10-24-payfac-partner-agreement.sql 原文，开头填入
-- mor-test 变量。首选路径是阶段 7 经运营 API 创建 PayFac Partner 合同与两个方案（留审计 / 事件 / maker-checker，见 data-inventory.md 第 5 节）；
-- 只有运营 API 不可用、且用户另行同意 SQL 兜底时才执行本文件（D-2 已确认走运营 API）。价目按 D-4（用户 2026-10-25 确认）：每个方案两条
-- AE001 / USD 价格——CARD_STANDARD（priority 1，草稿原有）与 APPLE_PAY_STANDARD（priority 2，本副本在第 7 步后追加，ID 900000000001371033/34、
-- 组件 …43/44，均在 86 回滚号段内），费率全部 0。
-- 变量来源（盘点）：PayFac Partner 900000000001370001（22 固定）；AE001 Party / 资料 = 占位 880000000702 / 881000000702（21 种子建立，
--   与现有 9 份 Client 合同 OE 签约方一致）；平台签约方 880000000701 / 881000000701（21 种子建立，与 DEMO-PT-001 / DEMO2-PT-101 及 ledger
--   PLATFORM_HQ 一致）；签署文件为合成 ID + 合成 SHA-256（现有演示合同同样使用合成文件 ID）；操作人 = mor-test 运营管理员账号；
--   生效起点 2026-10-25T00:00Z（须不晚于执行时刻、不早于资料批准时间 2026-10-13）。
SET @payfac_local_partner_id = 900000000001370001;
SET @ae001_party_id = 880000000702;
SET @ae001_party_profile_id = 881000000702;
SET @platform_party_id = 880000000701;
SET @platform_party_profile_id = 881000000701;
SET @signing_file_id = 900000000001371099;
SET @signing_content_hash = SHA2('mor-test relsnap seed 2026-10-25: PAYFAC-AE001-PARTNER signing document', 256);
SET @operator_account_id = 221546852372267008;
SET @card_profile_code = 'CARD_STANDARD';
SET @effective_from = '2026-10-25 00:00:00.000000';
-- =====================================================================================================================
-- commercial（mor_commercial）· 2026-10-24 商业关系快照与真实解析接入 · 种子设计：我们的 PayFac Partner 合同（R6）
-- 需求：requirements/20261023/commercial-商业关系快照与真实解析接入/（technique.md §3.4.1 commercial DML）
--
-- 状态：设计稿，没有在任何环境执行。只在 mor-test 阶段 6 由操作员审阅、填好下列变量后手工执行。
--
-- 首选路径：经运营 API（Gateway → operations-console-bff → commercial）创建，不走本 SQL。运营 API 会写完整的 BCR 根 / Child 与
--   t_business_change_version 审计快照（含完整性哈希）、经 compensation_event 发布 AgreementStatusChanged /
--   CommercialTermVersionActivated，并做 maker / checker 与签署校验；2026-10-20 的 DEMO-PT-001 Partner 合同就是这样建的。顺序：
--   1) POST /agreements/v1（PARTNER，localPartnerId=@payfac_local_partner_id，覆盖 OE AE001，三方 Party 见下）→ 签署文件 → 提交签署 →
--      三方登记签署 → 激活；
--   2) POST /commercial-terms/v1/plans 建 PARTNER_SHARE、PLATFORM_SERVICE_CHARGE 两个方案（localPartnerId，agreementId=新合同）→ 各建
--      版本（agreementId=新合同）→ 替换价格（AE001 / USD / 卡画像，费率 0）→ 提交 → 批准（另一账号）→ 排期 → 激活。
--   注意：本次起保存 PLATFORM_SERVICE_CHARGE 价格与激活合同都会校验合同里 OPERATING_ENTITY 的 Party 等于 LOCAL_PARTNER 的 Party
--   （AC-10），所以必须用 AE001 的 Party 签 LOCAL_PARTNER 和 OPERATING_ENTITY 两个角色。
--
-- 本 SQL 只是运营 API 不可用时的兜底，限制如下：
--   * 不写 t_business_change_version：快照 JSON 的完整性哈希只能由应用生成，SQL 生成的会通不过历史完整性检查；运营端的变更历史页对这些
--     记录为空。BCR 根 / Child 照常写（Checkout 解析与写接口的 CAS 需要它们）。
--   * 不登记 compensation_event：下游（billing / ledger）收不到 AgreementStatusChanged / CommercialTermVersionActivated，需要另行确认
--     下游是否依赖这两个事件。
--
-- 依赖与变量（阶段 6 填写；client-master 先执行 changes/2026-10-24-relationship-snapshot-data.sql 建好 LP-PAYFAC-AE001）：
--   @payfac_local_partner_id    LP-PAYFAC-AE001 的 t_local_partner.local_partner_id
--   @ae001_party_id             AE001 的 Party（= PayFac Partner 的 Party）
--   @ae001_party_profile_id     该 Party 已批准的资料 ID（client-master，签署时间之前批准、没有草稿申请）
--   @platform_party_id          平台签约方 Party（mor-test 现为占位 880000000701，阶段 6 确认）
--   @platform_party_profile_id  平台签约方 Party 已批准的资料 ID
--   @signing_file_id / @signing_content_hash  Platform 文件服务里的 Partner 合同签署文件 ID 与 SHA-256（小写十六进制）
--   @operator_account_id        记录签署与创建的运营账号
--   @card_profile_code          mor-test 上 ACTIVE 的卡定价画像代码（例如通用 CARD 画像）
--   @effective_from             合同与条款生效起点（UTC，不晚于执行时刻）
--
-- ID 段：9000000000013710xx（commercial 本需求专用，执行前确认未被占用）。
--
-- 执行前核对：
--   SELECT * FROM t_agreement WHERE local_partner_id = @payfac_local_partner_id;            -- 应为空
--   SELECT * FROM t_commercial_term_plan WHERE local_partner_id = @payfac_local_partner_id; -- 应为空（uk_comm_term_partner）
--   SELECT * FROM t_agreement WHERE agreement_id BETWEEN 900000000001371000 AND 900000000001371099;  -- 应为空
--
-- 回滚（同一事务执行；须与 client-master / acquiring 的回滚一起进行）：
--   DELETE FROM t_business_change_record WHERE history_id BETWEEN 900000000001371000 AND 900000000001371099;
--   DELETE FROM t_commercial_term_component WHERE term_component_id BETWEEN 900000000001371000 AND 900000000001371099;
--   DELETE FROM t_commercial_term_price WHERE term_price_id BETWEEN 900000000001371000 AND 900000000001371099;
--   DELETE FROM t_commercial_term_version WHERE term_version_id BETWEEN 900000000001371000 AND 900000000001371099;
--   DELETE FROM t_commercial_term_plan WHERE term_plan_id BETWEEN 900000000001371000 AND 900000000001371099;
--   DELETE FROM t_agreement_party WHERE agreement_party_id BETWEEN 900000000001371000 AND 900000000001371099;
--   DELETE FROM t_agreement_document_ref WHERE agreement_document_id BETWEEN 900000000001371000 AND 900000000001371099;
--   DELETE FROM t_agreement_operating_entity WHERE agreement_operating_entity_id BETWEEN 900000000001371000 AND 900000000001371099;
--   DELETE FROM t_agreement WHERE agreement_id BETWEEN 900000000001371000 AND 900000000001371099;
-- =====================================================================================================================

-- SET @payfac_local_partner_id = ...;  SET @ae001_party_id = ...;  SET @ae001_party_profile_id = ...;
-- SET @platform_party_id = ...;        SET @platform_party_profile_id = ...;
-- SET @signing_file_id = ...;          SET @signing_content_hash = '...';
-- SET @operator_account_id = ...;      SET @card_profile_code = '...';
-- SET @effective_from = '2026-10-24 00:00:00.000000';
SET @now = UTC_TIMESTAMP(6);

START TRANSACTION;

-- 1. PARTNER 合同：Owner 为 PayFac Partner，business_mode / fund_mode / pricing_authority / client_id / entity_no 为空，ACTIVE。
INSERT INTO t_agreement (agreement_id, agreement_no, agreement_name, agreement_type, business_mode, fund_mode,
  pricing_authority, referral_partner_id, client_id, local_partner_id, entity_no, agreement_format, status,
  effective_from, effective_to, signed_time, activated_time, create_by, create_time, update_time)
VALUES (900000000001371001, 'PAYFAC-AE001-PARTNER', 'PayFac Partner Agreement · AE001', 'PARTNER', NULL, NULL,
  NULL, NULL, NULL, @payfac_local_partner_id, NULL, 'MERGED_PARTY', 'ACTIVE',
  @effective_from, NULL, @effective_from, @effective_from, @operator_account_id, @now, @now);

-- 2. 覆盖 OE：AE001。
INSERT INTO t_agreement_operating_entity (agreement_operating_entity_id, agreement_id, entity_no, create_time, update_time)
VALUES (900000000001371002, 900000000001371001, 'AE001', @now, @now);

-- 3. 签署文件（恰好一份 SIGNING_DOCUMENT）。
INSERT INTO t_agreement_document_ref (agreement_document_id, agreement_id, document_type, file_id, content_hash,
  create_time, update_time)
VALUES (900000000001371003, 900000000001371001, 'SIGNING_DOCUMENT', @signing_file_id, @signing_content_hash, @now, @now);

-- 4. 三方签署：LOCAL_PARTNER 与 OPERATING_ENTITY 同为 AE001 的 Party（同一份签署事实，只有最小 ID 的行占 acceptance_ref_slot=1），
--    PLATFORM_SIGNATORY 为平台主体 Party。签署方式 CLICK_ACCEPTANCE，证据为上面的签署文件。
INSERT INTO t_agreement_party (agreement_party_id, agreement_id, party_id, party_profile_id, party_role, acceptance_method,
  accepted_by_actor_id, accepted_time, evidence_document_id, acceptance_reference, acceptance_ref_slot,
  acceptance_recorded_by, acceptance_recorded_at, create_by, create_time, update_time)
VALUES
  (900000000001371004, 900000000001371001, @ae001_party_id, @ae001_party_profile_id, 'LOCAL_PARTNER', 'CLICK_ACCEPTANCE',
   @operator_account_id, @effective_from, 900000000001371003, 'PAYFAC-AE001-SIGN-PARTNER', 1,
   @operator_account_id, @effective_from, @operator_account_id, @now, @now),
  (900000000001371005, 900000000001371001, @ae001_party_id, @ae001_party_profile_id, 'OPERATING_ENTITY', 'CLICK_ACCEPTANCE',
   @operator_account_id, @effective_from, 900000000001371003, 'PAYFAC-AE001-SIGN-PARTNER', NULL,
   @operator_account_id, @effective_from, @operator_account_id, @now, @now),
  (900000000001371006, 900000000001371001, @platform_party_id, @platform_party_profile_id, 'PLATFORM_SIGNATORY',
   'CLICK_ACCEPTANCE', @operator_account_id, @effective_from, 900000000001371003, 'PAYFAC-AE001-SIGN-PLATFORM', 1,
   @operator_account_id, @effective_from, @operator_account_id, @now, @now);

-- 5. 两个 Partner 条款方案：Owner 为 Local Partner，方案不存合同 / OE / Client，ACTIVE。
INSERT INTO t_commercial_term_plan (term_plan_id, plan_code, term_type, owner_type, template_name, agreement_id, entity_no,
  client_id, local_partner_id, status, create_time, update_time)
VALUES
  (900000000001371011, 'PAYFAC-AE001-PARTNER-SHARE', 'PARTNER_SHARE', 'LOCAL_PARTNER', NULL, NULL, NULL,
   NULL, @payfac_local_partner_id, 'ACTIVE', @now, @now),
  (900000000001371012, 'PAYFAC-AE001-SERVICE-CHARGE', 'PLATFORM_SERVICE_CHARGE', 'LOCAL_PARTNER', NULL, NULL, NULL,
   NULL, @payfac_local_partner_id, 'ACTIVE', @now, @now);

-- 6. 各方案第 1 版：引用 Partner 合同，ACTIVE（active_slot=1），BLENDED / EXCLUSIVE，自 @effective_from 起无终点。
INSERT INTO t_commercial_term_version (term_version_id, term_plan_id, agreement_id, version_no, status, active_slot,
  effective_from, effective_to, rounding_mode, pricing_model, fee_tax_treatment, source_template_version_id,
  signature_exempt, create_time, update_time)
VALUES
  (900000000001371021, 900000000001371011, 900000000001371001, 1, 'ACTIVE', 1, @effective_from, NULL, 'HALF_UP',
   'BLENDED', 'EXCLUSIVE', NULL, 0, @now, @now),
  (900000000001371022, 900000000001371012, 900000000001371001, 1, 'ACTIVE', 1, @effective_from, NULL, 'HALF_UP',
   'BLENDED', 'EXCLUSIVE', NULL, 0, @now, @now);

-- 7. 价格：AE001 / USD / 卡画像，Partner 条款不带结算字段；阶段 6 确定实际费率前先按 0 占位。
INSERT INTO t_commercial_term_price (term_price_id, term_version_id, entity_no, currency, pricing_profile_code, priority_no,
  settlement_business_days, business_calendar_code, settlement_timezone, cutoff_second_of_day, source_template_price_id,
  create_time, update_time)
VALUES
  (900000000001371031, 900000000001371021, 'AE001', 'USD', @card_profile_code, 1, NULL, NULL, NULL, NULL, NULL, @now, @now),
  (900000000001371032, 900000000001371022, 'AE001', 'USD', @card_profile_code, 1, NULL, NULL, NULL, NULL, NULL, @now, @now);

INSERT INTO t_commercial_term_component (term_component_id, term_price_id, component_no, component_code, component_type,
  trigger_code, calculation_base, refund_behavior, rate, fixed_amount, currency, min_amount, max_amount, band_basis, band_mode,
  create_time, update_time)
VALUES
  (900000000001371041, 900000000001371031, 1, 'PARTNER_SHARE', 'PERCENTAGE', 'SALE', 'GROSS_AMOUNT', 'RETURN_PRORATA',
   0.0000000000, NULL, NULL, NULL, NULL, NULL, NULL, @now, @now),
  (900000000001371042, 900000000001371032, 1, 'PLATFORM_SERVICE_CHARGE', 'PERCENTAGE', 'SALE', 'GROSS_AMOUNT', 'RETURN_PRORATA',
   0.0000000000, NULL, NULL, NULL, NULL, NULL, NULL, @now, @now);

-- 7b. 发布副本追加（D-4）：APPLE_PAY_STANDARD 的 AE001 / USD 价格，费率 0；priority 2（uk_term_price_priority 要求同版本同 OE 同币种不同）。
INSERT INTO t_commercial_term_price (term_price_id, term_version_id, entity_no, currency, pricing_profile_code, priority_no,
  settlement_business_days, business_calendar_code, settlement_timezone, cutoff_second_of_day, source_template_price_id,
  create_time, update_time)
VALUES
  (900000000001371033, 900000000001371021, 'AE001', 'USD', 'APPLE_PAY_STANDARD', 2, NULL, NULL, NULL, NULL, NULL, @now, @now),
  (900000000001371034, 900000000001371022, 'AE001', 'USD', 'APPLE_PAY_STANDARD', 2, NULL, NULL, NULL, NULL, NULL, @now, @now);

INSERT INTO t_commercial_term_component (term_component_id, term_price_id, component_no, component_code, component_type,
  trigger_code, calculation_base, refund_behavior, rate, fixed_amount, currency, min_amount, max_amount, band_basis, band_mode,
  create_time, update_time)
VALUES
  (900000000001371043, 900000000001371033, 1, 'PARTNER_SHARE', 'PERCENTAGE', 'SALE', 'GROSS_AMOUNT', 'RETURN_PRORATA',
   0.0000000000, NULL, NULL, NULL, NULL, NULL, NULL, @now, @now),
  (900000000001371044, 900000000001371034, 1, 'PLATFORM_SERVICE_CHARGE', 'PERCENTAGE', 'SALE', 'GROSS_AMOUNT', 'RETURN_PRORATA',
   0.0000000000, NULL, NULL, NULL, NULL, NULL, NULL, @now, @now);

-- 8. BCR：合同根、两个方案根（当前修订 1），两个版本作为方案根的受审计 Child（指向根修订 1）。不写快照，见文件头。
INSERT INTO t_business_change_record (history_id, record_type, record_id, aggregate_root_type, aggregate_root_id,
  parent_history_id, current_version, create_time, update_time)
VALUES
  (900000000001371051, 'AGREEMENT', '900000000001371001', 'AGREEMENT', '900000000001371001', NULL, 1, @now, @now),
  (900000000001371052, 'COMMERCIAL_TERM_PLAN', '900000000001371011', 'COMMERCIAL_TERM_PLAN', '900000000001371011', NULL, 1,
   @now, @now),
  (900000000001371053, 'COMMERCIAL_TERM_PLAN', '900000000001371012', 'COMMERCIAL_TERM_PLAN', '900000000001371012', NULL, 1,
   @now, @now),
  (900000000001371054, 'COMMERCIAL_TERM_VERSION', '900000000001371021', 'COMMERCIAL_TERM_PLAN', '900000000001371011',
   900000000001371052, 1, @now, @now),
  (900000000001371055, 'COMMERCIAL_TERM_VERSION', '900000000001371022', 'COMMERCIAL_TERM_PLAN', '900000000001371012',
   900000000001371053, 1, @now, @now);

COMMIT;

-- 执行后核对：
--   法律主体一致：SELECT party_role, party_id FROM t_agreement_party WHERE agreement_id = 900000000001371001;
--   （LOCAL_PARTNER 与 OPERATING_ENTITY 的 party_id 相同，且等于 client-master 中 AE001 与 LP-PAYFAC-AE001 的 Party）
--   直连 POST /checkout-context/v1/resolutions（X-Caller-Service: acquiring-service）解析 AE001 下的 USD 测试 Client：
--   sourceType=PARTNER，partnerTerms 的 shareVersionId / serviceChargeVersionId 为上面两个版本，relationshipSnapshot 带 PLATFORM 角色
--   且 partyId=@platform_party_id。
