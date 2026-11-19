import { NextRequest, NextResponse } from "next/server";
import { getSession } from "@/lib/auth";
import { apiBase } from "@/lib/api";

export async function GET(request: NextRequest) {
  const session = await getSession();
  if (!session)
    return NextResponse.json({ message: "Phiên đăng nhập đã hết hạn." }, { status: 401 });
  if (!session.roles.includes("ADMIN"))
    return NextResponse.json({ message: "Bạn không có quyền xem báo cáo." }, { status: 403 });
  const days = request.nextUrl.searchParams.get("days") ?? "30";
  if (!["7", "30", "90"].includes(days))
    return NextResponse.json({ message: "Khoảng thời gian chưa hợp lệ." }, { status: 400 });
  try {
    const response = await fetch(`${apiBase()}/api/v1/admin/analytics/shop-activity?days=${days}`, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
      cache: "no-store",
      signal: AbortSignal.timeout(6000),
    });
    if (!response.ok)
      return NextResponse.json({ message: "Chưa thể tải báo cáo." }, { status: response.status });
    return NextResponse.json(await response.json(), { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ message: "Kết nối báo cáo tạm gián đoạn." }, { status: 503 });
  }
}
