# channel 能力组编辑弹窗优化 · mor-test 发布记录（2026-10-22）

- 范围：仅 operations-console-web。MID 能力组编辑弹窗明细行（表头、开关“支持/不支持”、类型中文、删除与添加按钮）；`schemeCode` 界面名“体系”改为“卡组”。字段与接口不变。
- 构建：干净 worktree main ecab136c（ops-web 目录与 1b60ae43 相同），先构建基线，与现网 `sha256:70829a3a…` 的 162 个文件逐字节一致；再叠加 4 个文件构建。
- 镜像：`sha256:11ea8b2ebe81f6c2dd03f99ee36a70ac828635e60ea931dba283abc3d81e41cb`（tag capability-ui-20261022），容器 healthy。
- 核对：产物含 `channel-detail-head`，全部资源不再出现“体系”；`npm test` 1242 通过，`tsc --noEmit` 通过。未做浏览器人工验收。
- 回滚：`compose-rollback.yaml`（70829a3a）。
- 未提交代码。

## 第二版（同日）

- 范围追加：MID 详情改为页签“能力组 / 变更记录”，变更记录不再平铺在详情页；“新建能力组”移到页签栏右侧，与渠道详情页一致。
- 构建：同一干净 worktree 方法（main ecab136c + 同 4 个文件）。镜像 `sha256:0e5c0ed562516982284d4e988a51f2a48e07a844dfd982128ab8c4fe5dd105ea`（tag capability-ui-20261022b），容器 healthy，替换第一版 11ea8b2e。
- 核对：`tsc --noEmit` 通过，`npm test` 1242 通过；未做浏览器人工验收。回滚仍用 `compose-rollback.yaml`（70829a3a，替换前现网）。

## 第三版（同日）

- 范围追加：MID 能力组表去掉“明细摘要”列（选中组明细已在下方展示），删除仅此处使用的 `capabilitySummary` 及其断言。
- 镜像 `sha256:88f89c407dbcf8cb2bc575bd2481027d85abe2aa351d573f8b243df58786b3d8`（tag capability-ui-20261022c），替换第二版 0e5c0ed5；源文件追加 `src/channel/channel.test.ts`。
- 核对：`tsc --noEmit` 通过，`npm test` 1242 通过；未做浏览器人工验收。回滚仍用 `compose-rollback.yaml`。

## 第四版（同日）

- 范围追加：删除 5 处页底提示——渠道列表、MID 列表、成本方案列表（ChannelNote），外部接入方列表、标准 API 列表（Note，并去掉不再使用的 import）。
- 源文件共 9 个：src/channel/{CapabilityEditor.tsx, channel.css, channel.ts, channel.test.ts}，src/pages/{ChannelChannels, ChannelCostPlans, ChannelMids, ExternalIntegrations, ExternalStandardApis}.tsx；基于 main ecab136c 干净 worktree。
- 镜像 `sha256:d1e5619f23f2bce4b038336966ce921cd886cab0305dc791d44048cd654d8dfa`（tag capability-ui-20261022d），替换第三版 88f89c40；产物中已无上述 5 段文字。
- 核对：`tsc --noEmit` 通过，`npm test` 1242 通过；未做浏览器人工验收。回滚仍用 `compose-rollback.yaml`。
