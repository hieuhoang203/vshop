import { ShieldCheck, Inbox } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getShopQueue, getShopActivity } from "@/lib/api";
import { ShopActivityChart } from "@/components/admin/shop-activity-chart";
import { LoginPrompt } from "@/components/portal";
import { MfaRequired } from "@/components/admin/mfa-required";
import { ReviewForm } from "@/components/review-form";
import { PortalNav } from "@/components/portal-nav";
export const metadata = { title: "Quản trị" };
export default async function Admin() {
  const session = await getSession();
  if (!session) return <LoginPrompt target="admin" />;
  if (!session.roles.includes("ADMIN"))
    return (
      <div className="container empty-state page-space">
        <ShieldCheck size={40} />
        <h1>Bạn không có quyền truy cập</h1>
        <p>Khu vực này dành cho tài khoản quản trị được phân quyền.</p>
      </div>
    );
  if (!session.mfa) return <MfaRequired />;
  const shops = await getShopQueue();
  const report = await getShopActivity(30).catch(() => null);
  return (
    <div className="container page-space admin-page">
      <div className="portal-heading">
        <span className="eyebrow">QUẢN TRỊ VSHOP</span>
        <h1>Xét duyệt gian hàng</h1>
        <p>Kiểm tra hồ sơ và ghi nhận quyết định trước khi gian hàng hoạt động.</p>
      </div>
      <PortalNav portal="admin" current="/admin" />
      <ShopActivityChart initialReport={report} />
      <div className="queue-summary">
        <span className="portal-symbol small-symbol">
          <Inbox size={24} />
        </span>
        <div>
          <strong>{shops.length}</strong>
          <span>hồ sơ đang chờ xử lý</span>
        </div>
      </div>
      {shops.length ? (
        shops.map((shop) => (
          <article className="panel review-card" key={shop.id}>
            <div className="review-card-header">
              <div>
                <h2>{shop.name}</h2>
                <p>
                  Gửi lúc{" "}
                  {new Intl.DateTimeFormat("vi-VN", {
                    dateStyle: "medium",
                    timeStyle: "short",
                    timeZone: "Asia/Ho_Chi_Minh",
                  }).format(new Date(shop.createdAt))}
                </p>
              </div>
              <span className="status status-submitted">Chờ xét duyệt</span>
            </div>
            <p className="shop-description">{shop.description}</p>
            <ReviewForm shopId={shop.id} />
          </article>
        ))
      ) : (
        <div className="panel empty-state">
          <Inbox size={34} />
          <h2>Đã xử lý hết hồ sơ</h2>
          <p>Hồ sơ mới sẽ xuất hiện tại đây khi người bán gửi đăng ký.</p>
        </div>
      )}
    </div>
  );
}
