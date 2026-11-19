-- LOCAL PROFILE ONLY. Synthetic read-only catalog, never production migration.
INSERT INTO merchant.shop(id,owner_subject,name,description,status) VALUES
('10000000-0000-0000-0000-000000000001','local-fixture-studio','VShop Studio · mẫu','Gian hàng mẫu dùng cho phát triển nội bộ VShop, không phục vụ giao dịch thật.','APPROVED');
INSERT INTO catalog.category(id,name,slug,sort_order) VALUES
('20000000-0000-0000-0000-000000000001','Góc làm việc','goc-lam-viec',1),
('20000000-0000-0000-0000-000000000002','Nhà cửa & đời sống','nha-cua',2),
('20000000-0000-0000-0000-000000000003','Phụ kiện mỗi ngày','phu-kien',3);
INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,price,stock,image_key,status) VALUES
('30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','den-ban-luma','Đèn bàn Luma','Đèn bàn dáng vòm, ánh sáng ấm và chân đế vững chắc. Một điểm nhấn nhẹ nhàng cho góc làm việc. Sản phẩm mẫu để kiểm thử VShop.',459000,24,'lamp','ACTIVE'),
('30000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000003','tui-tote-everyday','Túi tote Everyday','Túi vải canvas dày, ngăn rộng và quai đeo thoải mái. Đồng hành cùng những ngày bận rộn. Sản phẩm mẫu để kiểm thử VShop.',189000,40,'tote','ACTIVE'),
('30000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','coc-gom-mori','Cốc gốm Mori','Cốc gốm men mờ với tay cầm tròn, dung tích 350 ml. Dành cho cà phê sáng và trà chiều. Sản phẩm mẫu để kiểm thử VShop.',129000,32,'mug','ACTIVE'),
('30000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','tai-nghe-hush','Tai nghe Hush','Tai nghe chụp tai với đệm êm và thiết kế tối giản. Màu xanh rêu cho góc nghe nhạc của bạn. Sản phẩm mẫu để kiểm thử VShop.',890000,15,'headphones','ACTIVE'),
('30000000-0000-0000-0000-000000000005','10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','binh-giu-nhiet-flow','Bình giữ nhiệt Flow','Bình giữ nhiệt 500 ml, thân thép và nắp kín. Gọn nhẹ cho hành trình hằng ngày. Sản phẩm mẫu để kiểm thử VShop.',269000,28,'bottle','ACTIVE'),
('30000000-0000-0000-0000-000000000006','10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','so-tay-grid','Sổ tay Grid','Sổ tay bìa cứng, giấy kẻ ô mịn. Ghi lại ý tưởng và những điều bạn muốn thực hiện. Sản phẩm mẫu để kiểm thử VShop.',99000,50,'notebook','ACTIVE');
