SELECT id, customer_name, status
FROM orders
WHERE status = 'ACTIVE'
ORDER BY id;
