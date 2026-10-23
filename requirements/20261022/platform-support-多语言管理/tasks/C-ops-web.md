# 任务卡 C：operations-console-web 多语言管理页面

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n`。**只改 `operations-console-web/**`**。卡 A 改 `paytech/platform-support/**`，卡 B 改两个 BFF 与需求 `db/`。
- 授权：普通文件编辑；`operations-console-web` 内 `npm install`（只为新增 `intl-messageformat`，精确版本）、`npm test`、`npm run build`。不提交、不推送、不动容器、不启动浏览器去 mor-test。
- 权威（主工作区，只读）：需求与 `technique.md`（v3）第 3.6—3.9 节；**设计稿** `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-多语言管理/assets/prototype/01—09*.png`（用户已确认，布局、交互、文案以此为准）。工作流 V2 页面是同风格的现成实现，可作为代码参照：`operations-console-web/src/workflow/`。

## 开工前必读

`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 10 节（前端界面文案）与 BFF / 前端相关章节、`operations-console-web/README.md`（如有）、`src/App.tsx`、`src/navigation/`、`src/api/session.ts`、`src/workflow/`、`src/dataQuery/`（列表 + 详情、弹窗、错误展示、`Idempotency-Key`、文件上传的现成写法）。逐张看 9 张设计稿。

## 范围

1. **i18n 运行时** `src/i18n/`：从 `/Users/kenny/Desktop/Workspace/Code-MoR-i18n-clientweb/client-console-web/src/i18n/`（已完成并有测试的同功能实现）复制，调整为 ops-web：manifest 路径 `/i18n/v1/manifest`（经 session 的 BFF 前缀），应用默认语言 `zh-CN`；**不提供语言切换入口**，不改全局 antd / dayjs locale（既有页面仍是中文）。复制其测试并通过。
2. **英文基准** `src/i18n/en/i18n-admin.json`（命名空间 `i18n-admin`，本需求页面的全部文案）与 `enum` 命名空间里本页面用到的枚举展示名；**中文首批翻译** `operations-console-web/i18n/operations-console.zh-CN.seed.json`；`npm run i18n:export-source` 脚本同 client-web 的格式。新页面所有文案经 `t()`，不得写死中文。
3. **导航与路由**：一级菜单"多语言"，子页"翻译"`/i18n/translations`、"发布记录"`/i18n/releases`、"语言与应用"`/i18n/settings`。组件键与资源以卡 B 报告为准（若尚未出来，先用 `OPERATIONS_I18N_TRANSLATIONS` / `_RELEASES` / `_SETTINGS`，报告中注明待对齐）。
4. **页面**（严格按设计稿 01—09）：
   - 翻译：页头应用 / 语言选择、页签计数、未发布提示、"N 个问题"浮层（逐条定位）、导入 key、发布；左侧 key 列表（命名空间筛选、搜索、状态图标）；右侧详情（英文基准 + 参数、中文编辑器带 ICU 高亮与前端即时校验、示例值预览、底栏校验结果、保存并下一条 ⌘↵、上下条键盘切换、离开时未保存提醒）；错误态与一键修正（02）。
   - 导入 key 弹窗（03）：选文件 → BFF 上传 → 预览四类差异 + 未创建命名空间（可直达新建命名空间弹窗，建好后自动重新预览）→ 确认；预览过期 409 时提示重新预览。
   - 发布确认弹窗（04）：调用预检；有阻止项时展示并禁用确认；发布后跳到发布记录并轮询状态。
   - 发布记录（05）：表格 + 详情，检查中每 2 秒刷新进度直至 READY / FAILED；切回确认弹窗（06）带必填原因。
   - 语言与应用（07—09）：应用表 + 设置面板（默认语言、必须翻译语言、发布要求 + 即时是否满足）、命名空间列表、新建应用弹窗、新建命名空间弹窗（编码即时格式与重复校验）；页签"语言"展示 Locale 目录（启停、方向）。
   - 加载、空、错误、无权限状态完整；写操作带 `expectedVersion`，并发 409 时提示刷新。
5. **API 调用**：路径前缀 `/localization-admin/v1`（BFF，卡 B），请求 / 响应字段以 Platform api 合同类为准：worktree `paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/localization/`。上传两步端点以卡 B 为准；未出来时按工作流附件上传的现成接口形态写，报告注明。
6. **测试**：node 测试覆盖运行时、硬编码扫描（`src/i18n-admin/**` 或你的页面目录 + `src/i18n/**` 不含中日韩字符）、英文基准与 seed 的 key 集合一致、页面模型（ICU 前端校验、问题汇总、发布状态轮询终止条件、导入预览阻止条件）。既有 1,300+ 测试全部继续通过。

## 验证

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR-i18n/operations-console-web
npm install && npm test && npm run build
```

## 交付报告（写到 `.../tasks/C-report.md`）

改动文件；页面与设计稿逐张对照（差异与原因）；依赖的 BFF 端点清单；测试与构建实际输出；待裁决问题。**完成后直接结束，不要等待后台事件。**
