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
