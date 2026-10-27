# 本机浏览器冒烟（阶段 5，2026-10-27）

环境：本机 e2e 栈；两个 Web 为 worktree `Code-MoR-refdata` 的 Vite 开发服务器（client-console-web :18302、operations-console-web :18301，改动即时生效），经 Nginx（Client https://127.0.0.1:18443、运营 https://127.0.0.1:18444）→ Gateway → BFF / client-master / Platform。最终后端：Platform `ab02b0b7…`、client-master `910bfc85…`、client-console-bff `b7c34ec6…`、operations-console-bff `ee26358f…`。语言包：client-console #4、operations-console #7（refdata 每个应用 429 个 key，en = zh-CN）。Playwright chromium headless。

脚本：`client_refdata.mjs <en|zh-CN> <1440|390>`、`ops_refdata.mjs <en|zh-CN> <caseId> [--currency]`；Client 账号与申请由 `../local-e2e/ui_provision.py` 预置（凭据只在 scratchpad 0600 文件，不打印）。截图在 `screens/`（约 12 MB，不提交）。

## 最终结果

| 页面 | 语言 × 宽度 | 结果 |
|---|---|---|
| Client 入驻：需要处理 → 打开任务页即显示页顶汇总并标红字段 → 点汇总聚焦；币种（4 个 ACTIVE、Intl 名称、"名称 (AED)"）；国家（249 项、国旗占位、按名称 / 英文名 / 编码 / 忽略变音搜索、Enter 选中）；酋长国（7 项，en 为通用英文名、zh 为中文；地址国家改 GB 后清空并变文本框、改回 AE 恢复下拉）；选项未下发时"暂时无法加载 · 重试"及重试恢复；核对页本地化显示 | en × 1440 / 390（P2 修复后复跑）；zh-CN × 1440 / 390 | 各 21 / 21 |
| 运营 参考数据：4 页签条数（249 / 47 / 42 / 298）、只读标记、搜索、空状态、MCC 状态筛选、加载失败与重试；国家"状态"列完整（F-3）；行业名称不截断（F-4） | en、zh-CN × 1440 | 通过 |
| 运营 O03 分类：两级行业、建议 MCC 与"建议"标签（不溢出，F-6）、细类说明按界面语言（F-6）、MCC 搜索改选、"已改 · 建议 xxxx"、服务端 422 挂到行 MCC 控件、页脚"保存未成功" | en、zh-CN × 1440 | 通过 |
| 运营 入驻配置目录：REF_CURRENCY、REF_COUNTRY_SUBDIVISION（地址格式组件）列入并标"Platform 参考数据"（标签不截断）；选中行政区目录后右侧预览本辖区行政区（F-5） | en、zh-CN × 1440 | 通过 |
| 运营 币种页：列表新列、"版本"列完整（F-7）、DRAFT 全部属性可编辑、ACTIVE 只读带锁 + "更换图标"弹窗 | en、zh-CN × 1440 | 通过（块 125 本机补注册后） |
| 运营 参考数据 · 行政区：CLDR 阿拉伯文名（E-2） | zh-CN × 1440 | AE 7、SA 13、EG 27 全部显示 |

合计：Client 4 组各 21 / 21；运营每种语言 29 / 29。无 pageerror；页面无原始 i18n key（行业详情"显示为 … (refdata.industry.X)"是设计中的诊断提示）；Client 390px 无横向溢出。

## 发现与处理

