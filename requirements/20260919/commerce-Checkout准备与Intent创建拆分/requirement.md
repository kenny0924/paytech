# Checkout 创建初始化与首次支付 Intent

用户于 2026-09-19 最终确认：删除独立准备阶段和 prepareCheckout 接口；创建 Checkout 时完成 OE 等信息初始化，Payment Intent 在首次提交支付时创建。本轮只改 Commerce，外部 Client 暂用固定 Mock。

- 创建 Checkout 时完成商业、风险资格引用、税额及法律资料初始化，再原子保存订单和相关事实。首次加载只读展示，不返回 PREPARE 操作，也不保留 preparation 兼容入口。
- 初始化完成不依赖 Payment Intent；同一 Order 对应零或一个 Intent。首次提交支付创建，后续提交复用，创建响应未知后仍按原业务身份取得原 Intent。
- 外部调用不得进入数据库事务；Gateway 负责 HTTP Idempotency-Key，本地不恢复请求命令表、租约或响应快照。
- 已初始化但尚未受理支付的订单允许本地取消；可能已发出 Intent 创建时，先取得原 Intent 并确认终止。

本变更取代旧 Customer 准备流程中“Consumer 保存后发起准备、准备时创建 Intent、Intent 绑定后才 READY”的时序，其他 Owner 事实不变。税务真实接入若需要完整地址，需明确补齐/重算时机，不能将本轮固定零税推广为真实税规则。
