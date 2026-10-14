-- 初始化目录 A4 写入的内置 INTERNAL 租户不在测试独占表内，但测试会按同一 ID 插入；仅删除这一行（副本在 mor_platform_e2e）。
DELETE FROM t_access_tenant WHERE tenant_id=900000000000000500 AND tenant_type='INTERNAL';
