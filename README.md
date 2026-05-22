# Bookstore AI Service

`bookstore-ai-service` la microservice AI cho he thong Bookstore, duoc tach tu monolithic theo kien truc Microservice.

## 1) Chuc nang

- Chatbot (tuong thich monolith):
  - `POST /api/v1/chatbot/chat` — body `ChatbotRequest`, tra `ApiResponse<ChatbotResponse>`
  - `POST /api/v1/chatbot/ai` — body chuoi JSON text, tra chuoi phan hoi
  - `POST /api/v1/chat/ai` — alias legacy, body chuoi JSON text, tra chuoi phan hoi
- Chat voi AI:
  - `POST /api/v1/ai/chat`
- Tim kiem thong minh (goi sang `book-service`):
  - `POST /api/v1/ai/search`
- Tao tom tat bao cao don hang (goi `order-service` + Gemini):
  - `GET /api/v1/ai/report`
- AI Agent voi Tool-Calling (Gemini Function Calling):
  - `POST /api/v1/agent/chat` — Agent tu chon tool phu hop, goi API, tong hop phan hoi
  - `GET  /api/v1/agent/tools` — Liet ke metadata tat ca tool dang bat

### Cac Tool da hien thuc (CS-07 → CS-15)

| Tool | Use case | Mo ta |
|------|----------|-------|
| `searchBooksTool` | CS-10 (basic) | Tim sach theo keyword qua `/api/v1/books/search` |
| `semanticSearchTool` | CS-07 | Hybrid keyword + Gemini-expanded concepts (san sang nang cap len vector DB) |
| `recommendationTool` | CS-08 | Goi y ca nhan hoa dua tren suggested + best-selling |
| `compareBooksTool` | CS-09 | So sanh nhieu cuon (batch-details hoac search theo title) |
| `stockCheckTool` | CS-10 + ton kho | Loc theo gia/rating/category + kiem tra con hang |
| `reviewSummaryTool` | CS-11 | Lay review tu review-service de Gemini tom tat sentiment |
| `imageScannerTool` | CS-13 | Multimodal: doc anh bia → tim sach trong he thong |
| `promotionTool` | CS-14 | Lay promotion active + rule engine theo `orderValue` |
| `guardrailTool` | CS-15 | Phat hien & xu ly cau hoi ngoai pham vi |
| `orderLookupTool` | Tien ich | Tra cuu don hang theo orderId hoac userId |

### Luong xu ly cua Agent

1. **Guardrail pre-flight**: tu choi som neu cau hoi ngoai pham vi
2. **Build prompt** + tool schema → goi Gemini Function Calling
3. **Vong lap** (toi da `ai.agent.max-tool-iterations` lan):
   - Gemini chon `functionCall` → Agent thuc thi tool → day `functionResponse` lai
   - Hoac Gemini tra `text` → ket thuc
4. **Tra ve** cau tra loi cuoi + danh sach tool da goi (de FE render card)

Tat ca endpoint tra theo format:

```json
{
  "code": 200,
  "message": "Success",
  "result": {}
}
```

## 2) Cong nghe

- Java 21
- Spring Boot 4.0.5
- Spring Cloud OpenFeign 2025.1.1
- Google Gemini API
- Docker / Docker Compose

## 3) Cau hinh moi truong

Can set cac bien sau:

- `GEMINI_API_KEY` (bat buoc neu muon chat/report AI that)
- `GEMINI_MODEL` (mac dinh `gemini-2.5-flash`, ho tro function calling + multimodal)
- `BOOK_SERVICE_URL` (mac dinh dev: `http://localhost:8082`)
- `ORDER_SERVICE_URL` (mac dinh dev: `http://localhost:8084`)
- `PROMOTION_SERVICE_URL` (mac dinh dev: `http://localhost:8086`) — optional, tool tu fallback
- `REVIEW_SERVICE_URL` (mac dinh dev: `http://localhost:8087`) — optional, tool tu fallback

Cau hinh Agent (trong `application-*.yml`):
- `ai.agent.max-tool-iterations` — so vong tool-call toi da (mac dinh 4)
- `ai.agent.enabled-tools` — danh sach ten tool duoc bat (mac dinh: bat het 10 tools)

## 4) Chay service

### Cach A: chay local bang Maven

```bash
./mvnw spring-boot:run
```

Service chay tai `http://localhost:8080` (host port trong compose la `8090`).

### Cach B: chay bang Docker Compose

```bash
docker compose up -d
```

Port mapping:
- host: `8090`
- container: `8080`

## 5) Postman

Import thư mục `postman/`:

- `bookstore-ai-service.postman_collection.json` — toàn bộ API
- `bookstore-ai-service.local.postman_environment.json` — Docker (`http://localhost:8090`)
- `bookstore-ai-service.maven.postman_environment.json` — Maven (`http://localhost:8080`)

## 6) Test API

### 5.1 Chat AI

```bash
curl -X POST http://localhost:8090/api/v1/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Gợi ý cho tôi 3 cuốn sách lập trình dễ học",
    "sessionId": "session-001"
  }'
```

### 5.2 Tim kiem thong minh

```bash
curl -X POST http://localhost:8090/api/v1/ai/search \
  -H "Content-Type: application/json" \
  -d '{
    "keyword": "clean code",
    "page": 0,
    "size": 5
  }'
```

### 5.3 Bao cao AI

```bash
curl http://localhost:8090/api/v1/ai/report
```

### 5.4 AI Agent (tool-calling)

```bash
curl -X POST http://localhost:8090/api/v1/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Tim cho toi sach kinh te duoi 200k va con hang",
    "sessionId": "s1",
    "userId": "user-uuid"
  }'
```

Co the gui kem anh bia sach (CS-13):

```bash
curl -X POST http://localhost:8090/api/v1/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Cuon nay ben minh co ban khong, gia bao nhieu?",
    "attachments": [{
      "type": "image/jpeg",
      "name": "cover.jpg",
      "url": "https://example.com/path/to/cover.jpg"
    }]
  }'
```

Liet ke tool dang co:

```bash
curl http://localhost:8090/api/v1/agent/tools
```

## 7) Luu y

- Neu chua cau hinh `GEMINI_API_KEY`, endpoint `/api/v1/ai/chat` van tra ve message thong bao cau hinh key.
- `ai-service` khong truy cap DB service khac; giao tiep qua API (`book-service`, `order-service`) dung Feign.
- Port convention theo kien truc:
  - container: `8080`
  - host: `8090`
