import { useRef, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Select, Table, Tag } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import {
  codePattern, commercialPaths, componentSummary, currencyPattern, dimensionCodes, entityNoPattern, idPattern, localInput, partnerTermTypes,
  termTypeLabels, termTypes, utcInstant,
} from '../commercial/commercial';
import type { PriceComponent } from '../commercial/commercial';
import { cutoffTime, decodeSimulation, simulationBody, simulationErrorMessage, simulationFieldName } from '../commercial/commercialPreview';
import type { ComponentIllustration, Simulation, SimulationFormValues } from '../commercial/commercialPreview';
import { CommercialNote } from '../commercial/CommercialParts';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const amountPattern = /^(0|[1-9][0-9]{0,14})(\.[0-9]{1,6})?$/;
const prefillKeys = ['termVersionId', 'agreementId', 'entityNo', 'clientId', 'currency', 'termType'] as const;

/**
 * 价格试算：输入条款版本、OE、币种、支付方式与维度事实，经 commercial 命中价格；填写示例金额时显示按命中价格计算的示例费用
 * <li>维度行留空表示事实未知；勾选「不适用」表示确认该维度不适用（提交 null）。命中失败显示 commercial 的错误码与原因。</li>
 * <li>支持 ?termVersionId=&agreementId=&entityNo=&clientId=&currency=&termType= 预填。</li>
 */
