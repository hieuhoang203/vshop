import Link from "next/link";
export default function NotFound() {
  return (
    <div className="container empty-state page-space">
      <h1>Không tìm thấy trang</h1>
      <p>Sản phẩm hoặc nội dung bạn tìm không còn hiển thị.</p>
      <Link className="button button-dark" href="/">
        Về trang chủ
      </Link>
    </div>
  );
}
