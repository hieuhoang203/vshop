import { NextRequest, NextResponse } from "next/server";
import { getSession, webBase } from "@/lib/auth";
import { apiBase } from "@/lib/api";
async function forward(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  if (request.headers.get("origin") !== new URL(webBase()).origin)
    return NextResponse.json({ message: "Yêu cầu không hợp lệ." }, { status: 403 });
  const session = await getSession();
  if (!session)
    return NextResponse.json(
      { message: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại." },
      { status: 401 },
    );
  const path = (await context.params).path.join("/");
  const uuid = "[0-9a-fA-F-]{36}";
  const allowed =
    (request.method === "POST" &&
      (path === "seller/shops" || new RegExp(`^admin/shops/${uuid}/review$`).test(path))) ||
    (request.method === "PUT" && new RegExp(`^seller/shops/${uuid}$`).test(path));
  if (!allowed) return NextResponse.json({ message: "Không tìm thấy thao tác." }, { status: 404 });
  const body = await request.text();
  if (Buffer.byteLength(body, "utf8") > 16_384)
    return NextResponse.json({ message: "Thông tin vượt giới hạn." }, { status: 413 });
  try {
    const result = await fetch(`${apiBase()}/api/v1/${path}`, {
      method: request.method,
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        "Content-Type": "application/json",
      },
      body,
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
    });
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
export const POST = forward;
export const PUT = forward;
