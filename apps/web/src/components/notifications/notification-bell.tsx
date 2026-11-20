"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Bell } from "lucide-react";
import type { UnreadCount } from "@vshop/contracts";
import { bff } from "@/lib/client-api";

/** Bell with the unread count; refreshed every 30 seconds while the tab is visible. */
export function NotificationBell({ initial }: { initial: number }) {
  const [unread, setUnread] = useState(initial);
  // The server renders the header again after router.refresh(); follow its newer count.
  useEffect(() => setUnread(initial), [initial]);
  useEffect(() => {
    async function refresh() {
      if (document.visibilityState !== "visible") return;
      const result = await bff<UnreadCount>("GET", "me/notifications/unread-count");
      if (result.ok) setUnread(result.data.unread);
    }
    const timer = setInterval(refresh, 30_000);
    return () => clearInterval(timer);
  }, []);
  return (
    <Link
      href="/notifications"
      className="header-bell"
      aria-label={unread ? `Thông báo, ${unread} chưa đọc` : "Thông báo"}
    >
      <Bell size={20} aria-hidden="true" />
      {unread > 0 && <span className="header-bell-count">{unread > 99 ? "99+" : unread}</span>}
    </Link>
  );
}
