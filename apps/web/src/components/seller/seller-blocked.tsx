import Link from "next/link";
import { Store } from "lucide-react";
import { LoginPrompt } from "@/components/portal";
import type { SellerContext } from "@/lib/seller-context";

/** Renders the reason a seller page cannot show product tools. */
export function SellerBlocked({ context }: { context: Exclude<SellerContext, { kind: "ok" }> }) {
  if (context.kind === "login") return <LoginPrompt target="seller" />;
  return (
    <div className="container empty-state page-space">
      <Store size={40} />
      <h1>
        {context.kind === "forbidden"
          ? "Tài khoản chưa có quyền bán hàng"
          : context.kind === "no-shop"
            ? "Bạn chưa có gian hàng"
            : "Gian hàng chưa được duyệt"}
      </h1>
      <p>
        {context.kind === "forbidden"
          ? "Vui lòng đăng nhập bằng tài khoản người mua hoặc người bán."
          : context.kind === "no-shop"
            ? "Hãy gửi hồ sơ gian hàng trước khi thêm sản phẩm."
            : "Bạn có thể quản lý sản phẩm sau khi hồ sơ gian hàng được chấp thuận."}
      </p>
      {context.kind !== "forbidden" && (
        <Link href="/seller" className="button button-dark">
          Về trang gian hàng
        </Link>
      )}
    </div>
  );
}
