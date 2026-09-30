# SQL và cơ sở dữ liệu quan hệ — ghi chú ôn tập

Tổng hợp kiến thức SQL và PostgreSQL thường dùng khi làm backend.

## Transaction và ACID

Transaction là một nhóm thao tác được thực hiện như một đơn vị: hoặc tất cả thành công, hoặc không có gì thay đổi. Bốn tính chất ACID:

- **Atomicity (nguyên tử):** tất cả hoặc không gì cả. Lỗi giữa chừng → `ROLLBACK` toàn bộ.
- **Consistency (nhất quán):** transaction đưa database từ trạng thái hợp lệ này sang trạng thái hợp lệ khác, không vi phạm ràng buộc (khóa ngoại, `CHECK`, `UNIQUE`).
- **Isolation (cô lập):** các transaction chạy đồng thời không thấy trạng thái dở dang của nhau (mức độ tùy isolation level).
- **Durability (bền vững):** đã `COMMIT` thì dữ liệu không mất kể cả khi server sập — nhờ ghi write-ahead log (WAL) xuống đĩa trước.

```sql
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;
```

## Các mức cô lập (isolation level)

Ba hiện tượng đọc bất thường khi nhiều transaction chạy song song:

- **Dirty read:** đọc dữ liệu mà transaction khác chưa commit.
- **Non-repeatable read:** đọc cùng một dòng hai lần trong một transaction nhưng nhận giá trị khác nhau, vì transaction khác đã sửa và commit ở giữa.
- **Phantom read:** chạy lại cùng một câu truy vấn theo điều kiện nhưng số dòng thay đổi, vì transaction khác đã thêm/xóa dòng thỏa điều kiện.

| Isolation level | Dirty read | Non-repeatable read | Phantom read |
|---|---|---|---|
| READ UNCOMMITTED | có thể | có thể | có thể |
| READ COMMITTED | không | có thể | có thể |
| REPEATABLE READ | không | không | có thể (theo chuẩn SQL) |
| SERIALIZABLE | không | không | không |

**PostgreSQL mặc định dùng READ COMMITTED.** PostgreSQL không bao giờ cho dirty read (READ UNCOMMITTED hoạt động như READ COMMITTED), và REPEATABLE READ của PostgreSQL dùng snapshot nên cũng chặn luôn phantom read. MySQL InnoDB mặc định REPEATABLE READ. Mức cô lập càng cao càng an toàn nhưng càng dễ gặp lỗi serialization và phải retry.

## Index B-tree

Index là cấu trúc dữ liệu phụ giúp tìm dòng nhanh mà không phải quét toàn bộ bảng (sequential scan). Loại mặc định là **B-tree**: cây cân bằng, các khóa được sắp xếp, hỗ trợ tốt `=`, `<`, `>`, `BETWEEN`, `ORDER BY` và `LIKE 'abc%'` (tiền tố).

**Index nhiều cột (composite index)** tuân theo quy tắc **leftmost prefix**: index trên `(last_name, first_name)` dùng được cho điều kiện `last_name = ?` hoặc `last_name = ? AND first_name = ?`, nhưng **không** dùng hiệu quả cho điều kiện chỉ có `first_name = ?`. Đặt cột có điều kiện bằng (`=`) lên trước, cột điều kiện khoảng (range) ra sau.

Chi phí của index: tốn dung lượng và làm chậm `INSERT`/`UPDATE`/`DELETE` vì phải cập nhật index. Không đánh index tràn lan; đánh index theo các truy vấn thực sự chạy nhiều.

Các loại index khác trong PostgreSQL: **GIN** cho full-text search và JSONB, **GiST** cho dữ liệu hình học, **HNSW/IVFFlat** (extension pgvector) cho tìm kiếm vector gần đúng.

## Khi nào index không được sử dụng

Có index nhưng query planner vẫn chọn quét toàn bảng trong các trường hợp:

- Áp dụng **hàm lên cột** trong điều kiện: `WHERE lower(email) = 'a@b.com'` không dùng index trên `email`. Giải pháp: tạo expression index `CREATE INDEX ON users (lower(email))`.
- `LIKE` bắt đầu bằng ký tự đại diện: `WHERE name LIKE '%an'` không dùng được B-tree. Cần trigram index (`pg_trgm`) hoặc full-text search.
- **Ép kiểu ngầm** giữa cột và tham số khác kiểu.
- Điều kiện có **độ chọn lọc thấp** (ví dụ cột `status` chỉ có 2 giá trị và điều kiện trả về phần lớn bảng): quét tuần tự rẻ hơn đọc index rồi nhảy qua lại.
- Dùng `OR` giữa các cột khác nhau hoặc điều kiện phủ định (`<>`, `NOT IN`).
- Thống kê bảng lỗi thời — chạy `ANALYZE` để planner ước lượng đúng.

## Đọc kế hoạch thực thi với EXPLAIN

`EXPLAIN` cho biết planner định chạy câu truy vấn như thế nào; `EXPLAIN ANALYZE` **chạy thật** câu truy vấn và báo thời gian thực tế của từng bước.

