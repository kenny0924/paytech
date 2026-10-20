import { useEffect, useState } from 'react';
import { Button, Input, Select } from 'antd';
import { requestAllPages } from '../api/paging';
import { DatePicker } from '../components/DatePicker';
import {
  amountPattern, channelPaths, codePattern, costVersionBody, costVersionIssues, countryPattern, currencyPattern, decodeCostVersion, decodeMidPage,
  entityNoPattern, newComponentDraft, ratePattern, scopeDrafts, utcInstant,
} from './channel';
import type { ChannelMidSummary, ComponentDraft, CostVersion, ScopeDraft } from './channel';
import { ChannelNote, CommandFeedback } from './ChannelParts';
import { useChannelCommand } from './useChannelCommand';

interface Props {
  planCode: string; channelId: string; source?: CostVersion; onSaved: (version: CostVersion) => void; onCancel: () => void;
  onAuthorizationChanged: () => void;
}

const emptyScope = (): ScopeDraft => ({ midId: '', entityNo: '', countryCode: '', currency: '', paymentMethodCode: '', actionType: '', components: [newComponentDraft()] });
const invalid = (value: string | undefined, pattern: RegExp, required = true) => {
  const v = value?.trim() ?? '';
  return v === '' ? (required ? 'error' as const : undefined) : pattern.test(v) ? undefined : 'error' as const;
};
const upper = (value: string) => value.trim().toUpperCase();
const componentFields: { key: keyof ComponentDraft; label: string; pattern: RegExp; required: boolean; upper?: boolean }[] = [
  { key: 'componentCode', label: '组成代码', pattern: codePattern, required: true, upper: true },
  { key: 'componentType', label: '类型', pattern: codePattern, required: true, upper: true },
  { key: 'calculationBase', label: '计算基数', pattern: codePattern, required: true, upper: true },
  { key: 'rateValue', label: '费率', pattern: ratePattern, required: false },
  { key: 'fixedAmount', label: '固定费', pattern: amountPattern, required: false },
  { key: 'currency', label: '币种', pattern: currencyPattern, required: false, upper: true },
  { key: 'minAmount', label: '下限', pattern: amountPattern, required: false },
  { key: 'maxAmount', label: '上限', pattern: amountPattern, required: false },
  { key: 'roundingMode', label: '舍入', pattern: codePattern, required: true, upper: true },
];

/**
 * 版本编制（画板 11）：生效时间（UTC）+ 范围块（MID 下拉限本渠道 ACTIVE、OE 随 MID 带出、国家、币种、支付方式、动作、合同引用）+ 组成行
 * <li>「保存草稿」创建 DRAFT 版本；「保存并提交审批」创建后立即以当前操作者提交（Maker）。组成类型、计算基数与舍入代码由 Owner 目录校验。</li>
 * <li>来源版本存在时以其范围与组成作为初稿（复制后修改）。</li>
 */
