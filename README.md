# Bookstore AI Service

`bookstore-ai-service` la microservice AI cho he thong Bookstore, duoc tach tu monolithic theo kien truc Microservice.

## 1) Chuc nang

- Chat voi AI:
  - `POST /api/v1/ai/chat`
- Tim kiem thong minh (goi sang `book-service`):
  - `POST /api/v1/ai/search`
- Tao tom tat bao cao don hang (goi `order-service` + Gemini):
  - `GET /api/v1/ai/report`

Tat ca endpoint tra theo format:

```json
{
  "code": 200,
  "message": "Success",
  "data": {}
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
- `BOOK_SERVICE_URL` (mac dinh dev: `http://localhost:8082`)
- `ORDER_SERVICE_URL` (mac dinh dev: `http://localhost:8084`)

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

## 5) Test API

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

## 6) Luu y

- Neu chua cau hinh `GEMINI_API_KEY`, endpoint `/api/v1/ai/chat` van tra ve message thong bao cau hinh key.
- `ai-service` khong truy cap DB service khac; giao tiep qua API (`book-service`, `order-service`) dung Feign.
- Port convention theo kien truc:
  - container: `8080`
  - host: `8090`
