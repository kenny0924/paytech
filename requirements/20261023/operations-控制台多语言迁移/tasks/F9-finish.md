# 任务卡 F9：集成收尾

在主会话合并 F0 与 M1—M7 后的集成目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n` 中执行。

1. 守卫测试 `src/i18nSourceGuard.test.ts` 改为 `STRICT = true`：`src/` 源码（测试除外、注释除外）汉字为 0，白名单只有 `navigation/pageCapabilities.ts`；处理残留（含 `pages/pageHeading.test.ts` 等无主测试）。
2. 英文用词统一：按 `glossary.md` 扫描全部 `en/*.json`，统一同一中文在不同命名空间的英文（列出差异表，按术语表改；术语表没有的，选出现最多的写法）；检查大小写约定（菜单 / 按钮 / 表头 Title Case，句子首字母大写）、复数、残留拼音或中式英文。处理各模块报告里"拿不准的英文用词"中主会话已裁决的项。
3. 英文布局：本地 Vite + 浏览器（Playwright）在 1440 与 1280 宽遍历全部菜单页与主要弹窗的英文版，修正溢出、按钮折行错位、表头不可读（只改样式或缩短英文，不改交互）。截图存 `requirements/20261023/operations-控制台多语言迁移/assets/local-en/`。
4. 全量自检：`npm test`、`tsc`、`vite build`、`node scripts/i18n-export-source.mjs`（各命名空间 key 数、总数、文件大小）、operations-console-bff 测试。
5. 文档：`agent-docs/decisions/2026-10-22-ui-text-localization.md` 与 `agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 10 节补充：ops-web 全量接入、默认英文、本地缓存、禁中文守卫、新文案必须先加 en key 与 zh-CN 种子。

报告写 `tasks/F9-report.md`。边界同 F0（不提交、不推送、不碰主工作区代码、Maven 私有仓库、不调用 Superpowers skill、不派生子代理）。

## 补充（主会话，2026-10-23，全部 8 张卡已合并后）

集成目录现状：相对基线 `9f210b5c` 427 个文件；npm test 1480/1480、tsc、vite build 通过；守卫报告 0 残留；导出 8513 key。各卡报告见 `tasks/F0-report.md`、`M1`—`M7-report.md`。

### 必做清单

1. **守卫强制**：`STRICT = true`，删除不再需要的 `MIGRATED_DIRS` 机制或保留为空均可，确保全 `src` 严格。
2. **用词裁决**：逐条执行 `tasks/english-decisions.md` 中标"F9 修改"的项，至少包括：
   - 所有"驳回 / 已驳回"英文统一为 Reject / Rejected（finance、recon、channel、commercial、workflow 等；含 "Return Version""Couldn't return the version""returned" 等变体，按句意改写）；"退回修改"保持 Return for Changes。
   - "内容摘要"统一为 Content Hash；风控编辑器面包屑"规则配置"用 Rules（与菜单一致，优先 `nav.resource.*`）。
   - 句中"你"用小写 you。
   - 对账详情"{对象}不存在"改为 "No matching {object} found."。
   - 通知路由角色 Primary / Fallback、数据查询 Fragment / Expanded Validation 等作为标签时首字母大写。
   然后全量扫描 `src/i18n/en/*.json`：同一中文（按 zh-CN 种子反查）在不同 key 下的英文不一致时，列差异表并按术语表或多数写法统一（明显语境不同的可保留，在报告说明）。
3. **`richText()`**（M4 在 `channel/ChannelParts.tsx`）与 M7 `DataQueryParts` 的同类辅助：如通用则移到 `src/i18n/`（如 `RichText` 组件），调用方改为引用；不通用则保留并说明。
4. **`nav` 中文种子以 Platform 名称为准**：对照 `tasks/ops-resources-mor-test.tsv`，`nav.resource.<CODE>` 的 zh-CN 必须等于 Platform `display_name`（`pageCapabilities.ts` 名称不同时以 Platform 为准，例如"使用关系""菜单资源"所在资源），列出改动。
5. **英文布局浏览器检查（mor-test 只读）**：本地 Vite（端口用 18311 或其他空闲端口，不要动 18301 上别人的服务）+ Playwright，把 `/api/operations/**` 中继到 `http://localhost:28080`（带对应 Origin / Host，否则 Gateway 返回 GATEWAY_FORBIDDEN；路由匹配不能写 `**/api/**`，会误中 Vite 的 `/src/api/*`）。登录方式参照 `requirements/20261022/platform-support-多语言管理/release/acceptance/stage7_browser.py`。**只读**：不点保存 / 提交 / 删除 / 启停等写操作；可以打开弹窗再取消。mor-test 上的 BFF 是旧版本：角色权限树、审计字段名、定价试算说明三处依赖新 BFF 合同，页面报错属预期，记录即可。mor-test 还没有新命名空间的语言包，`?lang=en` 用内置英文即可。1440 与 1280 宽遍历全部菜单页（及每页主要弹窗 / 抽屉至少一个），截图存 `requirements/20261023/operations-控制台多语言迁移/assets/local-en/`，修正溢出、折行错位、表头不可读。再用 `?lang=zh-CN` 抽查 10 个页面中文显示正常（本地语言包不存在时会退回英文——可用 Playwright 模拟 manifest + 语言包，语言包内容用 `i18n-out/operations-console.zh-CN.json` 按命名空间拆出）。
6. **全量自检**：npm test、tsc、vite build、export（key 数、文件大小）；operations-console-bff 全部测试（私有 Maven 仓库参数同 F0）。
7. **文档**：`agent-docs/decisions/2026-10-22-ui-text-localization.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 10 节（含 key 写法改为方案 3.2 的小驼峰段名、ops-web 全量接入、默认英文、本地缓存、禁中文守卫、新文案须先加 en key 与 zh-CN 种子）——这两个文件改在**集成目录**里（随本需求提交），不要改主工作区。

文档以外的文件只改 `operations-console-web` 与 `paytech/operations-console-bff`。报告写 `tasks/F9-report.md`，最终回复简述。
