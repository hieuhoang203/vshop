import Link from "next/link";
import { Search, Store, Sparkles, ShoppingBag } from "lucide-react";
import { TextField, Button } from "@radix-ui/themes";
import { getSession } from "@/lib/auth";
import { HeaderActions } from "@/components/discovery/header-actions";

export async function Header() {
  const session = await getSession();
  return (
    <>
      <div className="topbar">
        <div className="container topbar-inner">
          <span>
            <Sparkles size={13} /> Mỗi ngày một món hay. Mỗi người một gu riêng.
          </span>
          <div>
            <Link href="/seller">
              <Store size={13} /> Kênh người bán
            </Link>
            <Link href="/admin">Quản trị</Link>
          </div>
        </div>
      </div>
      <header className="header">
        <div className="container header-inner">
          <Link href="/" className="brand" aria-label="VShop trang chủ">
            <span className="brand-icon">
              <ShoppingBag size={25} />
            </span>
            vshop
            <span className="brand-star" aria-hidden="true">
              ✳
            </span>
          </Link>
          <form action="/" className="search" role="search">
            <TextField.Root
              name="q"
              placeholder="Tìm món hợp gu hôm nay…"
              aria-label="Tìm sản phẩm"
              maxLength={100}
              size="3"
              className="header-search-input"
            >
              <TextField.Slot>
                <Search size={18} />
              </TextField.Slot>
            </TextField.Root>
            <Button type="submit" size="3">
              Tìm kiếm
            </Button>
          </form>
          <HeaderActions name={session?.name} roles={session?.roles ?? []} />
        </div>
        <div className="container nav-row">
          <nav className="nav" aria-label="Danh mục chính">
            <Link href="/" className="nav-explore">
              <Sparkles size={15} /> Khám phá
            </Link>
            <Link href="/?category=goc-lam-viec#san-pham">Góc làm việc</Link>
            <Link href="/?category=nha-cua#san-pham">Nhà cửa & đời sống</Link>
            <Link href="/?category=phu-kien#san-pham">Phụ kiện mỗi ngày</Link>
          </nav>
          <span className="nav-note">GOOD FINDS. GOOD VIBES.</span>
        </div>
      </header>
    </>
  );
}
