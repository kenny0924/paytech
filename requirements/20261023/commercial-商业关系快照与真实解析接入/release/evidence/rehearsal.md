# 离线 SQL 演练（阶段 6）

> 2026-10-25T11:41Z。没有连接 mor-test：mor-test 六个库的只读 mysqldump（11:12:17Z，见 [data-inventory.md](./data-inventory.md) 头部）
> 导入一次性容器 `relsnap-stage6-rehearsal-mysql`——`docker run --network none`，镜像与 mor-test 同一 ID
> `sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb`（mysql 8.4.11），参数相同
> （`--character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --default-time-zone=+00:00 --max-connections=400`），
> root 口令 throwaway。演练完毕容器已删除（`docker rm -f`，`docker ps -a` 中无 relsnap-stage6 容器）。
> 驱动：[scripts/rehearse_offline.sh](../scripts/rehearse_offline.sh)，经 [scripts/apply_sql.sh](../scripts/apply_sql.sh) `--apply` 逐步执行
> （覆盖变量 `RELSNAP_MYSQL_CONTAINER` / `RELSNAP_MYSQL_PWD` 只用于演练）。完整输出：
> [rehearsal/rehearsal-log.txt](./rehearsal/rehearsal-log.txt)、[rehearsal/compare-with-backup.txt](./rehearsal/compare-with-backup.txt)、
> [rehearsal/variant-a-official-files-only.txt](./rehearsal/variant-a-official-files-only.txt)。

## 1. Gateway 快照编译器

platform-support 本次不换镜像。编译器取 mor-test 现行 Platform 镜像 `4859efd7…` 中的 `application.jar`（用 `docker create` 建临时容器
复制出来再删除，未触碰运行中的容器），SHA-256 `be0abf79fc3ca9eb…`，与文件上传入口发布时的候选 JAR 相同；`git log 3baef5f0..8a86a31c --
paytech/platform-support paytech/foundation` 为空，worktree 对 platform-support 只改了 initial-data SQL，所以它就是 main 源码的编译器。
检查程序 [snapshot/check.sh](./snapshot/check.sh) + [snapshot/SnapshotCheck.java](./snapshot/SnapshotCheck.java) 复制自文件上传入口发布包，
只把统计的路由前缀换成块 137（`operations-operating-entity-` / `operations-local-partner-` / `operations-qualification-`）。

| 状态 | 结果 |
|---|---|
| 基线（备份原样） | COMPILE OK：loaded 579、effective 574、block137 0 |
| 休眠（60 注册后） | COMPILE OK：loaded 590、effective 574、block137 0 |
| 激活（61 后） | COMPILE OK：loaded 590、effective 585、block137 11 |
| 停用（62 后） | COMPILE OK：loaded 590、effective 574、block137 0 |
| 回滚（69 后） | COMPILE OK：loaded 579、effective 574、block137 0 |

## 2. 发布顺序执行（全部成功）

| 步骤 | 文件 | 结果 |
|---|---|---|
| precheck | 00 | 全部与盘点一致（compliance 37 / 3 决定、投影空；client-master OE 相关 0 行；46 / 46 PLATFORM；块 137 空闲；角色 510 v49；路由 574 / 579；多语言 #7 / 21） |
| 迁移前结构基线 | `schema_drift.py <容器> 8a86a31c` | 备份库对 main 8a86a31c 的四个 schema 文件：**漂移 0** |
| cmp-ddl / cmp-backfill（先 dry 再 --apply） | 10 / 11 | 37 个 Client 投影 APPROVED / NOT_REQUIRED + 37 条 BACKFILL 状态变更；3 个只有 REJECTED 的 Client 不回填 |
| cm-ddl / cm-seed（dry + apply）/ cm-data / cm-ac11（dry + apply） | 20 / 21 / 22 / 23 | AE001 迁为 PARTNER → PayFac 900000000001370001（Party = AE001 Party 880000000702）；OE_PROVIDER 1、party_role CLIENT 54 / OE 1 / OE_PROVIDER 1；AE001 根 v2、PayFac 根 v1；23 两行各 1 |
| 防重入 | 再次 `--apply` 21、23 | 均在写入前 SIGNAL 中止（“client-master already has OE / partner master data”、“AC-11 client profile state differs”） |
| com-fallback（dry + apply） | 30 | 合同 1、方案 2（兜底路径语法与约束验证；阶段 7 首选运营 API） |
| acq-ddl / acq-platform | 40 / 41 | 新表与三列（`effective_relationship_model_version` NOT NULL、存量 0）；46 条订单改 PARTNER + PayFac |
| risk | 50 | 46 条快照改写；目录行剩 2 |
| plt-reg（dry + apply）/ plt-activate | 60 / 61 | 150 行；激活 11 / 6 / 6 / 1；角色 510 v50 |
| postcheck | 99 | 全部等于期望值（见日志第 214—312 行） |
| 迁移后结构核对 | `schema_drift.py <容器>` | 迁移后四个库对 **worktree** schema 文件（列类型 / 可空 / 缺省 / 字符集 / 注释、索引、表注释）：**漂移 0** |

