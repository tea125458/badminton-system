# 🏸 羽球館預約管理系統 — 後端

> 一站式羽球場地預約與零打揪團平台的後端服務，提供 RESTful API 供前端串接。

## 📌 專案簡介

本專案為資展國際 EEIT 跨域 Java 全端工程師養成班的結訓專題，由 5 人團隊以 Scrum 敏捷開發方式協作完成。

**我負責的模組：臨打揪團 (Pickup Game)**  
獨立完成從資料庫設計、Entity/Repository/Service/Controller 全套開發，包含完整的 CRUD 與商業邏輯防呆機制。

## 🛠️ 技術棧

| 分類 | 技術 |
|------|------|
| 語言 | Java 17 |
| 框架 | Spring Boot 3.4.5 |
| ORM | Spring Data JPA + Hibernate |
| 資料庫 | Microsoft SQL Server |
| 安全性 | Spring Security + JWT + Google OAuth 2.0 |
| 金流 | LINE Pay API |
| 工具 | Maven、Lombok、Postman |

## 📁 專案結構 (Package by Feature)

```
com.badminton/
├── BadmintonApplication.java
├── config/          ... WebConfig, CORS, Security 設定
├── common/          ... Interceptor, ExceptionHandler
├── member/          ... 會員管理
├── admin/           ... 管理員功能
├── venue/           ... 場館管理
├── court/           ... 場地管理
├── booking/         ... 場地預約
├── timeslot/        ... 時段管理
├── product/         ... 商品管理
├── order/           ... 訂單管理
├── announcement/    ... 公告管理
└── pickupgame/      ... ⭐ 臨打揪團 (我負責的模組)
```

## ⭐ 我負責的模組：臨打揪團 (pickupgame)

### 核心功能

| 功能 | 說明 |
|------|------|
| 發起揪團 | 主揪可設定日期、起訖時間、人數上限、程度門檻與性別限制 |
| 瀏覽與篩選 | 提供多條件查詢 API (日期、程度、狀態) |
| 報名 / 退出 | 包含名額檢查、重複報名防呆、時間衝突驗證 |
| 主揪管理 | 查看報名名單、管理揪團資訊 |
| 後台管理 | 管理員可審核、編輯、刪除所有揪團 |

### API 端點設計

```
GET    /api/pickup-games              # 查詢所有揪團
GET    /api/pickup-games/{id}         # 查詢單筆揪團
POST   /api/pickup-games              # 新增揪團
PUT    /api/pickup-games/{id}         # 修改揪團
DELETE /api/pickup-games/{id}         # 刪除揪團

GET    /api/pickup-games/{gameId}/signups   # 查詢該揪團的報名名單
POST   /api/pickup-games/{gameId}/signups   # 報名揪團
DELETE /api/pickup-games/{gameId}/signups   # 取消報名
```

### 防呆機制 (Business Logic)

- ✅ 時間衝突檢查：同一場地、同一時段不可重複開團
- ✅ 名額控管：報名人數達上限時自動拒絕
- ✅ 重複報名攔截：同一會員不可重複報名同一場次
- ✅ 性別與程度門檻：依主揪設定的條件進行前端 + 後端雙重驗證

## 🚀 Getting Started

1. Clone this repository
2. **設定資料庫連線：**
   - 複製 `application-local.yml.example` 為 `application-local.yml`
   - 填入你自己的 SQL Server 密碼
   - **請勿直接修改 `application.yml`**
3. 啟動專案：
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```
4. 開啟瀏覽器：http://localhost:8080

## 🔗 相關連結

- **前端專案 (Vue 3)**：[badminton-vue](https://github.com/tea125458/badminton-vue)
- **Demo 影片**：[YouTube](https://www.youtube.com/live/t314dZDJq7s)

## 👥 團隊

- 5 人團隊，以 Scrum 方式協作開發
- 每位成員獨立負責一至兩個功能模組的全端開發