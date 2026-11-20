param([switch]$KeepDatabase, [switch]$ExportDictionary)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$database = 'vshop_design_review_' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
if ($database -notmatch '^vshop_design_review_[0-9]{14}$') { throw 'Invalid isolated database name.' }
$OutputEncoding = [Text.UTF8Encoding]::new($false)
Push-Location $root
try {
    & docker compose exec -T postgres createdb -U vshop $database
    if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated schema-check database.' }
    $files = @(
        'backend/src/main/resources/db/migration/V1__platform_merchant_catalog.sql',
        'backend/src/main/resources/db/migration/V2__event_receipts.sql',
        'backend/src/main/resources/db/migration/V1001__normalize_accounts_skus_and_inventory.sql',
        'backend/src/main/resources/db/migration/V1002__enforce_owner_membership_and_event_naming.sql',
        'backend/src/main/resources/db/migration/V1003__product_moderation.sql',
        'backend/src/main/resources/db/migration/V1004__cart_and_quote.sql',
        'backend/src/main/resources/db/migration/V1005__buyer_addresses.sql',
        'backend/src/main/resources/db/migration/V1006__policy_versions_and_generic_outbox.sql',
        'backend/src/main/resources/db/migration/V1007__orders.sql',
        'backend/src/main/resources/db/migration/V1008__manual_fulfillment.sql',
        'backend/src/main/resources/db/migration/V1009__seller_confirmation_policy.sql',
        'backend/src/main/resources/db/migration/V1010__payments.sql',
        'backend/src/main/resources/db/migration/V1011__shipments.sql',
        'backend/src/main/resources/db/migration/V1012__payment_sessions.sql',
        'backend/src/main/resources/db/migration/V1013__refunds.sql',
        'docs/database/target-commerce.sql',
        'docs/database/target-design-checks.sql'
    )
    foreach ($file in $files) {
        Get-Content -LiteralPath (Join-Path $root $file) -Raw |
            & docker compose exec -T postgres psql -q -v ON_ERROR_STOP=1 -U vshop -d $database
        if ($LASTEXITCODE -ne 0) { throw "Schema validation failed: $file" }
    }
    if ($ExportDictionary) {
        $core = "'identity.account','identity.address','merchant.shop','merchant.shop_member','merchant.shop_review','catalog.category','catalog.product','catalog.product_review','checkout.cart','checkout.cart_line','checkout.quote','catalog.product_sku','catalog.product_option','catalog.option_value','catalog.sku_option_value','inventory.warehouse','inventory.stock_balance','inventory.stock_reservation','inventory.stock_movement','platform.audit_event','platform.outbox_event','platform.event_receipt','payment.intent','payment.attempt','payment.charge','payment.allocation','payment.provider_event','payment.review_case','payment.refund','payment.refund_allocation','fulfillment.pickup_address','fulfillment.shipment','fulfillment.shipment_event','platform.policy_version','sales.checkout_group','sales.seller_order','sales.order_line','sales.order_line_reservation','sales.order_status_history'"
        $queries = @{
            'data-dictionary.csv' = "SELECT CASE WHEN c.table_schema||'.'||c.table_name IN ($core) THEN 'ImplementedSchema' ELSE 'ReferenceDesign' END AS schema_stage,c.table_schema,c.table_name,c.ordinal_position,c.column_name,c.data_type,c.udt_name,c.is_nullable,c.column_default,c.character_maximum_length,c.numeric_precision,c.numeric_scale,c.is_generated,c.generation_expression FROM information_schema.columns c WHERE c.table_schema NOT IN ('pg_catalog','information_schema','public') ORDER BY c.table_schema,c.table_name,c.ordinal_position"
            'constraints.csv' = "SELECT n.nspname AS table_schema,t.relname AS table_name,c.conname AS constraint_name,c.contype AS constraint_type,pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema','public') ORDER BY n.nspname,t.relname,c.conname"
            'indexes.csv' = "SELECT schemaname AS table_schema,tablename AS table_name,indexname,indexdef FROM pg_indexes WHERE schemaname NOT IN ('pg_catalog','information_schema','public') ORDER BY schemaname,tablename,indexname"
        }
        foreach ($name in $queries.Keys) {
            $copy = 'COPY (' + $queries[$name] + ') TO STDOUT WITH (FORMAT CSV, HEADER TRUE)'
            $csv = & docker compose exec -T postgres psql -q -U vshop -d $database -c $copy
            if ($LASTEXITCODE -ne 0) { throw "Dictionary export failed: $name" }
            $csv | Set-Content -LiteralPath (Join-Path $root "docs/database/$name") -Encoding utf8BOM
        }
    }
    & docker compose exec -T postgres psql -U vshop -d $database -c "SELECT table_schema, count(*) AS tables FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema NOT IN ('pg_catalog','information_schema') GROUP BY table_schema ORDER BY table_schema;"
    if ($LASTEXITCODE -ne 0) { throw 'Schema inspection failed.' }
    Write-Host "Reference DDL and ledger checks passed in isolated database $database."
} finally {
    if (-not $KeepDatabase) {
        # Delete only the exact database created for this verification run, never the application/test DB.
        & docker compose exec -T postgres dropdb -U vshop --if-exists $database
    }
    Pop-Location
}
