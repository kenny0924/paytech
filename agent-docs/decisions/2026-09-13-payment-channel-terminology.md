# 全系统支付渠道统一使用 Channel

- Decision status: Confirmed
- Decision date: 2026-09-13
- Superseded sections: 现行文档、模型与自有合同中表示支付渠道的 Provider 命名，以及模块方案中仅改 JAR 名而保留支付 Provider 实体名的限制；不取代业务事实或历史协议的解释。
- 2026-10-20 后继："Channel 仅指支付渠道、非支付供应商保留 Provider"的边界由[2026-10-20 统一对外出口决定](./2026-10-20-channel-connector-unified-outbound.md)取代：渠道分支付渠道（`PAYMENT`）与服务渠道（`SERVICE`）；本决定的支付命名迁移结果不变。

用户确认范围覆盖所有系统中的支付渠道命名，并在审阅[跨系统清单](../../requirements/20260913/payment-支付渠道命名统一/technique.md)后要求“改一下”，授权本地文档、模型、SQL 文件、代码与测试同步更名。实际实现、验证和环境迁移状态仅见[当前台账](../CURRENT_IMPLEMENTATION.md)，交付证据见[需求记录](../../requirements/20260913/payment-支付渠道命名统一/delivery.md)。

## 决定

Payment Channel/支付渠道是 NI、Geidea 等支付渠道及其能力的统一项目术语。支付领域自有 Provider/provider/PROVIDER 名称分别使用 Channel/channel/CHANNEL；具体表、列、索引及物理长名缩写按[已评审映射](../../requirements/20260913/payment-支付渠道命名统一/rename-map.json)。四个 Payment 业务模块仍是 Execution、Routing、Channel、Card。

Payment、Finance、Risk 的渠道实体/引用/目录、渠道争议事件，以及 Platform/Gateway/Operations 的回调代码统一迁移。公共回调 Route Channel、Security Profile 和 Authorization Mode 均为 CHANNEL_CALLBACK；六类 Route 的职责、数量、匹配规则与授权边界不变。

名称变化不改变业务 ID、外部交易值、金额、状态机、幂等身份或所有权。Payment 的 Channel Account 是技术 API 账户，Finance 的 Channel Account 是本地结算核算身份，两者仍不同。MID 归属固定 OE；Payment Method 与 Platform 访问 Channel 不与 Payment Channel 合并。原始卡处理继续限定于 Card 专项批准边界。

## 排除和兼容

- 框架/库及技术能力中的 Provider（JUnit、Spring、MyBatis、JWKS、RetryAfter、React Context 等）保持。
- OAuth、通知、对象存储、尽调/筛查等非支付供应商 Provider 保持。
- 外部固定协议字段、SDK 包名、原始签名报文和既有回调 URL 保持；只在适配边界转为本地 Channel 命名。
- 历史决定、执行记录、SQL、旧消息/快照及 Hash 不批量改写；必要的旧名只在明确的历史/迁移边界保留。新实现不得据旧文档重新引入现行支付 Provider 命名。
- 本次只修改本地工件。未执行物理数据库迁移、配置外部渠道、提交、发布或部署。已部署的旧枚举/数据与新制品的切换仍须按环境清单验证，不从本地改名推断已完成迁移。

PDManer 内部 ID、关系和非命名结构保持；生成器可以保留只用于稳定建模身份的旧名映射，该映射不代表运行期接受旧业务名称。全仓库 Provider 搜索零命中不是验收标准。
