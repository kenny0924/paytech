-- channel-connector 第二段合表前检查（需求 requirements/20261020/channel-connector-统一对外渠道出口，技术方案第 5.2 节）。
-- 只读：全部为 SELECT，不改任何数据或结构；审阅后由 root 在目标库 mor_channel_connector 手工执行（迁移前，t_payment_channel 与
-- t_integration 仍存在、协议表仍为 t_integration_* 名称）。
-- 判定：第 1–5 段为阻断检查，每段都必须返回 0 行才能执行 phase2-20-migrate.sql（迁移脚本开头按同一条件重算并在不为 0 时报错终止）。
--   第 6 段是对应关系表（每个合并后渠道一行），供人工核对并在迁移后与 phase2-30-verify.sql 的结果比对；第 7 段为提示信息，不阻断。
-- 编码匹配不区分大小写：比较 UPPER() 后的字节（合法编码只含 ASCII，迁移后 channel_code 为 ascii_bin、大小写敏感）。

SET NAMES utf8mb4;
USE mor_channel_connector;

-- 1. 编码格式：两表编码都必须满足 [A-Z][A-Z0-9_]{1,63}（含非 ASCII、小写、单字符、超长）。期望 0 行。
SELECT 't_payment_channel' AS source_table, channel_id AS row_id, CONVERT(channel_code USING utf8mb4) AS code
FROM t_payment_channel
WHERE NOT REGEXP_LIKE(channel_code, '^[A-Z][A-Z0-9_]{1,63}$', 'c')
UNION ALL
SELECT 't_integration', integration_id, CONVERT(integration_code USING utf8mb4)
FROM t_integration
WHERE NOT REGEXP_LIKE(integration_code, '^[A-Z][A-Z0-9_]{1,63}$', 'c');

-- 2. 同表内大写后重复（t_integration 为 ascii_bin，可能存在仅大小写不同的两条）。期望 0 行。
SELECT 't_payment_channel' AS source_table, CONVERT(UPPER(channel_code) USING utf8mb4) AS upper_code, COUNT(*) AS row_count
FROM t_payment_channel
GROUP BY CAST(UPPER(channel_code) AS BINARY), CONVERT(UPPER(channel_code) USING utf8mb4)
HAVING COUNT(*) > 1
UNION ALL
SELECT 't_integration', CONVERT(UPPER(integration_code) USING utf8mb4), COUNT(*)
FROM t_integration
GROUP BY CAST(UPPER(integration_code) AS BINARY), CONVERT(UPPER(integration_code) USING utf8mb4)
HAVING COUNT(*) > 1;

-- 3. 跨表大小写重复：大写后相同、原编码字节不同的渠道与接入方（合并保留渠道编码，接入方编码的大小写会丢失）。期望 0 行。
SELECT p.channel_id, CONVERT(p.channel_code USING utf8mb4) AS channel_code,
       i.integration_id, CONVERT(i.integration_code USING utf8mb4) AS integration_code
FROM t_payment_channel p
JOIN t_integration i ON CAST(UPPER(p.channel_code) AS BINARY) = CAST(UPPER(i.integration_code) AS BINARY)
WHERE CAST(p.channel_code AS BINARY) <> CAST(i.integration_code AS BINARY);

-- 4. ID 冲突：合并后渠道 ID 重复，或某接入方的原 ID 等于另一个合并后渠道的 ID（引用改写会串号）。期望 0 行。
WITH matched AS (
    SELECT i.integration_id, p.channel_id
    FROM t_integration i
    JOIN t_payment_channel p ON CAST(UPPER(p.channel_code) AS BINARY) = CAST(UPPER(i.integration_code) AS BINARY)
), mapping AS (
    SELECT integration_id, channel_id FROM matched
    UNION ALL
    SELECT NULL, p.channel_id FROM t_payment_channel p WHERE p.channel_id NOT IN (SELECT channel_id FROM matched)
    UNION ALL
    SELECT i.integration_id, i.integration_id FROM t_integration i
    WHERE i.integration_id NOT IN (SELECT integration_id FROM matched)
)
SELECT 'DUPLICATE_CHANNEL_ID' AS conflict, channel_id AS id, COUNT(*) AS row_count
FROM mapping GROUP BY channel_id HAVING COUNT(*) > 1
UNION ALL
SELECT 'INTEGRATION_ID_TAKEN', m1.integration_id, 1
FROM mapping m1
JOIN mapping m2 ON m2.channel_id = m1.integration_id AND m2.channel_id <> m1.channel_id;

