-- commercial 第二切片阶段 7 mor-test 验收合成数据清理（只在 mor-test mor_commercial 执行，commercial_app，客户端须 utf8mb4）。
-- 运行记录：local-environment/e2e/runtime/commercial-ops2-stage7-20261020T054329Z.json（RUN 395AEC）。
-- 只删本次合成的画像 3、合同 G8-CLIENT-395AEC、方案 G8-CLIENT-PRICING-395AEC（V1 APPROVED、V2 ACTIVE）及其子表与 BCR；
-- 真实合同 CL-F1105E（238132079668301824）及其 BCR 3 / 9 行、目录 7 / 11 / 21 行不动。t_compensation_event 已为 0。
-- 先 SELECT 核对（期望值写在列名中），再在同一事务内 DELETE 并逐条输出 ROW_COUNT()；清理后逐表行数应等于
-- 备份目录 commercial-rowcounts-before.tsv（~/Code-MoR-backups/20261020T053437Z-commercial-ops2-test/）。
-- Platform 侧的合成人员 2 个已由验收脚本经 IAM 接口停用、合成角色 2 个已清空权限；Platform 审计不回滚。
SET @profile_specific = 238175279422443520;
SET @profile_fallback = 238175279816708096;
SET @profile_applepay = 238175280043200512;
SET @agreement_id = 238175280441659392;
SET @plan_id = 238175282387816448;
SET @v1 = 238175282534617088;
SET @v2 = 238175283763548160;

-- 1. 核对
SELECT
 (SELECT COUNT(*) FROM t_pricing_profile WHERE pricing_profile_id IN (@profile_specific, @profile_fallback, @profile_applepay)) AS profiles_exp3,
 (SELECT COUNT(*) FROM t_pricing_profile_cond WHERE pricing_profile_id IN (@profile_specific, @profile_fallback, @profile_applepay)) AS conditions_exp3,
 (SELECT COUNT(*) FROM t_agreement WHERE agreement_id = @agreement_id AND agreement_no = 'G8-CLIENT-395AEC') AS agreement_exp1,
 (SELECT COUNT(*) FROM t_agreement_party WHERE agreement_id = @agreement_id) AS parties_exp2,
 (SELECT COUNT(*) FROM t_agreement_document_ref WHERE agreement_id = @agreement_id) AS documents_exp1,
 (SELECT COUNT(*) FROM t_agreement_product WHERE agreement_id = @agreement_id) AS products_exp1,
 (SELECT COUNT(*) FROM t_agreement_capability WHERE agreement_id = @agreement_id) AS capabilities_exp2,
 (SELECT COUNT(*) FROM t_agreement_capability_limit l JOIN t_agreement_capability c ON c.agreement_capability_id = l.agreement_capability_id
   WHERE c.agreement_id = @agreement_id) AS limits_exp3,
 (SELECT COUNT(*) FROM t_agreement_operating_entity WHERE agreement_id = @agreement_id) AS operating_entities_exp0,
 (SELECT COUNT(*) FROM t_commercial_term_plan WHERE term_plan_id = @plan_id AND plan_code = 'G8-CLIENT-PRICING-395AEC') AS plan_exp1,
 (SELECT COUNT(*) FROM t_commercial_term_version WHERE term_version_id IN (@v1, @v2) AND term_plan_id = @plan_id) AS versions_exp2,
 (SELECT COUNT(*) FROM t_commercial_term_price WHERE term_version_id IN (@v1, @v2)) AS prices_exp9,
 (SELECT COUNT(*) FROM t_commercial_term_component c JOIN t_commercial_term_price p ON p.term_price_id = c.term_price_id
   WHERE p.term_version_id IN (@v1, @v2)) AS components_exp27,
 (SELECT COUNT(*) FROM t_business_change_record
   WHERE aggregate_root_id IN (@profile_specific, @profile_fallback, @profile_applepay, @agreement_id, @plan_id)) AS bcr_exp10,
 (SELECT COUNT(*) FROM t_business_change_version v JOIN t_business_change_record r ON r.history_id = v.history_id
   WHERE r.aggregate_root_id IN (@profile_specific, @profile_fallback, @profile_applepay, @agreement_id, @plan_id)) AS bcr_versions_exp42;

-- 2. 删除（子表先删）
START TRANSACTION;
DELETE v FROM t_business_change_version v JOIN t_business_change_record r ON r.history_id = v.history_id
 WHERE r.aggregate_root_id IN (@profile_specific, @profile_fallback, @profile_applepay, @agreement_id, @plan_id);
SELECT ROW_COUNT() AS bcr_versions;
DELETE FROM t_business_change_record WHERE aggregate_root_id IN (@profile_specific, @profile_fallback, @profile_applepay, @agreement_id, @plan_id);
SELECT ROW_COUNT() AS bcr_records;
DELETE b FROM t_commercial_term_component_band b JOIN t_commercial_term_component c ON c.term_component_id = b.term_component_id
 JOIN t_commercial_term_price p ON p.term_price_id = c.term_price_id WHERE p.term_version_id IN (@v1, @v2);
SELECT ROW_COUNT() AS bands;
DELETE c FROM t_commercial_term_component c JOIN t_commercial_term_price p ON p.term_price_id = c.term_price_id
 WHERE p.term_version_id IN (@v1, @v2);
SELECT ROW_COUNT() AS components;
DELETE FROM t_commercial_term_price WHERE term_version_id IN (@v1, @v2);
SELECT ROW_COUNT() AS prices;
DELETE FROM t_commercial_term_version WHERE term_version_id IN (@v1, @v2) AND term_plan_id = @plan_id;
SELECT ROW_COUNT() AS versions;
DELETE FROM t_commercial_term_plan WHERE term_plan_id = @plan_id;
SELECT ROW_COUNT() AS plans;
DELETE l FROM t_agreement_capability_limit l JOIN t_agreement_capability c ON c.agreement_capability_id = l.agreement_capability_id
 WHERE c.agreement_id = @agreement_id;
SELECT ROW_COUNT() AS capability_limits;
DELETE FROM t_agreement_capability WHERE agreement_id = @agreement_id;
SELECT ROW_COUNT() AS capabilities;
DELETE FROM t_agreement_product WHERE agreement_id = @agreement_id;
SELECT ROW_COUNT() AS products;
DELETE FROM t_agreement_document_ref WHERE agreement_id = @agreement_id;
SELECT ROW_COUNT() AS documents;
DELETE FROM t_agreement_party WHERE agreement_id = @agreement_id;
SELECT ROW_COUNT() AS parties;
DELETE FROM t_agreement_operating_entity WHERE agreement_id = @agreement_id;
SELECT ROW_COUNT() AS operating_entities;
DELETE FROM t_agreement WHERE agreement_id = @agreement_id;
SELECT ROW_COUNT() AS agreements;
DELETE FROM t_pricing_profile_cond WHERE pricing_profile_id IN (@profile_specific, @profile_fallback, @profile_applepay);
SELECT ROW_COUNT() AS conditions;
DELETE FROM t_pricing_profile WHERE pricing_profile_id IN (@profile_specific, @profile_fallback, @profile_applepay);
SELECT ROW_COUNT() AS profiles;
COMMIT;
