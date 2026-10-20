# counterparty V1 PDManer 物理建模决策

> **2026-10-19 改名：** 服务由 consumer 改名为 counterparty（库 `mor_counterparty`），表结构、字段与注释不变；下文"consumer"指改名前的服务名。
>
> **2026-10-18 新建（V2 第 03 步）：** commerce-service 拆为 acquiring 与 consumer 两服务两库（[任务卡](../../../../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/03-acquiring-consumer.md)、design/03 §2.5、D42、S6）。本模型 `counterparty.pdma.json` 由 `scripts/pdmaner/generate-counterparty-model.mjs` 从 Commerce 家族共享清单（`commerce-model-core.mjs`）生成；`t_customer` 的字段、索引与注释与拆分前 Commerce 模型逐字一致，只有 PDManer 内部 ID 按共享清单的确定性规则重新生成。

## 1. 范围

- mor_counterparty 只有两张表：`t_customer` 与本库 `t_compensation_event`（`STORED_CARD_INVALIDATION`）。
- `t_client_stored_card_setting` 归 client-master（D42）；存卡资格由 counterparty 组合本库 Customer 与 client-master `GET /client-master/v1/clients/{clientId}/stored-card-setting`，不复制开关。
- `customer_id` 与 `customer_no` 全局稳定（S6）：acquiring 订单只存 `customer_id` 软引用，Payment 已存卡以 `customer_id` 为键并以 `customer_no` 作为付款人引用；不建立跨库关系。
- 不含 `t_command_idempotency`（S10）；不新增 Vault Token、Blind Index 或消费者资料表（按订单的消费者上下文仍在 acquiring 的 `t_consumer_context`）。

## 2. 已冻结物理 Manifest

<!-- COUNTERPARTY_PHYSICAL_MANIFEST_START -->
```json
{
  "modelTotals": {
    "tables": 2,
    "fields": 25,
    "uniqueIndexes": 3,
    "accessIndexes": 2,
    "relations": 0,
    "diagramGroups": 2
  },
  "templateSha256": "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540",
  "modelSha256": "1636b3f06df936e880b070a31dfe3227310ade50e541bbd8ccf84a0436b13555",
  "structuralProjectionSha256": "efbebd9b370dc4fc85f61d32d2c6a49b31b870fb6332a0a65e71a8fc5a7b1cae"
}
```
<!-- COUNTERPARTY_PHYSICAL_MANIFEST_END -->

两个 Diagram Group 为 `Customer` 与 `可靠执行`。

## 3. 逐表场景映射

| 表 | 示例 | 写入 / 更新 / 冻结语义 |
|---|---|---|
| `t_customer` | Client `NOVA` 的已登录用户 `user-1001` 首次建单或经 Client API 创建，得到 `cus_` 前缀的公开编号 | 建或取按 `uk(client_id, active_reference)` 幂等，撞唯一键重读获胜行；删除时同一事务置 `DELETED`、清空 `active_reference` 并登记失效任务，不可撤销，同一引用再次使用新建 Customer |
| `t_compensation_event` | 删除 `cus_` Customer 后登记 `STORED_CARD_INVALIDATION`，事件键为 Customer 主键 | 与删除同事务写入，提交后由 Foundation 唤醒并在事务外调用 Payment 失效全部已存卡；Payment 暂不可用时保留重试，Customer 不存在或未删除时终止 |

## 4. 通用物理规则

与 [acquiring 物理建模决策](../../../acquiring/docs/database/ACQUIRING_PHYSICAL_DECISIONS.md) 第 3、4 节相同：完整克隆共享模板并清空连接、确定性 UUID、`t_` 表名与 `uk_` / `idx_` 索引、Snowflake `BIGINT` 主键、UTC `DATETIME(6)`、不生成物理 CHECK / FK。`t_customer` 使用 `version` 乐观锁与双审计时间；`t_compensation_event` 只使用技术 `lock_version`。

## 5. 实施门禁

PDManer 模型是设计源，人工评审并手工执行的 `sql/counterparty-schema.sql` 是可执行定义；测试库 DDL `paytech-counterparty-test/src/test/resources/init/ddl.sql` 逐表逐字切片自该 Schema。禁止 Flyway、Liquibase、Testcontainers 或应用启动时迁移。