| # | 级别 | 页面 | 现象 | 处理 | 状态 |
|---|---|---|---|---|---|
| F-1 | **P1** | Client 入驻任务页 | 已保存的参考数据编码失效（停用、不存在、省州与国家不一致）时，首页标"需要处理"、核对页锁定，但任务页不标红、不汇总，"继续"照常前进，客户无法定位和提交 | e2e-dev 修复：打开任务页时把需求清单中本页字段的非"缺少"类问题挂到字段（红框 + 说明）并显示页顶汇总（原型 D）；语言包加载后刷新文案；用户已改动的字段不回挂。文件：`client-console-web/src/onboarding/progress.ts`、`TaskPage.tsx`、`progress.test.ts` | 已修，`tsc` 通过、`npm test` 129 / 129、Client 冒烟 4 × 21 / 21 |
| F-2 | P2 | Client 酋长国 / 运营行政区（英文） | 英文名是 ISO 3166-2 拉丁转写（`Dubayy`、`Abū Z̧aby`、`Ash Shāriqah`） | e2e-dev：新增生成源 `generator/sources/subdivision-en.json`（47 个通用英文名），只用于 `refdata.subdivision.*` 的 en 文案；库中 `name` 仍为 ISO 拉丁转写 | 已修：Client en 1440 / 390 显示 Abu Dhabi、Ajman、Dubai、Fujairah、Ras Al Khaimah、Sharjah、Umm Al Quwain；运营行政区页签 en 显示 Dubai |
| F-3 | P2 | 运营 参考数据 · 国家 | 1440 宽时"状态"列被截成"Activ" | ops-fe（ops-web 布局） | 已修：断言"状态"表头完整落在面板内，en / zh-CN 通过 |
| F-4 | P2 | 运营 参考数据 · 行业 | "名称"列截断、"Subcategory"在单词中间折行 | ops-fe：编码与名称合并为一列（新 key `reference-data.industry.column.codeAndName`） | 已修：断言名称单元格无溢出（"RETAIL_APPAREL / 服装与配饰"），en / zh-CN 通过 |
| F-5 | P2 | 运营 入驻配置 · 目录 | 只列 Schema 字段引用的目录，地址格式组件引用的 `REF_COUNTRY_SUBDIVISION` 不出现；"Platform 参考数据"标签被截断；"取值"列过窄 | cm-dev：client-master `/client-master-admin/v1/jurisdictions` 返回 `addressComponents`；ops-fe：目录清单并入地址格式目录、行政区预览、列宽。复测中发现点任一目录行会把页签重置为"采集 Schema"（refdata 命名空间首次按需加载时控制台重新挂载页面）→ ops-fe 改为根 I18nGate 预加载 refdata | 已修：`REF_COUNTRY_SUBDIVISION`（address.stateOrProvince (AE_POBOX)）列出、标签完整、点行后停留在"目录"并预览本辖区行政区（含 AE-DU），en / zh-CN 通过 |
| F-6 | P2 | 运营 O03 分类（1440） | "建议"标签溢出到右侧依据面板；细类下拉截断；zh 下细类说明为英文 | e2e-dev：新增 i18n key `refdata.industry.<CODE>.description`（42 个，en = 库中 description，zh-CN 来自 `generator/sources/industry-description-zh-CN.json`，生成脚本断言全覆盖）；ops-fe：行布局与说明按 refdata 显示 | 已修：断言"建议"标签在行内；zh 依据面板显示"家具、家居陈设、装饰品和日用家居用品……"，en 显示英文 |
| F-7 | P2 | 运营 币种页（1440） | 最右"版本"列被截断 | ops-fe（ops-web 布局） | 已修：断言"版本"表头完整显示，en / zh-CN 通过 |
| F-8 | 信息 | Client 国家搜索 | 不支持三位码（ARE）；名称、英文名、二位码、忽略变音均可 | 设计只要求"编码或名称" | — |
| F-9 | 信息 | 两端 | 国旗 / 币种 / 行业图标全为空，显示占位（国旗上传桩未执行） | 阶段 6 发布清单项 | — |
| F-10 | 信息 | 运营 币种图标 | 本机管理员没有文件上传权限，图标字段退回手填文件地址 | 设计中的降级路径；本机权限缺口 | — |
| F-11 | 信息 | ops-bff | 登录后 `/authorization/**` 记 ERROR `Cannot replace a committed response`（响应 200） | 换 JAR 前已有，与本需求无关 | — |

## 本机留存测试数据

- UI 账号 `onb-ui-<run>@example.test`：AE 机构草稿 240791861415915520（每次运行前 `ui_provision.py reset` 把币种 / 省州置为 XTS / EG-C；XTS 在 Platform 中不存在，视为"不在列表"）。
- AE 案件 240789978148261888：分类冒烟中的改动都已"放弃修改"，保存的分类以最后一次 API 验收为准（RETAIL_APPAREL / 5699），未确认。
- 验收用 DRAFT 币种 XTS 已于 12:47 UTC 删除（币种为 AED / EGP / SAR / USD 4 个 ACTIVE）；重跑币种页冒烟前先跑 `refdata_acceptance.py --only C`（会重新建 XTS），之后再删除。
