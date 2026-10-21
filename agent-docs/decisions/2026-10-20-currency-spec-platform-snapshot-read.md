# 2026-10-20 决定：币种规则只由 Platform 持有，ledger / billing 读取进程内快照

状态：已定（2026-10-20，用户确认）。取代 [目标架构 V2 决定](./2026-10-17-target-architecture-v2.md) D41 中“会计与计费各持同结构只读副本”的部分；D41 的 Owner 归属（Platform 持有全平台参考数据并提供查询 API）不变。需求与方案见 [platform-support：币种主数据维护与读取缓存](../../requirements/20261020/platform-support-币种主数据维护与读取缓存/requirement.md)。

## 背景

D41 为保证“过账与舍入不依赖远程调用”，让 ledger、billing 各持一份 `t_currency_spec` 副本，同步机制留待后续。实际三处副本由同一份 SQL 初始化后彼此独立，Platform 也没有维护入口；一旦在 Platform 维护，副本必然漂移。

## 决定

1. **唯一来源：** `t_currency_spec` 只存在于 `mor_platform`。Operations 经「平台配置 → 币种」维护：新建草稿、修改草稿、启用、停用、再启用。变更由 Platform 再次校验 `CURRENCY_SPEC_MANAGE`，并写入统一审计 `t_audit_event`。
2. **启用后冻结：** 只有 DRAFT 可修改小数位与舍入方式；启用后冻结，币种代码不可变、不可删除。
3. **停用只拦新用途：** INACTIVE 只拦截新建账簿等新用途；已有账簿的过账、试算、关账与计费事实登记照常进行。DRAFT 不能用于任何用途。
4. **读取方式：** ledger、billing 经 `platform-support-api` 的 `CurrencySpecQueryApi` 读取全量币种，在进程内持有不可变快照，原子替换：
   - 启动即加载，首次成功前服务不就绪；
   - 就绪后每 60 秒全量刷新，失败保留上次快照；
   - 业务代码（含事务内）只读内存快照，从不在事务内回源；
   - 查不到时拒绝并请求刷新，由既有重试恢复。
5. **不用 Caffeine：** `paytech-caffeine-starter` 的过期即回源语义可能让回源发生在事务内，且 Platform 不可用时会丢失数据。币种数据量很小，用全量快照代替。
6. **定时方式：** 刷新使用进程内 `@Scheduled`，不用 XXL-Job，因为每个实例都必须刷新自己的内存。
7. **其他服务：** 今后其他服务需要币种时按同样方式读取，不建副本。

## 影响

- `mor_ledger`、`mor_billing` 删除 `t_currency_spec`；ledger 删除 `/currencies/v1`。Operations 财务页的币种列表改由 ops-bff 读 Platform。
- `mor_platform.t_currency_spec` 增加 `version`，用于维护并发保护。
- arch-rules 表归属、数据库文档与场景文档同步更新；实施与部署状态只记录在 `CURRENT_IMPLEMENTATION.md`。
