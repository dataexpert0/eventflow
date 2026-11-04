
CREATE TABLE orders (
                        id UUID PRIMARY KEY,
                        customer_id UUID NOT NULL,
                        status VARCHAR(20) NOT NULL
                            DEFAULT 'PENDING',
                        created_at TIMESTAMPTZ NOT NULL
                            DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL
                            DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT chk_order_status
                            CHECK (status IN (
                                              'PENDING',
                                              'CONFIRMED',
                                              'CANCELLED'
                                ))
);

CREATE TABLE order_items (
                             id UUID PRIMARY KEY,
                             order_id UUID NOT NULL,
                             product_id UUID NOT NULL,
                             quantity INTEGER NOT NULL,
                             unit_price NUMERIC(19, 2) NOT NULL,

                             CONSTRAINT fk_order_items_order
                                 FOREIGN KEY (order_id)
                                     REFERENCES orders(id),

                             CONSTRAINT chk_order_item_quantity
                                 CHECK (quantity > 0),

                             CONSTRAINT chk_order_item_unit_price
                                 CHECK (unit_price >= 0),

                             CONSTRAINT uq_order_product
                                 UNIQUE (order_id, product_id)
);

CREATE INDEX idx_orders_customer_id
    ON orders(customer_id);

CREATE INDEX idx_order_items_order_id
    ON order_items(order_id);
