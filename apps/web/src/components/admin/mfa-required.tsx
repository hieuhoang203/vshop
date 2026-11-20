import { ShieldAlert } from "lucide-react";

/** Shown to ADMIN accounts whose session does not prove a second factor. */
export function MfaRequired() {
  return (
    <div className="container empty-state page-space" role="alert">
      <ShieldAlert size={40} />
      <h1>Cần xác thực hai bước</h1>
      <p>
        Khu vực quản trị chỉ mở cho phiên đã xác thực bằng mã OTP. Nếu đây là lần đầu bạn thiết lập
        ứng dụng xác thực, hãy đăng xuất rồi đăng nhập lại và nhập mã OTP khi được hỏi.
      </p>
      <form action="/api/auth/logout" method="post">
        <button type="submit" className="button button-dark">
          Đăng xuất để đăng nhập lại
        </button>
      </form>
    </div>
  );
}
