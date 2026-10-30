# Ops web report: joint query pages (worktree Code-MoR-jq-ops, detached at e3e0c12f)

Nothing is committed, pushed or deployed. No Java or BFF files were touched by this agent.

## Verification

All commands run in `operations-console-web`:

- `npx tsc --noEmit`: passes.
- `npm test`: 1624 of 1624 pass. The project's test runner is `node --test`, not vitest; 26 of these tests are new.
- `node --experimental-strip-types --test src/i18nSourceGuard.test.ts src/i18nCatalog.test.ts`: 7 of 7 pass.
- `npm run build`: builds. The only warning is the chunk-size warning, which I believe was already there; `dist` was removed afterwards.

**Browser pass (mocked API only).** I ran a temporary harness with the `session.request` API call stubbed, in Chrome, in English, at 1440px and in a 390px iframe. I compared the screens against prototypes 02, 03, 04 and 06:

- Results appeared level by level, and the Query button turned into Stop while running.
- Lanes, the start marker, and edges that skip a lane routed through the gap above the cards.
- Failed, not-queried and 12+ (truncated) nodes showed their markers.
- The gap summary expanded correctly, and "retry this item" turned 3 gaps into 2.
- The client filter chips appeared.
- The inline format error was marked on the bad line.
- At 390px the cards rendered with no horizontal scroll (scrollWidth equals clientWidth).
- The key types list and editor matched the prototype.
- No console errors.

The harness has since been deleted. None of this has been run against the real BFF or Platform, and zh-CN was not checked in the browser; it is only covered by the catalog test.

## Files added (`operations-console-web/src/`)

**Pages**
- `pages/JointQuery.tsx`: the joint query page. It has the search box, summary, gap list, client filter, link graph, node detail, cards at 390px, coverage drawer, load more, retry, the "query by this" action, and URL `?keyType=&value=` with auto-run.
- `pages/QueryKeyTypes.tsx`: the key types page. It lists key types grouped, with search, "show disabled", and drag-to-reorder within a group. Reorder is optimistic: it calls K5 and rolls back on 409. The editor is an inline panel at 1200px and wider, and a drawer below that.

**Logic and models (`dataQuery/`)**
- `jointRun.ts`: the step loop. It skips key/value pairs already queried, splits a level into several calls when it has more than 20 key types, and stops on the page limits (depth, total executions, total seconds) or user stop. "Retry this item" replaces the failed node and keeps expanding.
- `jointLayout.ts`: lanes and colours, edges, source path, node markers, gap summary, client filter, and "locate" for link values.
- `jointModel.ts`: decoders for J1, J2 and J3; the template `joint` draft and request body; the editor checks; the entry input check.
- `keyTypeModel.ts`: the K1 decoder, form and request bodies, grouping, reorder, and the regex subset check.

**Components (`dataQuery/`)**
- `JointGraph.tsx`: the `@xyflow/react` link graph.
- `JointDetail.tsx`: node detail.
- `JointSearch.tsx`: the combined search box and key type menu.
- `JointCoverage.tsx`: the coverage drawer.
- `KeyTypeEditor.tsx`: the key type editor.
- `JointPanel.tsx`: the template editor's "Joint Query" tab.
- `useKeyTypes.ts`, `useMediaQuery.ts`: small hooks.
- `jointQuery.css`: styles for all of the above.

**Tests**
- `jointRun.test.ts`, `jointLayout.test.ts`, `keyTypeModel.test.ts`, `jointModel.test.ts`.

## Files changed

- `dataQuery/templateModel.ts`:
  - Templates round-trip `joint` and `jointQuery`.
  - Turning participation off sends `joint: null`.
  - The problem level list gains `INFO`, which save responses can now contain.
  - Problems on `joint.*` fields jump to the new tab.
- `dataQuery/TemplateEditor.tsx`:
  - New "Joint Query" tab.
  - Live validation now sends `joint`.
  - When the server returns `joint.*` problems, the problems panel uses those; otherwise it shows the editor's own checks.
  - A save that returns only INFO messages shows as a success notification.
- `dataQuery/versionDiff.ts` and `dataQuery/VersionDrawer.tsx`: version compare shows joint changes as their own section (`jointChanges`).
- `dataQuery/dataQueryCommon.ts`: paths and routes for K1–K5, J1–J3 and single-template execution. The write receipt type now accepts `QUERY_KEY_TYPE`.
- `dataQuery/DataQueryParts.tsx`: `useResource` has a new optional `silent` parameter that skips the error toast. It defaults to off, so existing callers behave as before.
- `navigation/pageCapabilities.ts`, `navigation/ConsolePage.tsx`: entries for `OPERATIONS_JOINT_QUERY` (`/data-query/joint-query`) and `OPERATIONS_QUERY_KEY_TYPES` (`/data-query/key-types`). Neither has page actions.
- `configuration/configurationModel.test.ts`: page count changed from 78 to 80, plus route assertions for the two new pages.
- `dataQuery/versionDiff.test.ts`: fixture updated with `joint` and `jointQuery`.
- i18n files:
  - `src/i18n/en/data-query.json` and `i18n/zh-CN/data-query.json`: 213 new keys each.
  - `src/i18n/en/nav.json` and `i18n/zh-CN/nav.json`: 2 new keys each.
  - The catalog test fails unless en and zh-CN have exactly the same keys, so the repo's zh-CN seed files had to change too.
  - The zh-CN text for the new keys alone is also in `.jq-notes/zh-CN-data-query.json`.

