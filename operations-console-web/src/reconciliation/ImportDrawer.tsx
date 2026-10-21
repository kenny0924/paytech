import { useRef, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Progress, Upload } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { FinanceNote } from '../finance/FinanceParts';
import {
  chunkLines, decodeCompletion, decodeUtf8, decodeWithId, parseBankStatementCsv, parseChannelStatementCsv, reconPaths, sha256Hex,
} from './reconModel';
import type { BankStatementFile, CashAccount, ChannelStatementFile, CsvIssue, ImportCompletion } from './reconModel';
import { RejectionTable } from './ReconParts';

type Kind = 'channel' | 'bank';
type Parsed = { kind: 'channel'; file: ChannelStatementFile } | { kind: 'bank'; file: BankStatementFile };
interface UploadProgress { id: string; chunks: unknown[][]; next: number }
type Phase =
  | { step: 'idle' }
  | { step: 'invalid'; fileName: string; issues: CsvIssue[] }
  | { step: 'uploading'; sent: number; total: number }
  | { step: 'failed'; message: string; sent: number; total: number }
  | { step: 'done'; result: ImportCompletion; reference: string };

/** 发送一次分段请求：JSON 正文、每次请求独立的 Idempotency-Key（同一段重试复用）。 */
async function send<T>(path: string, body: unknown, key: string, decode: (value: unknown) => T): Promise<T> {
  return session.request<T>(path, { method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), 'Idempotency-Key': key } }, decode);
}

/**
 * 分段导入抽屉（D-01）：浏览器本地解析 CSV、算整个文件的 SHA-256，按「开始 → 追加（每段 ≤ 100 行、≤ 60 KB）→ 完成」发送
 * <li>本地结构校验不通过时不上传，直接列出行号与原因；完成导入后显示 reconciliation 的结果：成功显示账单号与行数，被拒显示逐行原因（AC-V2）。</li>
 * <li>中途失败可从失败的分段继续：追加按行号幂等（同号同内容忽略），不重新开始导入。</li>
 * <li>银行对账单须先在页面选银行账户，文件末四位必须一致。</li>
 */
