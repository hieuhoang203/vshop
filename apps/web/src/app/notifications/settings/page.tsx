import Link from "next/link";
import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { getEmailNotifications } from "@/lib/api";
import { EmailPreference } from "@/components/notifications/email-preference";

export const metadata = { title: "Cài đặt thông báo" };

export default async function NotificationSettings() {
  if (!(await getSession())) redirect("/api/auth/login?returnTo=/notifications/settings");
  const email = await getEmailNotifications();
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">THÔNG BÁO</span>
        <h1>Cài đặt thông báo</h1>
        <p>
          Chọn kênh nhận thông báo. <Link href="/notifications">Quay lại thông báo</Link>
        </p>
      </div>
      <EmailPreference initial={email} />
    </div>
  );
}
