"use client";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <div className="container empty-state page-space">
      <h1>Kết nối đang tạm gián đoạn</h1>
      <p>Vui lòng thử lại sau ít phút.</p>
      <button className="button button-dark" onClick={reset}>
        Thử lại
      </button>
    </div>
  );
}
