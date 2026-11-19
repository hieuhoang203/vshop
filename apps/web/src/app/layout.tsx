import type { Metadata } from "next";
import { Header } from "@/components/header";
import { Footer } from "@/components/footer";
import { VshopProviders } from "@/components/providers";
import localFont from "next/font/local";
import "@radix-ui/themes/styles.css";
import "./globals.css";
const manrope = localFont({
  src: "../fonts/manrope.ttf",
  variable: "--font-manrope",
  display: "swap",
  weight: "200 800",
});
export const metadata: Metadata = {
  title: { default: "VShop — Chọn điều bạn thích", template: "%s | VShop" },
  description: "Khám phá góc làm việc, nhà cửa và phụ kiện mỗi ngày trên VShop.",
  robots: { index: false, follow: false },
};
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body className={manrope.variable}>
        <VshopProviders>
          <a href="#main" className="skip-link">
            Đến nội dung chính
          </a>
          <Header />
          <main id="main">{children}</main>
          <Footer />
        </VshopProviders>
      </body>
    </html>
  );
}
