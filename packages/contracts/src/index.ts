/**
 * API types for the web and mobile clients.
 *
 * Everything here is derived from `generated/api.ts`, which is generated from `docs/api/openapi.json`,
 * which is generated from the Java controllers and DTOs (see docs/19-openapi-contracts.md). Do not
 * hand-write API shapes in this file: change the backend, then run `npm run openapi:update`.
 * The names below are the stable vocabulary the apps import; they are only aliases.
 */
import type { components, operations, paths } from "./generated/api";

export type { components, operations, paths };
type Schemas = components["schemas"];

/** Every error response has this body; the HTTP status says which kind it is. */
export type ApiError = Schemas["ApiErrorResponse"];

// Catalog (public)
export type Category = Schemas["CategoryResponse"];
export type Product = Schemas["ProductResponse"];
export type CatalogPage = Schemas["PageResponseProductResponse"];
export type PublicSku = Schemas["PublicSkuResponse"];

// Shops and their review
export type Shop = Schemas["ShopResponse"];
export type ShopStatus = Shop["status"];
export type ShopActivityReport = Schemas["ShopActivityResponse"];
export type ReviewShopInput = Schemas["ReviewShopRequest"];
export type SubmitShopInput = Schemas["SubmitShopRequest"];

// Seller catalog and moderation
export type SellerProduct = Schemas["SellerProductResponse"];
export type SellerProductPage = Schemas["PageResponseSellerProductResponse"];
export type SellerOption = Schemas["OptionResponse"];
export type SellerSku = Schemas["SkuResponse"];
export type ProductStatus = SellerProduct["status"];
export type SkuStatus = SellerSku["status"];
export type NewProductInput = Schemas["CreateProductRequest"];
export type UpdateProductInput = Schemas["UpdateProductRequest"];
export type ChangeProductStatusInput = Schemas["ChangeProductStatusRequest"];
export type AddSkuInput = Schemas["SkuRequest"];
export type UpdateSkuInput = Schemas["UpdateSkuRequest"];
export type ReviewProductInput = Schemas["ReviewProductRequest"];

// Inventory
export type StockBalance = Schemas["StockBalanceResponse"];
export type StockMovement = Schemas["StockMovementResponse"];
export type StockAdjustmentInput = Schemas["StockAdjustmentRequest"];

// Cart and quotes
export type CartLineInput = Schemas["CartLineRequest"];
export type SetQuantityInput = Schemas["SetQuantityRequest"];
export type CreateQuoteInput = Schemas["CreateQuoteRequest"];
export type CartContent = Schemas["CartResponse"];
export type CartGroup = Schemas["ShopGroupResponse"];
export type CartLine = Schemas["LineResponse"];
export type CartLineIssue = NonNullable<CartLine["issue"]>;
export type Quote = Schemas["QuoteResponse"];

// Address book
export type Address = Schemas["AddressResponse"];
export type AddressInput = Schemas["AddressRequest"];
export type UpdateAddressInput = Schemas["UpdateAddressRequest"];

// Account
export type Profile = Schemas["ProfileResponse"];

// Orders and checkout
export type PlaceOrderInput = Schemas["PlaceOrderRequest"];
export type CheckoutGroup = Schemas["CheckoutGroupResponse"];
export type Order = Schemas["OrderResponse"];
export type OrderLine = Schemas["OrderLineResponse"];
export type OrderSummary = Schemas["OrderSummaryResponse"];
export type OrderPage = Schemas["PageResponseOrderSummaryResponse"];

// Policies (admin)
export type Policy = Schemas["PolicyResponse"];
export type CreatePolicyInput = Schemas["CreatePolicyRequest"];

// Seller order handling and buyer cancel
export type SellerOrder = Schemas["SellerOrderResponse"];
export type SellerOrderPage = Schemas["PageResponseSellerOrderResponse"];
export type ReasonInput = Schemas["ReasonRequest"];
export type ShipOrderInput = Schemas["ShipOrderRequest"];

// Carrier shipping
export type PickupAddress = Schemas["PickupAddressResponse"];
export type PickupAddressInput = Schemas["PickupAddressRequest"];
export type CreateShipmentInput = Schemas["CreateShipmentRequest"];

// Support queue
export type OverdueOrders = Schemas["OverdueOrdersResponse"];

// Refunds
export type Refund = Schemas["RefundResponse"];
export type AdminRefund = Schemas["AdminRefundResponse"];
export type RefundAccountInput = Schemas["RefundAccountRequest"];
export type RefundPaidInput = Schemas["RefundPaidRequest"];

// Return cases
export type ReturnCase = Schemas["ReturnCaseResponse"];
export type ReturnCasePage = Schemas["PageResponseReturnCaseResponse"];
export type OpenReturnInput = Schemas["OpenReturnRequest"];
export type OfferRefundInput = Schemas["OfferRefundRequest"];
export type ConfirmReceivedInput = Schemas["ConfirmReceivedRequest"];
export type DecideReturnInput = Schemas["DecideReturnRequest"];
