# channel-connector 第二段合表迁移的回退

适用于 [phase2-20-migrate.sql](phase2-20-migrate.sql)（目标库 `mor_channel_connector`）。

## 方式：迁移前整库备份恢复

迁移脚本含 `RENAME TABLE`、`ALTER TABLE`、`CREATE TABLE`、`DROP TABLE`，MySQL 的 DDL 会隐式提交，无法用事务回滚；中途失败时库可能停在
“部分表已改名或改列”的中间状态。因此不提供逆向 SQL，统一以迁移前的整库备份恢复。第 4 段数据合并虽在一个事务中，但其前后都是已提交的
DDL，单独回滚它没有意义。

## 执行前备份

```bash
set -a; . /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local; set +a   # 本机；mor-test 使用其 root 凭据来源
BACKUP=mor_channel_connector-before-phase2-$(date -u +%Y%m%dT%H%M%SZ).sql
docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 \
  mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF mor_channel_connector > "$BACKUP"
grep -c '^CREATE TABLE' "$BACKUP"   # 期望 28：迁移前的 28 张表
```

备份前先停止 channel-connector 写入（停实例或冻结配置写入与沙箱运行），确保备份与迁移之间没有新写入。

## 恢复

1. 停止 channel-connector（第二段镜像不能连接旧结构，旧镜像也不能连接新结构）。
2. 删除并重建库，再导入备份：

```bash
docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot \
  -e 'DROP DATABASE mor_channel_connector; CREATE DATABASE mor_channel_connector CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'
docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot mor_channel_connector < "$BACKUP"
```

   重建库不影响账号 `channel_connector_app` 的库级授权（`GRANT ... ON mor_channel_connector.*` 按库名保存）；恢复后用
   `SHOW GRANTS FOR 'channel_connector_app'@'%'` 确认。
3. 核对：表数为 28 且含 `t_payment_channel`、`t_integration`，关键表行数与 [phase2-10-precheck.sql](phase2-10-precheck.sql) 第 7 段一致。
4. 恢复第一段的 channel-connector 镜像与对应 ops-bff、ops-web 镜像。

## 不需要回退的部分

- `tmp_phase2_channel_map`、`tmp_phase2_row_count` 随整库恢复一并消失。
- Platform 注册数据（块 129）与 card 调用方收紧不在本库；如一并回退，按各自发布清单处理。
