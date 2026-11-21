import Link from "next/link";

const LINKS = {
  seller: [
    { href: "/seller", label: "Gian hàng" },
    { href: "/seller/products", label: "Sản phẩm" },
    { href: "/seller/orders", label: "Đơn hàng" },
    { href: "/seller/returns", label: "Trả hàng" },
  ],
  admin: [
    { href: "/admin", label: "Hồ sơ gian hàng" },
    { href: "/admin/products", label: "Duyệt sản phẩm" },
    { href: "/admin/policies", label: "Chính sách" },
    { href: "/admin/orders", label: "Đơn quá hạn" },
    { href: "/admin/refunds", label: "Hoàn tiền" },
    { href: "/admin/cod", label: "Thu hộ COD" },
    { href: "/admin/returns", label: "Trả hàng" },
  ],
} as const;

export function PortalNav({ portal, current }: { portal: "seller" | "admin"; current: string }) {
  return (
    <nav className="portal-nav" aria-label={portal === "seller" ? "Kênh người bán" : "Quản trị"}>
      {LINKS[portal].map((link) => (
        <Link
          key={link.href}
          href={link.href}
          aria-current={link.href === current ? "page" : undefined}
        >
          {link.label}
        </Link>
      ))}
    </nav>
  );
}
