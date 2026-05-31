# Danh sách API gọi ra ngoài cho AI Service

## Outbound APIs (chi tiết)

### 1) Book Service

#### API: `GET {BOOK_SERVICE_URL}/api/v1/books?page=&size=&keyword=`

- Mục đích: phục vụ AI search (`POST /api/v1/ai/search`) để lấy danh sách sách theo từ khóa.
- Nơi gọi: `BookServiceClient.searchBooks(page, size, keyword)`.

**Request**

- Method: `GET`
- Query params:
  - `page` (required): integer, >= 0
  - `size` (required): integer, > 0
  - `keyword` (required): string

Ví dụ:

```http
GET /api/v1/books?page=0&size=10&keyword=harry
```

**Response**

- `200 OK`: object (AI service đang map linh hoạt bằng `Map<String, Object>`), thường gồm:
  - `code`: integer (nếu có wrapper)
  - `message`: string
  - `data`: object phân trang (ví dụ `content`, `currentPage`, `totalPages`, `totalElements`)
- `400 Bad Request`: query param không hợp lệ
- `500 Internal Server Error`: lỗi nội bộ

---

### 2) Order Service

#### API: `GET {ORDER_SERVICE_URL}/api/v1/orders/admin/stats`

- Mục đích: lấy thống kê đơn hàng để tạo AI report (`GET /api/v1/ai/report`).
- Nơi gọi: `OrderServiceClient.getOrderStats()`.

**Request**

- Method: `GET`
- Auth: tùy policy admin của order-service (nếu bật).

Ví dụ:

```http
GET /api/v1/orders/admin/stats
```

**Response**

- `200 OK`: object (AI service map bằng `Map<String, Object>`), tối thiểu:
  - `totalOrders`: integer
  - `totalRevenue`: number
  - `pendingOrders`: integer (optional)
  - `completedOrders`: integer (optional)
  - `cancelledOrders`: integer (optional)
  - `currency`: string (optional, ví dụ `VND`)
- `401/403`: thiếu quyền
- `500 Internal Server Error`

## Ghi chú

- Giữ ổn định field-name response để tránh vỡ mapping runtime trong `AiServiceImpl`.