-- 5. 悬挂引用：协议与沙箱表的 integration_id 不在 t_integration。期望 0 行。
SELECT 't_integration_api' AS source_table, integration_api_id AS row_id, integration_id
FROM t_integration_api WHERE integration_id NOT IN (SELECT integration_id FROM t_integration)
UNION ALL
SELECT 't_integration_script', integration_script_id, integration_id
FROM t_integration_script WHERE integration_id NOT IN (SELECT integration_id FROM t_integration)
UNION ALL
SELECT 't_integration_standard_api', integration_standard_api_id, integration_id
FROM t_integration_standard_api WHERE integration_id NOT IN (SELECT integration_id FROM t_integration)
UNION ALL
SELECT 't_integration_verify_account', verify_account_id, integration_id
FROM t_integration_verify_account WHERE integration_id NOT IN (SELECT integration_id FROM t_integration)
UNION ALL
SELECT 't_integration_verify_case', verify_case_id, integration_id
FROM t_integration_verify_case WHERE integration_id NOT IN (SELECT integration_id FROM t_integration)
UNION ALL
SELECT 't_integration_verify_run', verify_run_id, integration_id
FROM t_integration_verify_run WHERE integration_id NOT IN (SELECT integration_id FROM t_integration);

-- 6. 对应关系表：每个合并后渠道一行。MATCHED 保留 t_payment_channel.channel_id，说明、出站地址、版本与操作人取自接入方；
--    CHANNEL_ONLY 无出站地址、版本 1；INTEGRATION_ONLY 新建 DRAFT 渠道，channel_id 沿用 integration_id。全部为 PAYMENT 类型
--    （INTEGRATION_ONLY 的类型如需改为 SERVICE，在发布清单中逐行确认后于迁移完成、启用前手工更正）。
WITH matched AS (
    SELECT i.integration_id, p.channel_id
    FROM t_integration i
    JOIN t_payment_channel p ON CAST(UPPER(p.channel_code) AS BINARY) = CAST(UPPER(i.integration_code) AS BINARY)
)
SELECT 'MATCHED' AS match_type, m.integration_id, m.channel_id AS target_channel_id,
       CONVERT(p.channel_code USING utf8mb4) AS channel_code, p.channel_status AS target_status,
       JSON_LENGTH(i.endpoints) AS endpoint_count
FROM matched m
JOIN t_payment_channel p ON p.channel_id = m.channel_id
JOIN t_integration i ON i.integration_id = m.integration_id
UNION ALL
SELECT 'CHANNEL_ONLY', NULL, p.channel_id, CONVERT(p.channel_code USING utf8mb4), p.channel_status, NULL
FROM t_payment_channel p WHERE p.channel_id NOT IN (SELECT channel_id FROM matched)
UNION ALL
SELECT 'INTEGRATION_ONLY', i.integration_id, i.integration_id, CONVERT(i.integration_code USING utf8mb4), 'DRAFT',
       JSON_LENGTH(i.endpoints)
FROM t_integration i WHERE i.integration_id NOT IN (SELECT integration_id FROM matched)
ORDER BY channel_code;

-- 7. 提示（不阻断）：迁移前各表行数，以及无法映射到现存接入方的历史 INTEGRATION 变更记录（接入方已删除；迁移后保留 INTEGRATION 原值）。
SELECT 't_payment_channel' AS table_name, COUNT(*) AS row_count FROM t_payment_channel
UNION ALL SELECT 't_integration', COUNT(*) FROM t_integration
UNION ALL SELECT 't_integration_api', COUNT(*) FROM t_integration_api
UNION ALL SELECT 't_integration_script', COUNT(*) FROM t_integration_script
UNION ALL SELECT 't_integration_standard_api', COUNT(*) FROM t_integration_standard_api
UNION ALL SELECT 't_integration_verify_account', COUNT(*) FROM t_integration_verify_account
UNION ALL SELECT 't_integration_verify_case', COUNT(*) FROM t_integration_verify_case
UNION ALL SELECT 't_integration_verify_run', COUNT(*) FROM t_integration_verify_run
UNION ALL SELECT 't_integration_verify_run_step', COUNT(*) FROM t_integration_verify_run_step
UNION ALL SELECT 't_channel_config_change', COUNT(*) FROM t_channel_config_change;

SELECT target_type, COUNT(*) AS row_count,
       SUM(target_type = 'INTEGRATION' AND target_id NOT IN (SELECT integration_id FROM t_integration)) AS unmapped_integration_rows
FROM t_channel_config_change
WHERE target_type IN ('INTEGRATION', 'INTEGRATION_API', 'INTEGRATION_SCRIPT', 'INTEGRATION_IMPLEMENTATION')
GROUP BY target_type;
