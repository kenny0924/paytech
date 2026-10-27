# client-master SQL（参考数据迁移）

权威文件在服务目录（worktree `Code-MoR-refdata` 中，随代码一起提交），这里不放副本：

| 文件 | 内容 |
|---|---|
| `paytech/client-master/docs/database/sql/changes/2026-10-27-reference-data-catalog.sql` | DML：删除 `t_catalog_item` 中 48 行（INDUSTRY 10、CURRENCY 4、STATE_OR_PROVINCE 34）；8 版 Schema 的 `business.currency` 改为 `REF_CURRENCY`；地址组件 `stateOrProvince` 改为 `REF_COUNTRY_SUBDIVISION`；`t_party_address.state_or_province` 中 `^(AE\|EG)_[A-Z0-9]+$` 转为连字符格式。执行前后各输出一次计数 |
| `paytech/client-master/docs/database/sql/changes/2026-10-27-reference-data-catalog-rollback.sql` | 回退上面的 DML（恢复 48 行、改回目录码、地址值改回下划线格式），须与旧镜像一起执行 |
| `paytech/client-master/docs/database/sql/changes/2026-10-27-reference-data-catalog-comment.sql` | 仅注释 DDL：`t_catalog_item` 表注释与三处 `catalog_code` 列注释，与 schema / PDManer 一致 |
| `paytech/client-master/docs/database/sql/client-master-initial-data.sql` | 新环境初始化 DML 同步（目录 109 → 61 行，REF_ 引用） |

执行顺序：Platform 参考数据上线（AED / SAR / EGP 为 ACTIVE）→ 本 DML 与新 client-master 镜像同一窗口 → 注释 DDL（顺序不限）。
`2026-10-27-onboarding-country-pack.sql` 是历史增量，本 DML 之后不得重跑（会重新插入 48 行目录项）。

## 本地执行记录（2026-10-27，`mor_client_master` @ 127.0.0.1:13306）

执行前备份：`/Users/kenny/mor-backups/refdata-20261027/mor_client_master.sql.gz`（本地库此时各目录表为空，测试每例清空）。
为验证脚本，先装入改动前的初始化 DML，并插入 4 行合成地址（`AE_DU`、`EG_ALX`、`London`、小写 `ae_du`），再依次执行：

| 步骤 | retired_catalog_items | CURRENCY / REF_CURRENCY 字段 | STATE_OR_PROVINCE / REF_ 组件 | 下划线地址 | ISO 地址 |
|---|---|---|---|---|---|
| 执行前 | 48 | 8 / – | 2 / – | 2 | 0 |
| 执行后 | 0 | – / 8 | – / 2 | 0 | 2 |
| 重复执行 | 0 | 0 → 8（不变） | 0 → 2（不变） | 0 | 2 |
| 回退后 | 48 | 8 | 2 | 2 | – |
| 再次执行 | 0 | – / 8 | – / 2 | 0 | 2 |

`London` 与小写 `ae_du` 未被改动（匹配区分大小写）。验证后删除了 4 行合成地址；注释 DDL 已在本地执行。
