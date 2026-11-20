"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** Re-reads the server state while a payment is being verified. The URL proves nothing. */
export function AutoRefresh({ seconds = 5 }: { seconds?: number }) {
  const router = useRouter();
  useEffect(() => {
    const timer = setInterval(() => router.refresh(), seconds * 1000);
    return () => clearInterval(timer);
  }, [router, seconds]);
  return null;
}
