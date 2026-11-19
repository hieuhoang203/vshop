# VShop engineering conventions

## Architecture

- Read `docs/architecture/README.md` and `docs/database/README.md` before changing domain code or schema.
- Organize backend code under `vn.vshop.modules.<domain>` with `controller`, `dto/request`, `dto/response`, `mapper`, `service`, `repository`, `repository/jdbc`, `entity` and query `model` packages as needed.
- Controllers and workers call application services. All SQL and JDBC APIs belong in repository adapters.
- Services own transactions and business guards; they must not depend on servlet/HTTP types, DTOs or concrete JDBC adapters.
- Return response DTOs, not persistence entities. Keep entities framework-independent while JDBC is the chosen adapter.
- Do not add empty controllers or fake service implementations for future modules.
- Cross-module writes go through the owning module's service. Explicitly documented catalog read joins are allowed in this monolith.

## Database

- Applied Flyway migrations are immutable, including local V1000. New production migrations continue after V1002.
- Back up existing data before a migration that moves or removes columns; validate both fresh creation and upgrade preservation.
- Product content, SKU price and warehouse inventory are separate data owners. Keep money exact; never use floating point for financial calculations.
- Protect tenant relationships with composite foreign keys when IDs alone cannot prevent cross-shop linkage.
- `docs/database/target-commerce.sql` is reference DDL, not an application migration. Validate it only in an isolated design database.
- Update the database dictionary after schema changes with `scripts/verify-db-design.ps1 -ExportDictionary`.

## Formatting and verification

- General UI uses Radix Themes and Primitives. Keep accessible names, keyboard navigation and focus restoration intact when customizing visual styles.
- Storefronts on web and native mobile put products first: search/categories then real product images, names and prices visible in the initial viewport. Marketing banners, mood widgets and seller recruitment stay after the product list; do not reintroduce a large hero above products.
- Charts follow the shadcn Area Chart pattern with Recharts and the shared `components/ui/chart.tsx` adapter; adapt styling to the existing CSS system.
- Keep UI by feature under `components/discovery`, `components/admin` and shared `components/ui`. Split CSS by concern under `src/styles`; `globals.css` only assembles those styles.
- Show analytics from protected backend queries, with explicit metric definitions and Vietnam calendar-day boundaries. Never fabricate commerce metrics.
- Saved product snapshots currently live only on the user's device. Do not imply account synchronization or live prices for snapshots.
- Native mobile lives in `apps/mobile` with Expo Router routes under `src/app`, feature screens/hooks under `src/features`, shared native UI under `src/components`, and transport in `src/services`. Routes stay thin; use `@vshop/contracts` for API types.
- Radix and the web shadcn/Recharts adapter are for web. Native screens use React Native components and share visual tokens/conventions, not DOM/CSS or Next.js BFF cookies.
- Mobile auth uses the `vshop-mobile` public Keycloak client with Authorization Code + PKCE S256 and the exact registered callback. Refresh tokens belong in SecureStore; do not put tokens in AsyncStorage, navigation params or logs. Backend remains authoritative for authorization.
- Run mobile typecheck, native component/session tests, Expo dependency checks and both platform exports after mobile changes. Distinguish bundles, installable Android APKs and runtime device validation; iOS export is not a signed IPA or App Store submission.

- Java: Spotless/google-java-format AOSP, 4 spaces. Run `backend/mvnw.cmd -f backend/pom.xml spotless:apply`.
- Web/config: Prettier, 2 spaces. Run `npm run format` and `npm run format:check`.
- Backend checks: `pwsh -File scripts/test-backend.ps1`; `verify` in CI also enforces Java formatting and ArchUnit rules.
- Database reference checks: `pwsh -File scripts/verify-db-design.ps1`.
- Web checks: `npm run typecheck`, `npm run build`; run browser E2E after changing API contracts, authentication or visible behavior.
- Tests use isolated PostgreSQL databases; never point test URLs at production. Keep generated credentials, backups and reports under ignored local paths.
- Report implemented schema, reference design and unimplemented business flows separately.
