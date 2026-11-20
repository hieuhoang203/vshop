"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { Notification } from "@vshop/contracts";
import { Button } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";
import { dateTime } from "@/components/orders/order-card";

/** Notifications of the signed-in person. Opening one marks it read and goes to what it is about. */
export function NotificationList({ items }: { items: Notification[] }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  async function readAll() {
    if (busy) return;
    setBusy(true);
    await bff("POST", "me/notifications/read-all", {});
    setBusy(false);
    router.refresh();
  }
  const unread = items.filter((n) => !n.read).length;
  return (
    <>
      <div className="list-toolbar">
        <span>{unread} chưa đọc</span>
        <Button variant="soft" disabled={busy || unread === 0} onClick={readAll}>
          Đánh dấu đã đọc tất cả
        </Button>
      </div>
      <ul className="cart-lines">
        {items.map((n) => (
          <li
            key={n.id}
            className={n.read ? "panel cart-line" : "panel cart-line unread"}
            data-testid="notification"
          >
            <div className="cart-line-info">
              <strong>{n.title}</strong>
              <span className="row-sub">{n.body}</span>
              <span className="row-sub">{dateTime.format(new Date(n.createdAt))}</span>
            </div>
            <div className="cart-line-total">
              {!n.read && <span className="status status-submitted">Mới</span>}
              {n.link ? (
                <Link
                  href={n.link}
                  onClick={() => void bff("POST", `me/notifications/${n.id}/read`, {})}
                >
                  Xem
                </Link>
              ) : null}
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
