# 3DS 全链路配置 harness

仅手工配置使用，不进入生产应用；编写时未对任何环境执行。运行环境、启动步骤与运行目录沿用 [PayerMax 接入 harness](../../../../20261014/platform-support-PayerMax接入/assets/harness/README.md)（`E2E_RUNTIME`，Web 代理 → Gateway → Operations BFF → channel-connector），本目录脚本从那里导入 `common.py` 与 `configure.py`。

| 文件 | 作用 |
|---|---|
| `configure_3ds.py` | 按[技术方案第 3.3.7 节](../../technique.md)增量修改：标准 API `PAY` 追加 `payment.threeDsRequired`（BOOLEAN，可选）；各渠道原子 API `ORDER_AND_PAY` 追加 `data.paymentDetail.cardInfo.dynamic3DS`；`PAY` 流程要求 3DS 时传 `do3DS`、否则不传；核对 `PAY_QUERY` 已输出 `CLOSED` / `NOT_FOUND`，缺少时按接入 harness 的 `QUERY_FLOW` 保存 |

前提：渠道已执行接入 harness 的 `configure.py` 与[卡直付 harness](../../../../20261014/payment-PayerMax卡直付对接/assets/harness/README.md) 的 `configure_card.py`。

步骤（技术方案 3.4 执行顺序第 ⑤ 步，应用部署与 `mor_channel_connector` DML 之后）：

```bash
E2E_RUNTIME=<运行目录> python3 configure_3ds.py PAYERMAX_MOCK PAYERMAX
```

- 每一步先读取现行定义，已是目标状态则跳过；输出 `changed` 列出实际修改项，调用记录（只含名称、方法、路径、状态码）写入 `$E2E_RUNTIME/configure-3ds-<渠道>.json`。
- 修改标准 API `PAY` 会复核全部接入方的 `PAY` 流程，并使沙箱用例变为“需重新验证”，阶段 5 重跑。
- `dynamic3DS` 需 PayerMax 为商户开通；未开通时的行为待 UAT 实测（技术方案 D6）。
- 回滚：经运营控制台或管理接口删除新增的请求映射与字段（先流程、再原子 API、最后标准 API）。
