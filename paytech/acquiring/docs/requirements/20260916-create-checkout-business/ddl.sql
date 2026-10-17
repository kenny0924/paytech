-- 仅手工执行；目标 mor-local / 127.0.0.1:13306 / mor_commerce。
-- 执行前备份 t_commerce_order 结构。只新增可空列，不修改历史行。
-- 新创建订单必须写入本列；NULL 仅用于历史订单，继续按旧快照读取。
ALTER TABLE mor_commerce.t_commerce_order
    ADD COLUMN consumer_country_code CHAR(2) NULL
    COMMENT '基础创建时固定的消费者 ISO 3166-1 alpha-2 国家代码；NULL 仅用于历史订单读取旧创建快照。'
    AFTER client_order_id;