Untracked scratch: the `operations-console-web/node_modules` symlink and `.jq-notes/`.

## Deviations from the prototypes and spec

- **"Common" vs "More" in the key type menu.** The spec doesn't define what goes under "Common". I used the 5 key types this browser used most recently (stored in localStorage), falling back to the first 5 by sort order.
- **Gap rows after a page-limit stop.** No nodes exist for the steps that never ran, so the "not queried" row reads "further data wasn't queried: <reason>" instead of listing template names as prototype 03 does.
- **"Show all".** The detail table shows the first 10 rows and expands locally on "show all". "Load more" appears only when a node is truncated and its template has paging. Templates without paging show a "truncated" note instead.
- **Permission for the key types page.** Write actions reuse the existing button `OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE`; no new resource was added.
- **Default role stays editable.** When a key type is used by templates, only the value type is locked, following the technique doc §8.1. Prototype 04 locks both value type and role.
- **Regex check is stricter than the spec's list.** The client also rejects `[]`, `[^]`, nested character classes, `&&`, and Java-only escapes such as `\p{}`, `\A`, `\Z`, `\Q…\E` and `\h`. These behave differently in Java and JavaScript. The server may accept some of them.
- **URL parameters are an addition.** `?keyType=&value=` is written to the address on every query and auto-runs on load, which makes a query shareable and linkable.
- **Timer after a retry.** The summary shows the retry's own elapsed time, not the total, and the page time budget restarts from the retry. This is minor and I left it as is.
- **Client filter is keyed on a fixed code.** It uses the key type code `CLIENT_ID` (requirement B11 / Q3). Group names and colours are not hard-coded anywhere.
- **Edge tooltip text.** Hovering an edge shows the key type's name, taken from the column label, followed by the values.

## Open questions

1. **Load more column assumptions.** "Load more" assumes the single-template execution route returns the same columns and masking as the joint node, and uses the first page's row count as the page size. Columns are re-matched by name to be safe.

## Resolved since the first report

- **J2 error status.** The server returns 400 with a Problem `detail` for an invalid entry key type, and jq-platform is updating `contract.md` to say so.
  - The coverage drawer shows that `detail` inline. A 422 field error is still handled the same way.
  - The drawer now shows any J2 failure only once, inline: the error toast is turned off for that call. Other failures still show the general error panel inside the drawer.
  - Checks re-run after this change: `npx tsc --noEmit` passes, `npm test` passes 1624 of 1624, and `npm run build` builds.
- **K2–K4 responses.** These return the usual write receipt with `objectType: QUERY_KEY_TYPE`. The key type editor now decodes that receipt and reloads the list.
- **K4 request field.** The status call sends `status`.
- **K5 response.** The response body is ignored and the list is reloaded.
- **J1 list name.** The entry key type list is `entryKeyTypes`; the decoder also accepts `keyTypes`.
- **J3 node fields.** `keyParameterList` decides whether "load more" sends a single value or an array, and `nextCursor` is passed back as `cursor` for CURSOR templates.
- **Template live validation.** Requests now send `joint`.

- **Dimension group chip.** J1 `groups` will leave out groups that never form a lane, such as "归属维度", so the empty state needs no change.
- **New skip code `EXECUTOR_BUSY`.** J3 can now report skipped nodes with `EXECUTOR_BUSY`, in addition to `STEP_BUDGET_EXHAUSTED` and `STEP_EXECUTION_LIMIT`.
  - `EXECUTOR_BUSY` now has its own reason text, "the system was busy; retry in a moment", in both the node detail and the gap rows.
  - Any other skip code the page does not recognise is still shown as "not queried" with "retry this item". Its gap row reads "not queried".
  - Before this fix, an unrecognised code in a gap row would have read "stopped by you", and a skipped node with no message would have shown the step-time-limit reason.
  - The fix added 4 en/zh-CN keys: `joint.reason.EXECUTOR_BUSY`, `joint.reason.skippedGeneric`, `joint.stop.EXECUTOR_BUSY` and `joint.stop.notQueried`.
  - Checks re-run after this fix: `npx tsc --noEmit` passes, `npm test` passes 1624 of 1624, and `npm run build` builds.
- **Exclusion reasons.** New reason codes such as `KEY_TYPE_INACTIVE` and `TEMPLATE_INACTIVE` need no change: the coverage drawer shows the server's `reason` text.
- **`truncated`.** It is now true only when more rows exist, which is what "load more" and the n+ marker already assume.

## Next

The team lead may ask for a pass against the real backend, in both en and zh-CN, at 1440px and 390px.
