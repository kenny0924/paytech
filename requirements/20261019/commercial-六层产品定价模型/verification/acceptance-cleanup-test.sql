-- commercial 阶段 7 mor-test 验收合成数据清理（任务卡 G 第 8 步，只在 mor-test mor_commercial 执行，commercial_app，客户端须 utf8mb4）。
-- 运行记录：local-environment/e2e/runtime/commercial-stage7-20261019T214150Z.json（RUN 89BD2A）。
-- 按本次创建的 ID 删除：画像 2（条件 3）、合同 1（参与方 2、文件 1、产品 1、开通 1、开通约束 2）、方案 1、版本 1、价格 4、组件 12、
-- BCR 记录 7 / 版本 26；目录 t_product / t_product_capability / t_product_fee_event 不动。t_compensation_event 已为 0（发布成功即删除）。
-- 先 SELECT 核对（期望值写在列名中），再在同一事务内 DELETE 并逐条输出 ROW_COUNT()，最后核对非目录表全部 0 行。
-- Platform 侧的合成审批角色 / 人员已由验收脚本经 IAM 接口停用、清空权限；Platform 审计记录不回滚。
SET @profile_specific = 238054069984366592;
SET @profile_fallback = 238054070571569152;
SET @agreement_id = 238054071007776768;
SET @capability_id = 238054072266067968;
SET @plan_id = 238054073125900288;
SET @version_id = 238054073369169920;

-- 1. 核对
SELECT
 (SELECT COUNT(*) FROM t_pricing_profile WHERE pricing_profile_id IN (@profile_specific, @profile_fallback)) AS profiles_exp2,
 (SELECT COUNT(*) FROM t_pricing_profile_cond WHERE pricing_profile_id IN (@profile_specific, @profile_fallback)) AS conditions_exp3,
 (SELECT COUNT(*) FROM t_agreement WHERE agreement_id = @agreement_id AND agreement_no = 'G7-CLIENT-89BD2A') AS agreement_exp1,
 (SELECT COUNT(*) FROM t_agreement_party WHERE agreement_id = @agreement_id) AS parties_exp2,
 (SELECT COUNT(*) FROM t_agreement_document_ref WHERE agreement_id = @agreement_id) AS documents_exp1,
 (SELECT COUNT(*) FROM t_agreement_product WHERE agreement_id = @agreement_id) AS products_exp1,
 (SELECT COUNT(*) FROM t_agreement_capability WHERE agreement_id = @agreement_id) AS capabilities_exp1,
 (SELECT COUNT(*) FROM t_agreement_capability_limit WHERE agreement_capability_id = @capability_id) AS limits_exp2,
 (SELECT COUNT(*) FROM t_commercial_term_plan WHERE term_plan_id = @plan_id AND plan_code = 'G7-CLIENT-PRICING-89BD2A') AS plan_exp1,
 (SELECT COUNT(*) FROM t_commercial_term_version WHERE term_version_id = @version_id AND term_plan_id = @plan_id) AS version_exp1,
 (SELECT COUNT(*) FROM t_commercial_term_price WHERE term_version_id = @version_id) AS prices_exp4,
 (SELECT COUNT(*) FROM t_commercial_term_component c JOIN t_commercial_term_price p ON p.term_price_id = c.term_price_id
   WHERE p.term_version_id = @version_id) AS components_exp12,
 (SELECT COUNT(*) FROM t_business_change_record WHERE aggregate_root_id IN (@profile_specific, @profile_fallback, @agreement_id, @plan_id)) AS bcr_exp7,
 (SELECT COUNT(*) FROM t_business_change_version v JOIN t_business_change_record r ON r.history_id = v.history_id
   WHERE r.aggregate_root_id IN (@profile_specific, @profile_fallback, @agreement_id, @plan_id)) AS bcr_versions_exp26;

-- 2. 删除（子表先删）
START TRANSACTION;
DELETE v FROM t_business_change_version v JOIN t_business_change_record r ON r.history_id = v.history_id
 WHERE r.aggregate_root_id IN (@profile_specific, @profile_fallback, @agreement_id, @plan_id);
SELECT ROW_COUNT() AS bcr_versions;
DELETE FROM t_business_change_record WHERE aggregate_root_id IN (@profile_specific, @profile_fallback, @agreement_id, @plan_id);
SELECT ROW_COUNT() AS bcr_records;
DELETE b FROM t_commercial_term_component_band b JOIN t_commercial_term_component c ON c.term_component_id = b.term_component_id
 JOIN t_commercial_term_price p ON p.term_price_id = c.term_price_id WHERE p.term_version_id = @version_id;
SELECT ROW_COUNT() AS bands;
DELETE c FROM t_commercial_term_component c JOIN t_commercial_term_price p ON p.term_price_id = c.term_price_id
 WHERE p.term_version_id = @version_id;
SELECT ROW_COUNT() AS components;
DELETE FROM t_commercial_term_price WHERE term_version_id = @version_id;
SELECT ROW_COUNT() AS prices;
DELETE FROM t_commercial_term_version WHERE term_version_id = @version_id;
SELECT ROW_COUNT() AS versions;
DELETE FROM t_commercial_term_plan WHERE term_plan_id = @plan_id;
SELECT ROW_COUNT() AS plans;
DELETE FROM t_agreement_capability_limit WHERE agreement_capability_id = @capability_id;
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
DELETE FROM t_pricing_profile_cond WHERE pricing_profile_id IN (@profile_specific, @profile_fallback);
SELECT ROW_COUNT() AS conditions;
DELETE FROM t_pricing_profile WHERE pricing_profile_id IN (@profile_specific, @profile_fallback);
SELECT ROW_COUNT() AS profiles;
COMMIT;
