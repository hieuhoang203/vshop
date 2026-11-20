import Link from "next/link";
import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { getNotifications } from "@/lib/api";
import { NotificationList } from "@/components/notifications/notification-list";

export const metadata = { title: "Thông báo" };

export default async function Notifications() {
  if (!(await getSession())) redirect("/api/auth/login?returnTo=/notifications");
  const page = await getNotifications();
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">THÔNG BÁO</span>
        <h1>Thông báo của tôi</h1>
        <p>Cập nhật về đơn hàng, hoàn tiền và yêu cầu trả hàng của bạn.</p>
        <p>
          <Link href="/notifications/settings">Cài đặt thông báo email</Link>
        </p>
      </div>
      {page.items.length ? (
        <NotificationList items={page.items} />
      ) : (
        <div className="panel empty-state">Chưa có thông báo nào.</div>
      )}
    </div>
  );
}