export function CostVersionEditor({ planCode, channelId, source, onSaved, onCancel, onAuthorizationChanged }: Props) {
  const command = useChannelCommand(onAuthorizationChanged);
  const [effectiveFrom, setEffectiveFrom] = useState<string | undefined>();
  const [scopes, setScopes] = useState<ScopeDraft[]>(source ? scopeDrafts(source) : [emptyScope()]);
  const [mids, setMids] = useState<ChannelMidSummary[]>([]);
  const [midFailure, setMidFailure] = useState(false);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages(channelPaths.mids, controller.signal, undefined, { channelId, status: 'ACTIVE' }, decodeMidPage)
      .then(setMids).catch(() => { if (!controller.signal.aborted) setMidFailure(true); });
    return () => controller.abort();
  }, [channelId]);
  const effectiveInstant = effectiveFrom ? utcInstant(effectiveFrom) : undefined;
  const issues = costVersionIssues(effectiveInstant, scopes);
  const setScope = (index: number, patch: Partial<ScopeDraft>) => setScopes(scopes.map((s, i) => i === index ? { ...s, ...patch } : s));
  const setComponent = (scopeIndex: number, index: number, patch: Partial<ComponentDraft>) => setScope(scopeIndex,
    { components: scopes[scopeIndex].components.map((c, i) => i === index ? { ...c, ...patch } : c) });
  const save = async (submit: boolean) => {
    setTouched(true);
    if (issues.length || !effectiveInstant) return;
    const created = await command.run(channelPaths.costVersions(planCode), 'POST', costVersionBody(effectiveInstant, scopes),
      decodeCostVersion(planCode), '版本创建失败');
    if (!created) return;
    if (!submit) { onSaved(created); return; }
    const submitted = await command.run(channelPaths.costVersionAction(planCode, created.costVer, 'submit'), 'POST', undefined,
      decodeCostVersion(planCode, created.costVer), '提交审批失败');
    onSaved(submitted ?? created);
  };
  return <>
    <div className="page-heading"><h1>新建版本 · {planCode}</h1>
      <div className="channel-actions"><Button onClick={onCancel} disabled={command.saving}>取消</Button>
        <Button loading={command.saving} onClick={() => void save(false)}>保存草稿</Button>
        <Button type="primary" loading={command.saving} onClick={() => void save(true)}>保存并提交审批</Button></div>
    </div>
    <CommandFeedback command={command} onReload={onCancel} />
    <div className="channel-form-grid">
      <div><div className="channel-cell-meta">生效时间（按本地时间选择，提交为 UTC）</div>
        <DatePicker showTime value={effectiveFrom} onChange={setEffectiveFrom} status={touched && !effectiveFrom ? 'error' : undefined} /></div>
      <ChannelNote>版本号由系统分配；生效时间必须晚于当前版本的生效时间；已有 DRAFT 或 PENDING_APPROVAL 版本时不能再建。</ChannelNote>
    </div>
    {midFailure && <ChannelNote tone="warning">本渠道 ACTIVE MID 加载失败，MID 下拉为空；请稍后刷新。</ChannelNote>}
    {scopes.map((scope, scopeIndex) => <section className="channel-scope" key={scopeIndex}>
      <div className="channel-scope-head"><h3>范围 {scopeIndex + 1}</h3>
        <Button type="link" onClick={() => setScopes([...scopes.slice(0, scopeIndex + 1), structuredClone(scope), ...scopes.slice(scopeIndex + 1)])}
          disabled={scopes.length >= 200}>复制范围</Button>
        <Button type="link" danger onClick={() => setScopes(scopes.filter((_, i) => i !== scopeIndex))}>删除范围</Button></div>
      <div className="channel-scope-grid">
        <label><span className="channel-cell-meta">MID</span>
          <Select aria-label="MID" showSearch optionFilterProp="label" value={scope.midId || undefined} status={touched && !scope.midId ? 'error' : undefined}
            options={mids.map(mid => ({ value: mid.midId, label: `${mid.externalMidCode} · ${mid.midId}` }))}
            onChange={midId => setScope(scopeIndex, { midId, entityNo: mids.find(m => m.midId === midId)?.entityNo ?? scope.entityNo })} /></label>
        <label><span className="channel-cell-meta">OE</span>
          <Input value={scope.entityNo} maxLength={5} status={touched ? invalid(scope.entityNo, entityNoPattern) : undefined}
            onChange={e => setScope(scopeIndex, { entityNo: upper(e.target.value) })} /></label>
        <label><span className="channel-cell-meta">国家</span>
          <Input value={scope.countryCode} maxLength={2} placeholder="AE" status={touched ? invalid(scope.countryCode, countryPattern) : undefined}
            onChange={e => setScope(scopeIndex, { countryCode: upper(e.target.value) })} /></label>
        <label><span className="channel-cell-meta">币种</span>
          <Input value={scope.currency} maxLength={3} placeholder="AED" status={touched ? invalid(scope.currency, currencyPattern) : undefined}
            onChange={e => setScope(scopeIndex, { currency: upper(e.target.value) })} /></label>
        <label><span className="channel-cell-meta">支付方式</span>
          <Input value={scope.paymentMethodCode} maxLength={64} placeholder="CARD" status={touched ? invalid(scope.paymentMethodCode, codePattern) : undefined}
            onChange={e => setScope(scopeIndex, { paymentMethodCode: upper(e.target.value) })} /></label>
        <label><span className="channel-cell-meta">动作</span>
          <Input value={scope.actionType} maxLength={64} placeholder="PAY" status={touched ? invalid(scope.actionType, codePattern) : undefined}
            onChange={e => setScope(scopeIndex, { actionType: upper(e.target.value) })} /></label>
        <label><span className="channel-cell-meta">渠道合同引用</span>
          <Input value={scope.channelAgreementRef ?? ''} maxLength={128} placeholder="渠道合同编号"
            status={touched && !(scope.channelAgreementRef ?? '').trim() ? 'error' : undefined}
            onChange={e => setScope(scopeIndex, { channelAgreementRef: e.target.value })} /></label>
      </div>
      <div className="channel-component-head">{componentFields.map(field => <span key={field.key}>{field.label}</span>)}<span /></div>
      {scope.components.map((component, index) => <div className="channel-component-row" key={index}>
        {componentFields.map(field => <Input key={field.key} aria-label={field.label} placeholder={field.label} value={component[field.key] ?? ''}
          status={touched ? invalid(component[field.key], field.pattern, field.required) : undefined}
          onChange={e => setComponent(scopeIndex, index, { [field.key]: field.upper ? upper(e.target.value) : e.target.value.trim() })} />)}
        <Button type="link" danger onClick={() => setScope(scopeIndex, { components: scope.components.filter((_, i) => i !== index) })}>删除</Button>
      </div>)}
      <Button type="link" disabled={scope.components.length >= 20}
        onClick={() => setScope(scopeIndex, { components: [...scope.components, newComponentDraft(scope.currency)] })}>+ 添加组成</Button>
    </section>)}
    <Button type="link" disabled={scopes.length >= 200} onClick={() => setScopes([...scopes, emptyScope()])}>+ 添加范围</Button>
    {touched && issues.length > 0 && <ul className="channel-issues">{issues.slice(0, 12).map(issue => <li key={issue}>{issue}</li>)}
      {issues.length > 12 && <li>另有 {issues.length - 12} 项问题</li>}</ul>}
    <ChannelNote>费率为十进制比例（0.0290 即 2.90%），金额为主单位十进制；填写金额时需要组成币种。组成类型、计算基数与舍入方式以 channel-connector 目录为准。</ChannelNote>
  </>;
}