export function CommercialPriceSimulator({ onAuthorizationChanged }: Props) {
  const [params] = useSearchParams();
  const [form] = Form.useForm<SimulationFormValues>();
  const notify = useErrorNotification();
  const [result, setResult] = useState<Simulation | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const busy = useRef(false);
  const termType = Form.useWatch('termType', form) ?? 'CLIENT_PRICING';
  const partner = partnerTermTypes.includes(termType);
  const prefill = Object.fromEntries(prefillKeys.map(key => [key, params.get(key) ?? undefined]));
  const submit = async (values: SimulationFormValues) => {
    if (busy.current) return;
    busy.current = true; setRunning(true); setFailure(null);
    const body = simulationBody({ ...values, contextAsOfTime: utcInstant(values.contextAsOfTime) }, partner);
    try {
      const simulation = await session.request(commercialPaths.simulate, { method: 'POST', body: JSON.stringify(body),
        headers: { 'Content-Type': 'application/json' } }, decodeSimulation(body));
      setResult(simulation);
    } catch (error) {
      setResult(null);
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.fieldErrors.length) {
        form.setFields(error.fieldErrors.flatMap(item => {
          const name = simulationFieldName(item.field);
          return name ? [{ name, errors: [item.message] }] : [];
        }));
      }
      const message = simulationErrorMessage(error);
      if (message) setFailure(message); else notify(error, '价格试算失败');
    } finally { busy.current = false; setRunning(false); }
  };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>价格试算</h1></div>
    <Form form={form} name="commercial-price-simulation" layout="vertical" onFinish={values => void submit(values)}
      initialValues={{ termType: 'CLIENT_PRICING', currency: 'USD', paymentMethodCode: 'CARD', contextAsOfTime: localInput(new Date().toISOString()),
        dimensions: [], ...prefill, ownerId: prefill.clientId }}>
      <div className="commercial-sim-fields">
        <Form.Item name="termVersionId" label="条款版本 ID" className="sim-id" rules={[{ required: true, pattern: idPattern, message: '正十进制版本 ID' }]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="agreementId" label="合同 ID" className="sim-id" rules={[{ required: true, pattern: idPattern, message: '正十进制合同 ID' }]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="termType" label="条款类型" className="sim-type"><Select options={termTypes.map(value => ({ value, label: `${termTypeLabels[value]} ${value}` }))} /></Form.Item>
        <Form.Item name="ownerId" label={partner ? 'Local Partner ID' : 'Client ID'} className="sim-id" rules={[{ required: true, pattern: idPattern, message: '正十进制 ID' }]}>
          <Input maxLength={19} /></Form.Item>
        <Form.Item name="entityNo" label="OE" className="sim-short" normalize={(v: string) => v?.toUpperCase()} rules={[{ required: true, pattern: entityNoPattern, message: '如 AE001' }]}>
          <Input maxLength={5} placeholder="AE001" /></Form.Item>
        <Form.Item name="currency" label="币种" className="sim-short" normalize={(v: string) => v?.toUpperCase()} rules={[{ required: true, pattern: currencyPattern, message: '三位大写币种' }]}>
          <Input maxLength={3} /></Form.Item>
        <Form.Item name="contextAsOfTime" label="商业时点" className="sim-time" rules={[{ required: true, message: '请选择时点' }]}><Input type="datetime-local" step={1} /></Form.Item>
        <Form.Item name="paymentMethodCode" label="支付方式" className="sim-code" normalize={(v: string) => v?.toUpperCase()} rules={[{ pattern: codePattern, message: '大写代码，如 CARD' }]}>
          <Input maxLength={64} placeholder="可空" /></Form.Item>
        <Form.Item name="amount" label="示例金额" className="sim-code" tooltip="主单位，如 1000.00；为空只看命中价格" rules={[{ pattern: amountPattern, message: '非负十进制，最多 6 位小数' }]}>
          <Input maxLength={22} placeholder="可空" /></Form.Item>
      </div>
      <Form.List name="dimensions">{(fields, { add, remove }) => <div className="commercial-sim-dimensions">
        {fields.map(field => <div className="commercial-sim-dimension" key={field.key}>
          <Form.Item name={[field.name, 'dimensionCode']} rules={[{ required: true, message: '维度' }]}>
            <Select aria-label="维度" options={dimensionCodes.map(value => ({ value, label: value }))} /></Form.Item>
          <Form.Item name={[field.name, 'value']} normalize={(v: string) => v?.toUpperCase()}><Input placeholder="VISA / CREDIT / AE；留空=未知" /></Form.Item>
          <Form.Item name={[field.name, 'notApplicable']} valuePropName="checked"><Checkbox>不适用</Checkbox></Form.Item>
          <Button type="link" danger onClick={() => remove(field.name)}>移除</Button>
        </div>)}
        <div className="commercial-sim-actions">
          <Button type="dashed" onClick={() => add({ dimensionCode: 'CARD_BRAND' })}>+ 维度事实</Button>
          <Button type="primary" htmlType="submit" loading={running}>试算</Button>
        </div>
      </div>}</Form.List>
    </Form>
    {failure && <Alert type="warning" showIcon className="commercial-banner" style={{ marginTop: 16 }} title={failure} />}
    {result && <SimulationResult result={result} />}
    <CommercialNote>命中顺序：币种精确 → 支付方式精确 → 条件等值 → 维度数多者先 → 优先级小者先。示例费用由 BFF 按命中价格计算，仅供核对价目，不是计费结果。</CommercialNote>
  </div>;
}

interface ComponentRow { key: string; feeEventCode: string; component: PriceComponent; illustration: ComponentIllustration | null; total: string | null }

function SimulationResult({ result }: { result: Simulation }) {
  const { price, illustration } = result;
  const illustrated = new Map((illustration?.feeEvents ?? []).flatMap(event => event.components.map(c => [`${event.feeEventCode}|${c.componentNo}`, c] as const)));
  const totals = new Map((illustration?.feeEvents ?? []).map(event => [event.feeEventCode, event.totalAmount]));
  const rows: ComponentRow[] = price.components.toSorted((a, b) => a.triggerCode.localeCompare(b.triggerCode) || a.componentNo - b.componentNo)
    .map(component => ({ key: `${component.triggerCode}|${component.componentNo}`, feeEventCode: component.triggerCode, component,
      illustration: illustrated.get(`${component.triggerCode}|${component.componentNo}`) ?? null, total: totals.get(component.triggerCode) ?? null }));
  return <section className="commercial-block" style={{ marginTop: 16 }}>
    <div className="commercial-block-head"><h3>命中画像 <span className="mono">{price.pricingProfileCode}</span></h3>
      <Tag>{price.entityNo}</Tag><Tag>{price.currency}</Tag><span className="commercial-cell-meta">优先级 {price.priorityNo}</span>
      {price.paymentMethodCode && <span className="commercial-cell-meta">支付方式 {price.paymentMethodCode}</span>}
      <span className="commercial-cell-meta mono">价格 {price.priceId}</span>
    </div>
    <div className="commercial-limits">{price.conditions.length ? price.conditions.map(c => <Tag key={`${c.dimensionCode}-${c.conditionValue}`}>{c.dimensionCode}={c.conditionValue}</Tag>)
      : <span className="commercial-cell-meta">无条件（兜底画像）</span>}</div>
    <Table<ComponentRow> size="small" rowKey="key" pagination={false} dataSource={rows} scroll={{ x: 900 }} style={{ marginTop: 12 }} columns={[
      { title: '计费事件', width: 170, render: (_, row) => <span className="mono">{row.feeEventCode}</span> },
      { title: '组件', render: (_, row) => <div className="commercial-cell"><span>{row.component.componentNo}. {row.component.componentCode}</span>
        <span className="commercial-cell-meta">{row.component.calculationBase} · 退款 {row.component.refundBehavior}</span></div> },
      { title: '费率 / 金额', render: (_, row) => <div className="commercial-cell"><span>{componentSummary(row.component)}</span>
        {row.component.bands.length > 0 && <span className="commercial-cell-meta">{row.component.bands.map(band =>
          `[${band.lowerBound}, ${band.upperBound ?? '∞'}) ${band.rate ?? band.fixedAmount ?? ''}`).join('；')}</span>}</div> },
      ...(illustration ? [
        { title: `示例费用（${illustration.amount} ${illustration.currency}）`, width: 220, render: (_: unknown, row: ComponentRow) => row.illustration?.computed
          ? <div className="commercial-cell"><span>{row.illustration.amount}</span>{row.illustration.note && <span className="commercial-cell-meta">{row.illustration.note}</span>}</div>
          : <span className="commercial-cell-meta">{row.illustration?.note ?? '未计算'}</span> },
        { title: '事件合计', width: 120, render: (_: unknown, row: ComponentRow) => <span className="commercial-fee-total">{row.total ?? '—'}</span> },
      ] : []),
    ]} />
    {price.settlement && <div className="commercial-cell-meta" style={{ marginTop: 8 }}>结算 T+{price.settlement.settlementBusinessDays} · {price.settlement.businessCalendarCode} ·
      {' '}{price.settlement.settlementTimezone} 截止 {cutoffTime(price.settlement.cutoffSecondOfDay)}</div>}
  </section>;
}