## 3. 回滚（逆序，全部成功）

| 步骤 | 文件 | 影响行数 |
|---|---|---|
| plt-deactivate / plt-rollback | 62 / 69 | 11 / 6 / 6 / 1；删除 66 / 17 / 38 / 11 / 6 / 6 / 6，角色 +1 |
| rb-risk | 89（`@relsnap_rewrite_time` 填 50 的输出 `2026-10-25 11:41:46.074839`） | 目录行恢复 1、快照改回 46 |
| rb-acq-platform / rb-acq-ddl | 87 / 88 | 订单改回 46；删表删列、表注释恢复 |
| rb-com-fallback | 86 | 合同 1 及其子行 |
| rb-cm-ac11 / rb-cm-data / rb-cm-seed / rb-cm-ddl | 82 / 83 / 84 / 85 | 资料恢复 1 / 1；party_role 56、partner_role 1、申请 1、OE 根 −1、来源 1、BCR 1、LP 1；种子 Affiliation 7、OE 1、Party 2；删 3 表与列、注释恢复 |
| rb-cmp-backfill / rb-cmp-ddl | 80 / 81 | 投影 37、状态变更 37；结构改回 BIGINT / NOT NULL、删 basis_note |
| precheck | 00 | 与基线相同（角色 510 v52） |

**与备份逐表比对**（[compare-with-backup.txt](./rehearsal/compare-with-backup.txt)：备份另导为 `base_<库>`，比较 SHOW CREATE TABLE 与按主键排序的全行内容，忽略 `update_time`）：
六个库 216 张表结构全部相同；数据只有 **1 处差异：mor_platform.t_role**——PLATFORM_ADMIN 版本 49 → 52（激活、停用、回滚各 +1，属设计行为，用于让会话重算授权）。
另有 mor_client_master 残留 2 个存储过程 `relsnap_seed_assert`、`relsnap_ac11_assert`：来自上面故意触发的防重入中止（SIGNAL 后 mysql 客户端退出，文件末尾的 DROP 没有执行）。
**阶段 7 若某个带断言的文件中止，先 `DROP PROCEDURE IF EXISTS relsnap_…` 再排查**（文件开头也会先 DROP，重跑不受影响）。

## 4. 变体 A：只执行正式文件（不执行 21 种子）

[variant-a-official-files-only.txt](./rehearsal/variant-a-official-files-only.txt)：在回滚后的副本上只跑 20、22——成功、不报错，但 `t_local_partner` 0、`t_partner_role` 0、
`t_operating_entity` 0，只有 54 条 CLIENT 角色行。即在 mor-test 现状下不加 21，PayFac Partner 不会建立（盘点 F-1）；此时若照常执行 41 / 50，订单与快照会指向不存在的 Partner
（R-6）。变体 A 的回滚（83、85）也成功，结束后表数 29、无残留过程。

## 5. 未覆盖

- 只验证了 SQL 与快照编译，没有用新镜像在副本上跑应用（应用侧在阶段 5 本地验证；阶段 7 以真实镜像与运营 API 验收）。
- 运营 API 建 PayFac 合同、资格手工登记、多语言导入与发布不在 SQL 演练范围（阶段 7）。
- 41 / 50 的改写对象是备份时刻的 46 / 46 行；阶段 7 执行前若 mor-test 又新增 PLATFORM 订单（旧 acquiring 仍在运行），00-precheck 的计数会不同，先重新盘点。
- 备份之后 mor-test 上其他会话的写入不在副本里；阶段 7 第一步重新备份并再跑 00-precheck。
- SQL 文件 00-precheck.sql 在演练后只改了一处标签文字（最早签署时间），已在同一副本上重跑通过（`precheck-final-label`）。

## 6. 文件清单与校验

`release/sql/SHA256SUMS`（26 个 SQL）。与 worktree 源文件逐字相同（`cmp`）：10、11、20、22、40、60、61、62；41 / 50 = 源文件 + 开头三行（mor-test 变量与改写时刻输出）；
30 = 草稿 + 开头 mor-test 变量；21、23、69、80—89、00、99 为本发布包新写。

## 7. 补充复演（D-4 确认后）

30 的发布副本追加 APPLE_PAY_STANDARD 两条 0 费率价格（priority 2）后，在新的断网一次性容器（同一镜像 ID）只导入 mor_commercial 备份复演：
dry → `--apply` → 核对（两个方案各 CARD_STANDARD / APPLE_PAY_STANDARD、组件费率 0，三个签约方正确）→ 86 回滚（号段内残留 0 行）。
记录：[rehearsal/sql30-apple-pay-rerun.txt](./rehearsal/sql30-apple-pay-rerun.txt)；容器已删除；`sql/SHA256SUMS` 已更新。