```sql
EXPLAIN ANALYZE
SELECT * FROM orders WHERE customer_id = 42 ORDER BY created_at DESC LIMIT 10;
```

Những điểm cần xem:

- `Seq Scan` trên bảng lớn thường là dấu hiệu thiếu index; `Index Scan` / `Index Only Scan` là tốt.
- So sánh `rows` ước lượng với `actual rows`: lệch nhiều nghĩa là thống kê sai.
- Nút tốn thời gian nhất (`actual time`) là chỗ cần tối ưu.
- Kiểu join: `Nested Loop` tốt khi một bên ít dòng; `Hash Join` cho tập lớn không sắp xếp; `Merge Join` khi cả hai bên đã sắp xếp.

Cẩn thận: `EXPLAIN ANALYZE` với `UPDATE`/`DELETE` sẽ thực sự sửa dữ liệu — bọc trong `BEGIN; ... ROLLBACK;`.

## Các loại JOIN

- **INNER JOIN:** chỉ giữ các dòng khớp ở cả hai bảng.
- **LEFT JOIN:** giữ mọi dòng bảng trái; không khớp thì các cột bảng phải là `NULL`. Dùng để tìm dòng "không có": `LEFT JOIN orders o ON ... WHERE o.id IS NULL` → khách hàng chưa có đơn nào.
- **RIGHT JOIN:** ngược lại với LEFT JOIN, ít dùng.
- **FULL OUTER JOIN:** giữ mọi dòng của cả hai bảng.
- **CROSS JOIN:** tích Descartes, mọi cặp dòng.

Lỗi hay gặp: đặt điều kiện lọc bảng phải vào `WHERE` thay vì `ON` khi dùng LEFT JOIN khiến LEFT JOIN trở thành INNER JOIN, vì các dòng `NULL` bị loại.

## Chuẩn hóa dữ liệu (1NF, 2NF, 3NF)

Chuẩn hóa loại bỏ dư thừa dữ liệu và bất thường khi cập nhật.

- **1NF:** mỗi ô chứa một giá trị nguyên tử, không có nhóm lặp (không lưu "java,sql,react" trong một cột).
- **2NF:** đạt 1NF và mọi cột không khóa phụ thuộc vào **toàn bộ** khóa chính, không chỉ một phần của khóa ghép.
- **3NF:** đạt 2NF và không có **phụ thuộc bắc cầu** — cột không khóa không phụ thuộc vào cột không khóa khác (ví dụ lưu `city_name` trong bảng `users` trong khi đã có `city_id`).

Trong thực tế, đôi khi **phi chuẩn hóa** có chủ đích (lưu thêm cột tổng, cột sao chép) để tăng tốc đọc, chấp nhận phải giữ đồng bộ dữ liệu.

## Deadlock

Deadlock xảy ra khi hai transaction chờ nhau: T1 giữ khóa dòng A và chờ dòng B, trong khi T2 giữ khóa dòng B và chờ dòng A. PostgreSQL phát hiện vòng chờ (sau `deadlock_timeout`, mặc định 1 giây) và **hủy một transaction** với lỗi `deadlock detected`; transaction còn lại tiếp tục.

Cách phòng tránh:

- Luôn **khóa/cập nhật các dòng theo cùng một thứ tự** (ví dụ theo `id` tăng dần).
- Giữ transaction **ngắn**, không gọi API bên ngoài khi đang giữ khóa.
- Dùng mức cô lập và khóa phù hợp, tránh khóa thừa.
- Ứng dụng nên **retry** transaction bị hủy do deadlock hoặc serialization failure.

## GROUP BY, WHERE và HAVING

- `WHERE` lọc **từng dòng trước** khi gom nhóm; không dùng được hàm tổng hợp.
- `GROUP BY` gom các dòng có cùng giá trị thành nhóm.
- `HAVING` lọc **các nhóm sau** khi gom, dùng được hàm tổng hợp như `COUNT`, `SUM`.

```sql
SELECT customer_id, COUNT(*) AS total_orders
FROM orders
WHERE status = 'PAID'            -- lọc dòng
GROUP BY customer_id
HAVING COUNT(*) >= 5;            -- lọc nhóm
```

Thứ tự thực thi logic: `FROM` → `WHERE` → `GROUP BY` → `HAVING` → `SELECT` → `ORDER BY` → `LIMIT`. Vì vậy alias đặt trong `SELECT` không dùng được trong `WHERE`.

## Phân trang: OFFSET và keyset

`LIMIT 20 OFFSET 100000` buộc database đọc và bỏ qua 100.000 dòng đầu, nên trang càng sâu càng chậm; dữ liệu thêm/xóa giữa các lần tải cũng làm trang bị lặp hoặc mất dòng.

**Keyset pagination** (seek method) nhớ khóa của dòng cuối trang trước và lọc tiếp từ đó, tận dụng index nên tốc độ gần như không đổi ở mọi trang:

```sql
SELECT * FROM posts
WHERE (created_at, id) < (:last_created_at, :last_id)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

Nhược điểm của keyset: không nhảy thẳng tới trang bất kỳ được, phù hợp với cuộn vô hạn hoặc nút "trang sau".
