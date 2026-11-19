import { Store, FileCheck2, CircleCheck, Clock3 } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getMyShops } from "@/lib/api";
import { LoginPrompt } from "@/components/portal";
import { ShopForm } from "@/components/shop-form";
export const metadata = { title: "Kênh người bán" };
export default async function Seller() {
  const session = await getSession();
  if (!session) return <LoginPrompt target="seller" />;
  if (!session.roles.some((r) => r === "BUYER" || r === "SELLER"))
    return (
      <div className="container empty-state page-space">
        <h1>Tài khoản chưa có quyền đăng ký gian hàng</h1>
      </div>
    );
  const shops = await getMyShops();
  const shop = shops[0];
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">KÊNH NGƯỜI BÁN</span>
        <h1>Gian hàng của bạn</h1>
        <p>Mỗi hành trình đều bắt đầu từ một gian hàng.</p>
      </div>
      <div className="portal-grid">
        <div>
          {shop ? (
            <>
              <section className="panel shop-summary">
                <div className="panel-title">
                  <span className="portal-symbol small-symbol">
                    <Store size={24} />
                  </span>
                  <div>
                    <h2>{shop.name}</h2>
                    <span className={`status status-${shop.status.toLowerCase()}`}>
                      {shop.status === "APPROVED"
                        ? "Đã duyệt"
                        : shop.status === "REJECTED"
                          ? "Cần bổ sung"
                          : "Đang xét duyệt"}
                    </span>
                  </div>
                </div>
                <p>{shop.description}</p>
                {shop.status === "SUBMITTED" && (
                  <div className="notice">
                    <Clock3 size={21} />
                    <p>Hồ sơ đã được gửi. Kết quả xét duyệt sẽ hiển thị tại đây.</p>
                  </div>
                )}
                {shop.status === "APPROVED" && (
                  <div className="notice success">
                    <CircleCheck size={22} />
                    <p>
                      Gian hàng đã được chấp thuận. Chức năng đăng sản phẩm và xử lý đơn sẽ được mở
                      ở giai đoạn tiếp theo.
                    </p>
                  </div>
                )}
                {shop.status === "REJECTED" && (
                  <div className="notice">
                    <FileCheck2 size={22} />
                    <div>
                      <strong>Nội dung cần bổ sung</strong>
                      <p>{shop.rejectionReason}</p>
                    </div>
                  </div>
                )}
              </section>
              {shop.status === "REJECTED" && <ShopForm shop={shop} />}
            </>
          ) : (
            <ShopForm />
          )}
        </div>
        <aside className="panel guidance">
          <span className="eyebrow small">CÁC BƯỚC BẮT ĐẦU</span>
          <h2>Sẵn sàng cho gian hàng mới</h2>
          <div className="guidance-step">
            <FileCheck2 />
            <div>
              <strong>Giới thiệu gian hàng</strong>
              <p>Tên dễ nhận biết và thông tin sản phẩm rõ ràng.</p>
            </div>
          </div>
          <div className="guidance-step">
            <Clock3 />
            <div>
              <strong>Theo dõi xét duyệt</strong>
              <p>Bổ sung thông tin khi được yêu cầu.</p>
            </div>
          </div>
          <div className="guidance-step">
            <Store />
            <div>
              <strong>Cùng chuẩn bị mở bán</strong>
              <p>VShop hiện đang trong giai đoạn thử nghiệm.</p>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
