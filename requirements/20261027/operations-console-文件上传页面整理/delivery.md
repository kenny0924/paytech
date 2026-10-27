# 运营控制台：文件上传页、文件记录详情页与参考数据页签整理（2026-10-27）

只改前端（operations-console-web），接口、BFF、Platform 不变。

## 改动
- 说明条、蓝色限制条、黄色警告框、拖放区、常驻空结果卡合并为一张上传卡：头部说明 + 「公开」标签 + 「上传设置」弹出层（目标存储、写入规则、检查策略、允许类型、大小上限、授权有效期）；拖放区下一行提示（类型 / 大小 / 单个文件）；公开警告改为一行小字；进度与结果在卡内出现。
- 限制加载失败、未配置时才在卡上方显示提示条。
- 最近上传：类型与大小并入文件名下方，8 列 → 6 列；窄屏工具栏换行。
- 新增文案键 `file.upload.panel.hint`、`file.upload.settings.open`（en + zh-CN 种子）。

文件：`src/fileUpload/{UploadPanel.tsx,RecentUploads.tsx,fileUpload.ts,fileUpload.css}`、`src/pages/FileUpload.tsx`、`src/i18n/en/file.json`、`i18n/zh-CN/file.json`。

## 文件记录详情页（第二轮）
- 四张摘要卡 + 右侧窄栏（上传记录、嵌入的审计筛选表单）改为：返回链接 → 标题行（文件名、公开 / 检查标签，右侧刷新与下载）→ 一行概要（版本 · 大小 · 类型）→ 公开地址卡（地址与复制 / 打开 / 复制带版本地址同一行）→ 页签：概览（文件 ID、生效时间、来源系统、原因、上传人、进行中的替换、存储实例、存储桶、对象键）、版本与检查、上传记录、下载与操作记录（审计全宽，隐藏重复标题）。
- 公开地址条的复制按钮改为独立按钮，消除 button 套 button 的控制台错误（列表页预览共用，同步受益）。
- 新增 11 个 `file.detail.tab.*` / `file.detail.field.*` 键。
- 文件：`src/fileRecords/{FileRecordDetailView.tsx,FileRecordParts.tsx,fileRecords.css}`。

## 参考数据页签（第三轮）
- 删除页头"只读 · 数据来自 Platform"与"全平台共用的币种……"说明。
- 页签不再显示统计：参考数据页签数字徽标、国家 / 行业 / MCC 工具栏计数、币种状态筛选计数、行政区国家标签计数、文件记录详情页签计数。
- 按需加载：去掉页面级的"依次读取全部参考数据"，每个页签首次切换过去才请求自己的接口，离开后保留、回来不重查；页签之间不再互相取数（国家详情改为"查看行政区"链接；行业的建议 MCC 名称取 refdata 语言包；MCC 页去掉"被细类建议"列、详情与"网络高风险 / 被细类建议"快捷筛选——它们需要全量行业 / MCC，后端无对应筛选参数）。
- MCC 改为后端分页：每页 50 条，关键字（编码前缀或英文名）停顿 300 ms 与状态交给后端，翻页再查。国家、行政区、行业接口本身不分页（全量一次返回），保持一次请求。
- 新增 `reference-data.country.openSubdivisions`、`reference-data.subdivision.countryChipName`。
- 文件：`src/pages/{ReferenceData.tsx,CurrencySpecs.tsx}`、`src/referenceData/referenceData.css`、`src/fileRecords/FileRecordDetailView.tsx`、两份 `reference-data.json`。
- mor-test 实测请求序列：打开页面只请求币种；依次切换国家、行政区、行业、MCC 各发 1 个各自请求；MCC 翻到第 2 页发 `page=2`；输入 59 发 `keyword=59&page=1`；切回国家不再请求；无控制台错误、无原始 key。

## 行业页签展开 / 收起（第四轮）
- "展开全部 / 收起全部"从表格底部脚注移到工具栏状态筛选旁；脚注只保留说明。文件：`src/pages/ReferenceData.tsx`、`src/referenceData/referenceData.css`。
- 镜像 `fileupload-ux-20261027d` = `sha256:2e179d6f1bc3…`；替换 `release/compose-candidate.operations-console-web.industry-expand.yaml`，回滚 `release/compose-rollback.operations-console-web.industry-expand.yaml`（到 `615c1b89`）。无新增文案键。mor-test 实测：收起后 10 行（大类），展开后 42 行，无控制台错误。

## 验证
- `tsc --noEmit` 通过；`npm test` 1567 通过 / 0 失败。
- mor-test 浏览器（无头 Chromium，经 http://localhost:28080 登录）：中英文 × 1440 / 390，无控制台错误、无 4xx/5xx、无原始 key；真实上传一个 98 B PNG：检查通过，公开地址 / 带版本地址 / 文件地址显示，最近上传刷新为 251 个。

## mor-test 部署
- 构建源：`/Users/kenny/Desktop/Workspace/Code-MoR-fileupload-ux`（5e1f16ab + 上述 7 个文件）。基线（5e1f16ab）dist 243 个文件与现网 `f3da39ad` 逐字节相同。
- 镜像：`mor-test/operations-console-web:fileupload-ux-20261027a` = `sha256:bddabcbc3cec…`（dist 245 个文件，多出两个图标分片）。
- 替换：`release/compose-candidate.operations-console-web.yaml`；回滚：`release/compose-rollback.operations-console-web.yaml`（`f3da39ad`）。
- 第二轮镜像 `fileupload-ux-20261027b` = `sha256:bfae15b3a23c…`；替换 `release/compose-candidate.operations-console-web.file-records.yaml`，回滚到第一轮用 `release/compose-rollback.operations-console-web.file-records.yaml`。语言包 #16（新增 11、废弃 0、READY；同样 504 后由 Platform 完成）。mor-test 中文复核：两页无原始 key、四个页签与复制按钮正常。
- 第三轮镜像 `fileupload-ux-20261027c` = `sha256:615c1b890dc1…`；替换 `release/compose-candidate.operations-console-web.refdata-tabs.yaml`，回滚 `release/compose-rollback.operations-console-web.refdata-tabs.yaml`（到 `bfae15b3`）。语言包 #17（新增 2，READY；同样 504 后由 Platform 完成）。
- 语言包：用参考数据发布的 `refdata_bundles.py --app operations-console --web-root <构建工作区> --apply` 发布 operations-console #15（新增 2、废弃 0、READY）。发布请求经 Gateway 504 超时，但 Platform 已完成，库内 #15 READY；发布说明沿用了脚本固定的参考数据文案。

## 未做
- 已提交（feat(operations-console-web) 文件上传 / 文件记录 / 参考数据页签整理），未推送。
- 不再引用的旧键（`upload.panel.single`、`upload.result.empty`、`upload.result.inProgress`）保留，未废弃。
