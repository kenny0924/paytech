# SQL 索引

> 执行状态：mor-test 已于 2026-10-20 21:00—21:02Z 按下表顺序执行（输出在发布备份目录，见 [release.md §3](../release/release.md)）；本机 `mor_commercial` 已在阶段 4 执行 ddl 供测试使用。

| 文件 | 目标库 | 产出线 | 顺序 | 回滚 |
|---|---|---|---|---|
| `commercial/ddl.sql` | mor_commercial（root） | A | 1 | 只 ADD / MODIFY；回滚为注释中的逆向 ALTER（新列可保留） |
| `commercial/migration.sql` | mor_commercial（commercial_app） | A | 2 | 发布前整库备份恢复（数据合并不可逆） |
| `platform-support-service/registration-dml.sql` | mor_platform（platform_app） | B | 3 | 删除块 124 行 |
| `platform-support-service/test-activate.sql` / `test-deactivate.sql` | mor_platform | B | 4 | deactivate |
