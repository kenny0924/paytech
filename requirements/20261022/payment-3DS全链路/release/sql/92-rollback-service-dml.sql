-- 回滚（数据，非 Platform 三库）：分三段，各自单独执行，一般不执行。语句取自各来源文件头的回退说明。
-- 段 A 与段 C 仅在没有业务数据依赖时可执行；mor-test 发布后验收会在 BIN 库新增版本、在 Risk 写入引用 220003 的发布，届时不要执行。

-- ===== 段 A：mor_card（17 文件头回退；须先回退 Card 镜像；只删种子版本，运营导入的版本另行处理）=====
-- DELETE FROM t_card_bin_library_activation WHERE activation_id=1201;
-- DELETE FROM t_card_bin_range WHERE version_id=1001;
-- DELETE FROM t_card_bin_library_version WHERE version_id=1001;

-- ===== 段 B：mor_channel_connector（21 文件头回滚；可在任何时刻执行，执行后该渠道入站通知返回 404）=====
-- UPDATE t_channel SET callback_definition_json = NULL, version = version + 1, update_time = UTC_TIMESTAMP(6)
--  WHERE channel_code IN ('PAYERMAX', 'PAYERMAX_MOCK');

-- ===== 段 C：mor_risk_decision（10 文件头回退；须先回退 Risk 镜像，并确认没有规则引用三个变量、没有执行发布引用 220003）=====
-- SELECT COUNT(*) FROM t_decision_release WHERE point_version_id = 220003;   -- 须为 0
-- DELETE FROM t_risk_variable WHERE variable_id IN (900000000000093014, 900000000000093015, 900000000000093016);
-- DELETE FROM t_decision_point_version WHERE point_version_id = 220003;
