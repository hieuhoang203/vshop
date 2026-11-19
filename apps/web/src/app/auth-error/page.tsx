import Link from "next/link";
export default function AuthError() {
  return (
    <div className="container empty-state page-space">
      <h1>Chưa thể đăng nhập</h1>
      <p>Phiên xác thực đã hết hạn hoặc kết nối bị gián đoạn. Vui lòng bắt đầu lại.</p>
      <Link href="/api/auth/login" className="button button-dark">
        Đăng nhập lại
      </Link>
    </div>
  );
}
