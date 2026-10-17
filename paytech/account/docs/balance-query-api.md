# Client / Local Partner 余额查询接口

状态：HTTP、Domain Service、Repository、MyBatis 与 MySQL 只读链路已经实现。接口仅监听 Finance 内部地址，尚未接入 Gateway 和统一资源授权，
因此不能作为公网或租户直连接口发布。

## 接口

对应 FIN-I01、FIN-I02，定义于 `BalanceQueryApi`。路径为 Finance 内部服务路径，不创建公网 Gateway 路由；GET 参数走
Path/Query，没有请求体。

```http
GET /balances/v1/clients/1001?entityNo=AE001&currency=USD
GET /balances/v1/partners/3001?entityNo=AE001&currency=USD
```

每次只查询一个 Owner + Operating Entity + Currency，不允许省略 OE/币种后合计，也不查询平台 FE 利润。Owner ID 必须是 BIGINT 范围内的
正十进制字符串；OE 使用 entityNo（两位国家码加 `001`—`999`，如 `AE001`），与账簿 `entity_id`、账户 `owner_id` 的字符串身份一致，
不再接受旧的数字 `operatingEntityId`（2026-10-15 不兼容调整，当时无 Gateway Route 或 BFF 消费者）。币种必须是 JDK 识别的大写
ISO 4217 Code，且指定 OE/币种必须存在账簿（唯一键保证唯一）。

## Client 响应示例

```json
{
  "clientId": "1001",
  "entityNo": "AE001",
  "currency": "USD",
  "pendingAmount": {"currency": "USD", "amount": "100"},
  "availableAmount": {"currency": "USD", "amount": "70"},
  "lockedAmount": {"currency": "USD", "amount": "30"},
  "securityDepositAmount": {"currency": "USD", "amount": "500"},
  "recoveryReceivableAmount": {"currency": "USD", "amount": "0"}
}
```

## Partner 响应示例

```json
{
  "partnerId": "3001",
  "entityNo": "AE001",
  "currency": "USD",
  "pendingAmount": {"currency": "USD", "amount": "10"},
  "availableAmount": {"currency": "USD", "amount": "20"},
  "lockedAmount": {"currency": "USD", "amount": "5"},
  "recoveryReceivableAmount": {"currency": "USD", "amount": "2"}
}
```

金额在 Java 中使用公共 Money，JSON 包含 currency 与普通十进制 amount 字符串，单位是主货币单位而不是分。每项余额必填且与顶层查询币种一致。尾随零不保留，展示补零由前端处理；Money 不自动舍入。不取绝对值、不把追偿改写为负的应付，也不计算误导性的总余额。正常追偿应收用正金额表示，产品侧可以标记为“待补足/Negative”。

## 已实现的读取语义

- Client 三种应付来自 CLIENT_PAYABLE 的 PENDING/AVAILABLE/LOCKED；保证金来自 CLIENT_SECURITY_DEPOSIT/NONE，追偿来自
  CLIENT_RECOVERY_RECEIVABLE/NONE。Partner 使用对应 PARTNER_PAYABLE 和 PARTNER_RECOVERY_RECEIVABLE/NONE。
- Owner 的全部账户在一条 SQL 中读取，同一语句天然使用一个一致快照，不混用变更前后余额；账簿身份创建后不可变，因此不另开只读事务。读取不修复、不创建账户，也不触发过账。
- 指定 Owner 在有效账簿中尚未建立某个目标账户时，该分类返回同币种零；账簿不存在或不唯一、币种非法、数据库失败不能伪装成零。
- 不过滤掉有余额的停用账户来伪造零余额；账户状态不改变既有账务事实。
- 科目悬空、非叶子科目、账户与科目正常方向不一致、跨 Chart 或目标账户重复均 Fail-Closed，不返回可能错误的余额。
- Available 不是出款资格，不查询实时 Risk、不返回可出款承诺或最低保证金要求。出款四检查点保持独立。
- 成功响应直接返回上述对象。非法参数和不存在的账簿返回 Foundation 400 错误体；投影损坏和数据库错误返回安全的 500，内部细节不外显。

## 发布前仍需完成

- 接入统一入站安全上下文并执行 Owner/OE 资源授权；路径 ID 不是身份凭证。Client/Partner 只能读取获授权的自身数据，Operations 需要明确数据范围。
- 验证敏感财务数据的访问审计和 metadata-only 日志策略，并由 Gateway/BFF 只暴露符合产品身份模型的入口。
- 数据库结构来自完整 `finance-schema.sql` 与 `finance-initial-data.sql`，由开发者人工执行；应用和自动测试都不会自动建表。