export function ImportDrawer({ kind, cashAccount, onClose, onImported, onAuthorizationChanged }: {
  kind: Kind; cashAccount?: CashAccount; onClose: () => void; onImported: () => void; onAuthorizationChanged: () => void;
}) {
  const notify = useErrorNotification();
  const [phase, setPhase] = useState<Phase>({ step: 'idle' });
  const upload = useRef<UploadProgress | null>(null); const keys = useRef(new Map<string, string>());
  const reference = useRef('');
  const keyFor = (step: string) => { const key = keys.current.get(step) ?? crypto.randomUUID(); keys.current.set(step, key); return key; };
  const busy = phase.step === 'uploading';

  async function choose(file: File) {
    upload.current = null; keys.current.clear();
    let parsed: Parsed; let hash: string;
    try {
      const bytes = await file.arrayBuffer();
      const text = decodeUtf8(bytes);
      const result = kind === 'channel' ? parseChannelStatementCsv(text) : parseBankStatementCsv(text, cashAccount?.accountLast4);
      if (!result.file) { setPhase({ step: 'invalid', fileName: file.name, issues: result.issues }); return; }
      parsed = kind === 'channel' ? { kind, file: result.file as ChannelStatementFile } : { kind: 'bank', file: result.file as BankStatementFile };
      hash = await sha256Hex(bytes);
    } catch {
      setPhase({ step: 'invalid', fileName: file.name, issues: [{ lineNo: null, reason: '文件不是 UTF-8 编码的 CSV' }] });
      return;
    }
    const lines: readonly unknown[] = parsed.file.lines;
    const start = parsed.kind === 'channel'
      ? { ...parsed.file.header, fileName: file.name, fileContentHash: hash, declaredLineCount: lines.length }
      : { cashAccountId: cashAccount?.cashAccountId, ...parsed.file.header, fileName: file.name, fileContentHash: hash, declaredLineCount: lines.length };
    reference.current = parsed.kind === 'channel' ? parsed.file.header.channelBatchRef : parsed.file.header.statementRef;
    setPhase({ step: 'uploading', sent: 0, total: lines.length });
    try {
      const created = await send(kind === 'channel' ? reconPaths.channelStatements : reconPaths.bankStatements, start, keyFor('start'),
        decodeWithId<Record<string, string>>(kind === 'channel' ? 'statementId' : 'bankStatementId'));
      upload.current = { id: kind === 'channel' ? created.statementId : created.bankStatementId, chunks: chunkLines(lines), next: 0 };
    } catch (error) {
      fail(error, 0, lines.length);
      return;
    }
    await resume(lines.length);
  }

  async function resume(total: number) {
    const current = upload.current;
    if (!current) return;
    const linesPath = kind === 'channel' ? reconPaths.channelStatementLines(current.id) : reconPaths.bankStatementLines(current.id);
    let sent = current.chunks.slice(0, current.next).reduce((count, chunk) => count + chunk.length, 0);
    setPhase({ step: 'uploading', sent, total });
    try {
      for (; current.next < current.chunks.length; current.next++) {
        await send(linesPath, { lines: current.chunks[current.next] }, keyFor(`lines:${current.next}`),
          decodeWithId(kind === 'channel' ? 'statementId' : 'bankStatementId', current.id));
        sent += current.chunks[current.next].length;
        setPhase({ step: 'uploading', sent, total });
      }
      const completionPath = kind === 'channel' ? reconPaths.channelStatementCompletion(current.id) : reconPaths.bankStatementCompletion(current.id);
      const result = await send(completionPath, undefined, keyFor('completion'),
        decodeCompletion(kind === 'channel' ? 'statementId' : 'bankStatementId', current.id));
      setPhase({ step: 'done', result, reference: reference.current });
      onImported();
    } catch (error) {
      fail(error, sent, total);
    }
  }

  function fail(error: unknown, sent: number, total: number) {
    if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
    notify(error, '导入失败');
    setPhase({ step: 'failed', message: error instanceof Error ? error.message : '导入未完成', sent, total });
  }

  const title = kind === 'channel' ? '上传渠道账单' : `上传银行对账单 · ${cashAccount?.accountName ?? ''}（尾号 ${cashAccount?.accountLast4 ?? ''}）`;
  return <Drawer open size="min(880px, 100vw)" rootClassName="finance-drawer finance-page" title={title} destroyOnHidden
    onClose={() => { if (!busy) onClose(); }} footer={<div className="finance-drawer-footer"><span className="finance-spacer" />
      <Button onClick={onClose} disabled={busy}>关闭</Button></div>}>
    <Upload accept=".csv,text/csv" maxCount={1} showUploadList={false} disabled={busy}
      beforeUpload={file => { void choose(file); return Upload.LIST_IGNORE; }}>
      <Button type="primary" disabled={busy}>选择 CSV 文件</Button>
    </Upload>
    <div className="recon-import-body">
      {phase.step === 'invalid' && <>
        <Alert type="error" showIcon className="finance-banner" title={`${phase.fileName} 未通过本地校验，未上传（共 ${phase.issues.length} 条原因）`} />
        <RejectionTable rejections={phase.issues} />
      </>}
      {(phase.step === 'uploading' || phase.step === 'failed') && <>
        <Progress percent={phase.total ? Math.floor((phase.sent / phase.total) * 100) : 100} status={phase.step === 'failed' ? 'exception' : 'active'} />
        <div className="cell-meta">已发送 {phase.sent} / {phase.total} 行{phase.step === 'uploading' && phase.sent === phase.total ? '，等待完成校验…' : ''}</div>
        {phase.step === 'failed' && <Alert type="error" showIcon className="finance-banner" title={phase.message}
          action={upload.current && <Button size="small" onClick={() => void resume(phase.total)}>从中断处继续</Button>} />}
      </>}
      {phase.step === 'done' && <ImportResult kind={kind} result={phase.result} reference={phase.reference} />}
    </div>
    <FinanceNote>{kind === 'channel'
      ? '文件格式：第 1、2 行为 #HEADER 开头的账单头列名与取值（clearing_account_code、channel_batch_ref、settlement_date、currency、gross_amount、fee_amount、net_amount、cash_direction），第 3 行为明细列名（line_no、line_type、direction、amount、fee_amount、reference_type、external_transaction_id、transaction_date，其他列作为渠道特有列），其后每行一条明细。恒等式与匹配在完成导入时由对账服务校验。'
      : '文件格式：第 1、2 行为 #HEADER 开头的头部列名与取值（bank_account_last4、statement_ref、period_start、period_end、currency、opening_balance、closing_balance），第 3 行为流水列名（line_no、bank_transaction_ref、value_date、direction、amount、counterparty_name、reference_text），其后每行一条流水。期初 + 流水 = 期末在完成导入时校验。'}</FinanceNote>
  </Drawer>;
}

/** 完成导入的结果：通过显示编号与行数；被拒显示汇总原因与逐行原因（AC-V2）。 */
function ImportResult({ kind, result, reference }: { kind: Kind; result: ImportCompletion; reference: string }) {
  const accepted = result.status === 'MATCHING' || result.status === 'IMPORTED';
  if (!accepted) {
    return <>
      <Alert type="error" showIcon className="finance-banner" title={`${reference} 被拒绝：${result.rejectReason ?? '未通过校验'}`} />
      <RejectionTable rejections={result.rejections} />
    </>;
  }
  return <>
    <Alert type="success" showIcon className="finance-banner" title={kind === 'channel'
      ? `渠道账单 ${reference} 已导入 ${result.importedLineCount} 行，进入匹配`
      : `银行对账单 ${reference} 已导入 ${result.importedLineCount} 行`} />
    {kind === 'bank' && <Descriptions size="small" column={2} items={[
      { key: 'duplicate', label: '已存在而跳过的流水', children: result.duplicateLineCount ?? 0 },
      { key: 'gap', label: '与上一张的连续性', children: result.gapExceptionId ? `接不上，已开提示异常 ${result.gapExceptionId}` : '连续' },
    ]} />}
  </>;
}
