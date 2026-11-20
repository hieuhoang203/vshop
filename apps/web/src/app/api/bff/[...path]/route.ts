import { NextRequest, NextResponse } from "next/server";
import { getSession, webBase } from "@/lib/auth";
import { apiBase } from "@/lib/api";

const uuid = "[0-9a-fA-F-]{36}";
const shop = `seller/shops/${uuid}`;
const product = `${shop}/products/${uuid}`;
const routes: { method: string; path: RegExp; maxBytes: number }[] = [
  { method: "POST", path: /^seller\/shops$/, maxBytes: 16_384 },
  { method: "PUT", path: new RegExp(`^${shop}$`), maxBytes: 16_384 },
  { method: "POST", path: new RegExp(`^admin/shops/${uuid}/review$`), maxBytes: 16_384 },
  // Catalog: product with up to 100 SKUs fits in 128 KiB.
  { method: "POST", path: new RegExp(`^${shop}/products$`), maxBytes: 131_072 },
  { method: "PUT", path: new RegExp(`^${product}$`), maxBytes: 16_384 },
  { method: "PUT", path: new RegExp(`^${product}/status$`), maxBytes: 1_024 },
  { method: "POST", path: new RegExp(`^${product}/skus$`), maxBytes: 4_096 },
  { method: "PUT", path: new RegExp(`^${product}/skus/${uuid}$`), maxBytes: 1_024 },
  { method: "POST", path: new RegExp(`^admin/products/${uuid}/review$`), maxBytes: 4_096 },
  // Buyer cart and quotes
  { method: "POST", path: /^cart\/lines$/, maxBytes: 1_024 },
  { method: "PUT", path: new RegExp(`^cart/lines/${uuid}$`), maxBytes: 1_024 },
  { method: "DELETE", path: new RegExp(`^cart/lines/${uuid}$`), maxBytes: 64 },
  { method: "DELETE", path: /^cart$/, maxBytes: 64 },
  { method: "POST", path: /^checkout\/quotes$/, maxBytes: 256 },
  // Address book (owner only; the API scopes every call to the token's account)
  { method: "POST", path: /^me\/addresses$/, maxBytes: 2_048 },
  { method: "PUT", path: new RegExp(`^me/addresses/${uuid}$`), maxBytes: 2_048 },
  { method: "PUT", path: new RegExp(`^me/addresses/${uuid}/default$`), maxBytes: 64 },
  { method: "DELETE", path: new RegExp(`^me/addresses/${uuid}$`), maxBytes: 64 },
  // Inventory

  { method: "POST", path: new RegExp(`^${shop}/inventory/adjustments$`), maxBytes: 2_048 },
  { method: "GET", path: new RegExp(`^${shop}/inventory/skus/${uuid}$`), maxBytes: 0 },
  { method: "GET", path: new RegExp(`^${shop}/inventory/skus/${uuid}/movements$`), maxBytes: 0 },
];
const idempotencyKey = /^[A-Za-z0-9._:-]{8,100}$/;

async function forward(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  // Mutations carry an Origin header. Same-origin GETs usually do not, so they must prove
  // same-origin through Fetch Metadata instead.
  const origin = request.headers.get("origin");
  const sameOrigin =
    request.method === "GET"
      ? request.headers.get("sec-fetch-site") === "same-origin" ||
        origin === new URL(webBase()).origin
      : origin === new URL(webBase()).origin;
  if (!sameOrigin) return NextResponse.json({ message: "Yêu cầu không hợp lệ." }, { status: 403 });
  const session = await getSession();
  if (!session)
    return NextResponse.json(
      { message: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại." },
      { status: 401 },
    );
  const path = (await context.params).path.join("/");
  const route = routes.find((r) => r.method === request.method && r.path.test(path));
  if (!route) return NextResponse.json({ message: "Không tìm thấy thao tác." }, { status: 404 });
  const headers: Record<string, string> = { Authorization: `Bearer ${session.accessToken}` };
  let body: string | undefined;
  if (request.method !== "GET") {
    body = await request.text();
    if (Buffer.byteLength(body, "utf8") > route.maxBytes)
      return NextResponse.json({ message: "Thông tin vượt giới hạn." }, { status: 413 });
    headers["Content-Type"] = "application/json";
    const key = request.headers.get("idempotency-key");
    if (key !== null) {
      if (!idempotencyKey.test(key))
        return NextResponse.json({ message: "Khóa idempotency không hợp lệ." }, { status: 400 });
      headers["Idempotency-Key"] = key;
    }
  }
  const query = request.method === "GET" ? request.nextUrl.search : "";
  try {
    const result = await fetch(`${apiBase()}/api/v1/${path}${query}`, {
      method: request.method,
      headers,
      body,
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
    });
    // A body is not allowed on 204 (for example a successful DELETE).
    if (result.status === 204)
      return new NextResponse(null, { status: 204, headers: { "Cache-Control": "no-store" } });
    const data = await result.json().catch(() => ({
      message:
        result.status === 403
          ? "Bạn không có quyền thực hiện thao tác này."
          : "Không thể thực hiện yêu cầu.",
    }));
    return NextResponse.json(data, {
      status: result.status,
      headers: { "Cache-Control": "no-store" },
    });
  } catch {
    return NextResponse.json(
      {
        message: "Kết nối tạm gián đoạn. Vui lòng kiểm tra trạng thái hồ sơ trước khi thử lại.",
      },
      { status: 503 },
    );
  }
}
export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const DELETE = forward;
