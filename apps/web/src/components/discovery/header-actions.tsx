"use client";

import {
  Button,
  Dialog,
  IconButton,
  Tooltip,
  Badge,
  TextField,
  DropdownMenu,
} from "@radix-ui/themes";
import {
  Heart,
  Search,
  Command,
  X,
  UserRound,
  Store,
  ShieldCheck,
  Sparkles,
  ShoppingCart,
  MapPin,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useDiscovery } from "@/components/providers";
import { formatMoney } from "@/lib/format";

export function HeaderActions({
  name,
  roles,
  cartCount = null,
}: {
  name?: string;
  roles: string[];
  /** null hides the cart link (signed out, or an account that cannot shop). */
  cartCount?: number | null;
}) {
  const { saved, toggleSaved } = useDiscovery();
  const [searchOpen, setSearchOpen] = useState(false);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setSearchOpen((open) => !open);
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);
  return (
    <div className="header-actions">
      <Dialog.Root open={searchOpen} onOpenChange={setSearchOpen}>
        <Tooltip content="Tìm nhanh · Ctrl / ⌘ K">
          <Dialog.Trigger>
            <IconButton variant="ghost" color="gray" size="3" aria-label="Mở tìm kiếm nhanh">
              <Search size={21} />
            </IconButton>
          </Dialog.Trigger>
        </Tooltip>
        <Dialog.Content maxWidth="550px" className="discovery-dialog search-dialog">
          <div className="dialog-top">
            <Badge color="iris" variant="soft">
              <Sparkles size={13} /> Tìm một điều mới
            </Badge>
            <Dialog.Close>
              <IconButton variant="ghost" color="gray" aria-label="Đóng tìm kiếm">
                <X size={20} />
              </IconButton>
            </Dialog.Close>
          </div>
          <Dialog.Title>Tìm đúng món. Đúng gu.</Dialog.Title>
          <Dialog.Description>
            Tìm theo tên sản phẩm hoặc chọn một gợi ý bên dưới.
          </Dialog.Description>
          <form action="/" className="quick-search-form">
            <TextField.Root
              name="q"
              placeholder="Ví dụ: đèn bàn, Mori, tai nghe…"
              aria-label="Từ khóa tìm nhanh"
              size="3"
              maxLength={100}
            >
              <TextField.Slot>
                <Search size={18} />
              </TextField.Slot>
            </TextField.Root>
            <Button type="submit" size="3">
              Tìm kiếm
            </Button>
          </form>
          <div className="search-suggestions">
            {["Luma", "Mori", "Hush", "tote"].map((q) => (
              <Dialog.Close key={q}>
                <Button variant="soft" color="gray" asChild>
                  <Link href={`/?q=${q}#san-pham`}>{q}</Link>
                </Button>
              </Dialog.Close>
            ))}
          </div>
          <p className="shortcut-hint">
            <Command size={13} /> Ctrl / ⌘ K để mở · Esc để đóng
          </p>
        </Dialog.Content>
      </Dialog.Root>
      {cartCount !== null && (
        <Link
          href="/cart"
          className="saved-trigger cart-link"
          aria-label={`Giỏ hàng${cartCount ? ` (${cartCount} sản phẩm)` : ""}`}
        >
          <ShoppingCart size={21} />
          <span className="saved-count">{cartCount}</span>
        </Link>
      )}
      <Dialog.Root>
        <Tooltip content="Món đã lưu trên thiết bị này">
          <Dialog.Trigger>
            <button
              className="saved-trigger"
              aria-label={`Món đã lưu${saved.length ? ` (${saved.length})` : ""}`}
            >
              <Heart size={21} />
              <span className="saved-count">{saved.length}</span>
            </button>
          </Dialog.Trigger>
        </Tooltip>
        <Dialog.Content maxWidth="600px" className="discovery-dialog">
          <div className="dialog-top">
            <Badge color="pink">
              <Heart size={13} /> GÓC CỦA BẠN
            </Badge>
            <Dialog.Close>
              <IconButton variant="ghost" color="gray" aria-label="Đóng danh sách lưu">
                <X size={20} />
              </IconButton>
            </Dialog.Close>
          </div>
          <Dialog.Title>Món hợp gu, giữ ở đây.</Dialog.Title>
          <Dialog.Description>
            Danh sách được lưu trên thiết bị này. Mở chi tiết để kiểm tra giá và trạng thái hiện
            tại.
          </Dialog.Description>
          {saved.length ? (
            <div className="saved-list">
              {saved.map((product) => (
                <div className="saved-item" key={product.id}>
                  <img src={`/products/${product.imageKey}.svg`} alt="" width={72} height={72} />
                  <div>
                    <Dialog.Close>
                      <Link href={`/products/${product.slug}`} className="saved-name">
                        {product.name}
                      </Link>
                    </Dialog.Close>
                    <p>
                      {formatMoney(product.price)} <span>· giá khi lưu</span>
                    </p>
                  </div>
                  <IconButton
                    variant="soft"
                    color="pink"
                    aria-label={`Bỏ lưu ${product.name}`}
                    onClick={() => toggleSaved(product)}
                  >
                    <Heart size={17} fill="currentColor" />
                  </IconButton>
                </div>
              ))}
            </div>
          ) : (
            <div className="saved-empty">
              <span className="empty-heart">
                <Heart size={32} />
              </span>
              <h3>Gu của bạn đang chờ được khám phá.</h3>
              <p>Chạm vào trái tim ở món bạn thích để lưu lại.</p>
              <Dialog.Close>
                <Button size="3">Đi tìm món hợp gu</Button>
              </Dialog.Close>
            </div>
          )}
        </Dialog.Content>
      </Dialog.Root>
      {name ? (
        <DropdownMenu.Root>
          <DropdownMenu.Trigger>
            <Button
              variant="soft"
              color="gray"
              className="account-menu-trigger"
              aria-label={`Tài khoản ${name}`}
            >
              <UserRound size={18} />
              <span className="account-name">{name}</span>
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            <DropdownMenu.Label>{name}</DropdownMenu.Label>
            <DropdownMenu.Item asChild>
              <Link href="/seller">
                <Store size={16} /> Gian hàng của tôi
              </Link>
            </DropdownMenu.Item>
            {roles.some((role) => role === "BUYER" || role === "SELLER") && (
              <DropdownMenu.Item asChild>
                <Link href="/account/addresses">
                  <MapPin size={16} /> Sổ địa chỉ
                </Link>
              </DropdownMenu.Item>
            )}
            {roles.includes("ADMIN") && (
              <DropdownMenu.Item asChild>
                <Link href="/admin">
                  <ShieldCheck size={16} /> Quản trị hệ thống
                </Link>
              </DropdownMenu.Item>
            )}
            <DropdownMenu.Separator />
            <form action="/api/auth/logout" method="post">
              <DropdownMenu.Item asChild color="red">
                <button type="submit" className="dropdown-submit">
                  Đăng xuất
                </button>
              </DropdownMenu.Item>
            </form>
          </DropdownMenu.Content>
        </DropdownMenu.Root>
      ) : (
        <Button asChild color="iris" size="3" className="login-button">
          <Link href="/api/auth/login">
            <UserRound size={17} />
            <span>Đăng nhập</span>
          </Link>
        </Button>
      )}
    </div>
  );
}
