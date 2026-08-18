import test from "node:test";
import assert from "node:assert/strict";

const workerUrl = new URL("../dist/server/index.js", import.meta.url);

async function renderPage() {
  const worker = await import(workerUrl.href);
  const response = await worker.default.fetch(
    new Request("http://localhost/"),
    {},
    { waitUntil() {}, passThroughOnException() {} },
  );
  assert.equal(response.status, 200);
  return response.text();
}

test("checkout renders inline order snapshot as the MVP sales model", async () => {
  const content = await renderPage();

  assert.match(content, /下单时传入商品与金额/);
  assert.match(content, /不可变订单快照/);
  assert.match(content, /Order 持久化成功后才创建可支付 Session/);
  assert.equal(content.match(/Offering/g)?.length, 1);
  assert.match(content, /第一版不创建 Offering/);
});

test("finance renders calculation separately from double-entry posting", async () => {
  const content = await renderPage();

  assert.match(content, /计费与分润计算/);
  assert.match(content, /复式账本与余额/);
  assert.match(content, /预计成本/);
  assert.match(content, /实际成本/);
  assert.match(content, /收入减成本/);
  assert.match(content, /成本差异生成新的 Accounting Instruction/);
});

test("system catalog renders six functional systems instead of deployment-level fragments", async () => {
  const content = await renderPage();

  assert.equal(content.match(/class="system-card"/g)?.length, 6);
  for (const system of [
    "Client &amp; Contract System",
    "Commerce System",
    "Payment Orchestration System",
    "Risk &amp; Compliance System",
    "Finance System",
    "Platform Support System",
  ]) {
    assert.match(content, new RegExp(system));
  }
  for (const section of ["独立用例", "接口用例", "MQ 通知", "不负责"]) {
    assert.match(content, new RegExp(section));
  }
  assert.doesNotMatch(content, /14 组业务与支撑系统/);
});

test("system catalog distinguishes synchronous interfaces from MQ notifications", async () => {
  const content = await renderPage();

  assert.match(content, /同步命令和查询通过系统接口/);
  assert.match(content, /MQ 仅用于内部回调和业务事实通知/);
  assert.match(content, /订阅 MQ/);
  assert.doesNotMatch(content, /ReserveHoldRequested|ReserveReleaseRequested/);
});

test("system boundaries prohibit cross-database access and keep entry apps stateless", async () => {
  const content = await renderPage();

  assert.match(content, /禁止跨库查询和写入/);
  assert.match(content, /入口应用不拥有核心业务数据/);
});

test("payment executes only Commerce-approved refund requests", async () => {
  const content = await renderPage();

  assert.match(content, /必须引用 Commerce refundRequestId/);
});

test("entry applications remain outside the six core systems", async () => {
  const content = await renderPage();

  assert.equal(content.match(/class="entry-app-card"/g)?.length, 7);
  assert.equal(content.match(/class="system-card"/g)?.length, 6);
  assert.match(content, /Client Console/);
  assert.match(content, /Partner Console/);
  assert.match(content, /Operations Console/);
  assert.match(content, /Provider Callback Endpoint/);
});

test("Partner Console is read-only for transactions and can request only share payouts", async () => {
  const content = await renderPage();

  assert.match(content, /提交主体信息与开户材料/);
  assert.match(content, /查询关联主体、MID 和脱敏交易明细/);
  assert.match(content, /提交 Partner 出款申请/);
  assert.match(content, /仅限已形成的 Partner 可用分润余额/);
  assert.match(content, /禁止操作订单、支付、退款、路由和账本/);
  assert.match(content, /平台自有主体不创建 Partner 关系/);
});
