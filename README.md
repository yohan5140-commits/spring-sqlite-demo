# demo

Spring Boot + SQLite로 만든 사용자(User) 관리 REST API 예제입니다.

## 기술 스택

- Java 17
- Spring Boot 4.1.2 (Web MVC, Validation)
- Spring Data JDBC
- SQLite (`org.xerial:sqlite-jdbc`)
- Gradle

## 시작하기

### 1. 데이터베이스 준비

별도로 준비할 것은 없습니다. 앱이 시작할 때 `src/main/resources/schema.sql`을 실행해서 `demo.db` 파일과 필요한 테이블·뷰(`users`, `users_kst`, `ledger_entries`, `budgets`)를 만듭니다.
모든 문장이 `IF NOT EXISTS`라서 이미 있는 DB에 여러 번 실행해도 기존 데이터는 그대로입니다.

> `demo.db`는 `.gitignore`에 들어 있어 저장소에 포함되지 않습니다. 접속 URL은 `jdbc:sqlite:demo.db`(상대경로)입니다. 앱은 **프로젝트 루트에서 실행**해야 이 파일을 찾습니다.
> 다른 위치에서 실행하면 빈 DB 파일이 새로 만들어질 수 있습니다.

### 2. 실행

```bash
./gradlew bootRun
```

실행하면 **http://localhost:8080/** 에서 시작 페이지가 열리고, 가계부(`/ledger`)와 사용자 대시보드(`/dashboard`)로 이동할 수 있습니다. 시작할 때 `users_kst` 뷰의 내용이 콘솔에 출력됩니다(`UserCheckRunner`, 연결 확인용).

### 3. 테스트

```bash
./gradlew test
```

테스트는 실제 `demo.db`를 사용하지 않고 인메모리 SQLite를 사용합니다. 설정은 `src/test/resources/application.properties`에 분리되어 있고, 테이블은 메인의 `schema.sql`로 만듭니다.

## API

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| GET | `/users` | 목록 조회 (페이징·정렬) | 200 |
| GET | `/users/{id}` | 단건 조회 | 200 / 404 |
| POST | `/users` | 생성 | 201 / 400 / 409 |
| PUT | `/users/{id}` | 수정 | 200 / 400 / 404 / 409 |
| DELETE | `/users/{id}` | 삭제 | 204 / 404 |

### 요청 본문 (POST, PUT)

```json
{ "name": "홍길동", "email": "gildong@example.com" }
```

| 필드 | 규칙 |
|---|---|
| `name` | 필수, 공백만 있으면 안 됨 |
| `email` | 필수, 이메일 형식, 중복 불가 |

### 목록 조회 파라미터

| 파라미터 | 설명 | 기본값 |
|---|---|---|
| `page` | 0부터 시작하는 페이지 번호 | `0` |
| `size` | 페이지 크기 (최대 `100`) | `10` |
| `sort` | `항목,방향` 형식. 항목은 `id`, `name`, `email`, `createdAt` | `id` 오름차순 |

- `sort`는 여러 번 지정할 수 있습니다. 예: `sort=name,asc&sort=createdAt,desc`
- 정렬 값이 같은 행은 `id` 오름차순으로 정렬되어, 페이지 사이에 중복이나 누락이 생기지 않습니다.
- 허용되지 않은 정렬 항목은 `400`입니다.

### 사용 예

```bash
# 목록 (2페이지째, 2건씩, 이름 내림차순)
curl 'localhost:8080/users?page=1&size=2&sort=name,desc'

# 생성
curl -X POST localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"홍길동","email":"gildong@example.com"}'

# 수정
curl -X PUT localhost:8080/users/1 \
  -H 'Content-Type: application/json' \
  -d '{"name":"홍길순","email":"gilsoon@example.com"}'

# 삭제
curl -X DELETE localhost:8080/users/1
```

### 응답 형식

사용자 객체:

```json
{ "id": 1, "name": "김철수", "email": "chulsoo@example.com", "createdAt": "2026-10-27 15:32:38" }
```

목록 응답:

```json
{
  "content": [ { "id": 1, "name": "김철수", "email": "chulsoo@example.com", "createdAt": "2026-10-27 15:32:38" } ],
  "page": { "size": 10, "number": 0, "totalElements": 5, "totalPages": 1 }
}
```

### 오류 응답

| 상태 | 상황 | 본문 |
|---|---|---|
| 400 | 검증 실패 | `{"email": "이메일 형식이 올바르지 않습니다."}` (필드별 메시지) |
| 400 | 허용되지 않은 정렬 항목 | 안내 메시지 문자열 |
| 409 | 이메일 중복 | `이미 존재하는 이메일이거나 필수 값이 누락되었습니다.` |

## 대시보드

앱을 실행한 뒤 브라우저에서 **http://localhost:8080/dashboard** 를 열면 사용자 현황을 볼 수 있습니다.

| 영역 | 내용 |
|---|---|
| 요약 | 전체 사용자 수, 오늘 가입, 선택 기간 내 가입, 마지막 가입 시각 |
| 일별 가입자 | 선택 기간의 일별 가입자 막대 차트 (가입자가 없는 날은 0). 막대에 마우스를 올리거나 키보드로 포커스하면 값이 표시되고, "표로 보기"로 전체 값을 볼 수 있음 |
| 이메일 도메인 | 선택 기간 가입자의 도메인별 인원 (상위 8개) |
| 최근 가입자 | 선택 기간의 최근 가입자 5명 |

- 상단의 **7일 / 14일 / 30일 / 90일** 버튼으로 기간을 바꿉니다 (기본 14일). 요약의 "전체 사용자"를 제외한 모든 영역이 선택한 기간에 맞춰 바뀝니다.
- 30초마다 자동으로 갱신되며, "새로고침" 버튼으로 바로 갱신할 수도 있습니다.
- 브라우저의 라이트/다크 설정을 따르고, 모바일 폭에서도 볼 수 있습니다.
- 외부 라이브러리 없이 `src/main/resources/static/dashboard.html` 한 파일로 구성되어 있습니다.

### 통계 API

대시보드가 사용하는 API입니다.

```bash
curl 'localhost:8080/dashboard/stats?days=14'
```

| 파라미터 | 설명 | 기본값 |
|---|---|---|
| `days` | 조회 기간. `7`, `14`, `30`, `90` 중 하나 (그 외는 400) | `14` |

응답 예:

```json
{
  "total": 69, "today": 3, "period": 31, "latestAt": "2026-10-27 11:17:21", "days": 14,
  "daily":   [ { "date": "2026-10-14", "count": 3 }, "..." ],
  "domains": [ { "domain": "gmail.com", "count": 11 }, "..." ],
  "recent":  [ { "id": 1, "name": "사용자1", "email": "user1@naver.com", "createdAt": "2026-10-27 11:17:21" } ]
}
```

날짜와 시각은 모두 KST 기준입니다. 날짜별 집계도 UTC가 아니라 KST 날짜로 묶습니다(예: KST 00:30 가입은 UTC로는 전날이지만 당일로 집계).

## 가계부

앱을 실행한 뒤 **http://localhost:8080/ledger** 에서 수입·지출을 기록하고 월별로 확인할 수 있습니다.

| 영역 | 내용 |
|---|---|
| 월 이동 | 상단의 ‹ › 로 월을 바꾸고, "이번 달"로 돌아옵니다 |
| 요약 | 잔액(수입 − 지출), 수입, 지출과 지난달 지출 대비 증감 |
| 내역 입력 | 구분(지출/수입), 날짜, 금액, 분류, 자산, 내용, 메모. 금액은 천 단위 콤마로 표시하고 `+1천 +1만 +5만 +10만` 버튼으로 더할 수 있습니다. 분류·자산은 **최근 쓴 것을 앞에 둔 칩**으로 고르고, 저장 후에는 날짜·분류·자산을 유지해서 연속으로 입력하기 쉽습니다. 모바일에서는 우하단 **+ 버튼**을 누르면 하단 시트로 열립니다 |
| 일자별 현황 | **달력**(지출이 큰 날일수록 진하게)과 **일별 지출 차트**를 전환해서 봅니다. 날짜를 누르면 그날의 내역만 보고 입력 폼의 날짜도 바뀝니다 ("표로 보기" 지원) |
| 분류별 지출과 예산 | 분류별 지출 합계와 예산 사용률. 80% 이상은 **주의(▲)**, 초과하면 **초과(⚠)** 로 표시하고 그 자리에서 예산을 설정·수정·삭제할 수 있음 |
| 내역 | 날짜별로 묶어 **일 소계**와 함께 표시. 검색(내용·메모·분류·자산·금액), 구분·분류·자산 필터, 정렬(날짜/금액), 수정, 삭제 |
| 통장 내역 가져오기 | 인터넷뱅킹에서 내려받은 거래내역 파일을 올려 한꺼번에 등록 (아래 참고) |

- 금액은 원 단위 정수로 저장합니다. 날짜는 KST 기준 `yyyy-MM-dd`입니다.
- 예산은 분류별로 하나이며 **매달 같은 금액이 적용**됩니다. 지출이 없는 분류도 예산이 있으면 목록에 0원으로 나옵니다.
- 이체(계좌 간 이동)는 아직 지원하지 않습니다.
- 검색·필터·정렬은 **선택한 달의 내역** 안에서 동작합니다. 분류 이름을 누르면 그 분류의 내역만 모아 봅니다.
- 최근 쓴 분류·자산은 이 브라우저(localStorage)에만 저장되며, 저장소를 쓸 수 없는 환경에서도 화면은 정상 동작합니다.

색상은 의미에 고정되어 있습니다: **파랑**=동작·선택, **초록**=수입, **주황**=지출입니다. 예산 막대는 주황(정상) → 노랑(80% 이상, ▲ 주의) → 빨강(초과, ⚠)으로 바뀌고, 상태는 항상 아이콘과 문구를 함께 표시합니다. 라이트/다크 모드를 따릅니다.

### 통장 입출내역 가져오기

화면 상단의 **통장 내역 가져오기**에서 인터넷뱅킹 거래내역 파일을 올립니다.

1. 파일을 선택하거나 끌어다 놓습니다. 파일은 **이 브라우저 안에서만** 읽고, 확인한 내역만 서버에 저장합니다.
2. 머리글(거래일, 입금/출금, 적요, 메모…)로 열을 자동 인식하고, 분류를 거래 내용으로 추정합니다(예: 스타벅스 → 식비, 급여 → 급여, 못 맞추면 기타).
3. 미리보기에서 구분·분류를 고치거나 제외할 행을 해제합니다. 이미 등록된 거래는 **"이미 등록됨"** 으로 표시되고 자동으로 제외됩니다.
4. **N건 가져오기**를 누르면 저장하고, 가져온 내역 중 가장 최근 달로 이동합니다.

| 항목 | 내용 |
|---|---|
| 지원 형식 | CSV/TSV(UTF-8, **EUC-KR/CP949 자동 판별**), `.xlsx`, 확장자만 `.xls`인 HTML 표 |
| 지원하지 않는 형식 | PDF, 구형 엑셀(.xls, OLE2), 암호가 걸린 파일 — 이유를 안내하고 CSV/xlsx로 저장하도록 알려줍니다 |
| 열 인식 | 입금/출금이 따로 있는 형식, 부호 있는 금액 한 열, 금액 + 입출금 구분 열을 모두 처리. 못 찾으면 "열 인식 조정"에서 직접 고릅니다 |
| 건너뛰는 행 | 날짜나 금액을 읽을 수 없는 행(합계·안내 문구), 금액이 0인 행 |
| 한도 | 한 번에 1,000건, 파일 10MB |
| 자산 | 입력한 통장 이름이 가져오는 모든 내역의 자산으로 붙습니다 |

**중복 판단**: 날짜·구분·금액·내용이 같으면 같은 거래로 보되, 같은 날 같은 거래가 정상적으로 여러 건일 수 있어서(예: 커피 두 잔) **개수로 비교**합니다. 이미 m건이 등록되어 있으면 파일의 같은 거래 중 앞의 m건만 중복입니다. 그래서 같은 파일을 두 번 올려도 이중 등록되지 않습니다. 분류는 판단 기준이 아니라서 분류만 고쳐도 같은 거래입니다.
미리보기에서 **구분(수입/지출)을 직접 바꿔 저장**했다면, 같은 파일을 다시 올릴 때 파일의 원래 방향의 행은 새 거래로 보입니다.

### 가계부 API

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| GET | `/ledger/entries?month=2026-10` | 월별 내역 (최신 날짜 순, `month` 생략 시 이번 달) | 200 / 400 |
| POST | `/ledger/entries` | 내역 등록 | 201 / 400 |
| POST | `/ledger/entries/import?dryRun=false` | 여러 건 한꺼번에 등록 (최대 1,000건). 이미 등록된 거래는 건너뜀. `dryRun=true`면 저장하지 않고 중복 여부만 응답 | 200 / 400 |
| PUT | `/ledger/entries/{id}` | 내역 수정 | 200 / 400 / 404 |
| DELETE | `/ledger/entries/{id}` | 내역 삭제 | 204 / 404 |
| GET | `/ledger/summary?month=2026-10` | 월별 요약 | 200 / 400 |
| GET | `/ledger/budgets` | 예산 목록 | 200 |
| PUT | `/ledger/budgets/{category}` | 분류별 예산 설정 (있으면 금액 변경) | 200 / 400 |
| DELETE | `/ledger/budgets/{category}` | 예산 삭제 | 204 / 404 |

내역 등록·수정 본문:

```json
{ "type": "EXPENSE", "category": "식비", "amount": 12000, "date": "2026-10-27",
  "asset": "신한카드", "title": "점심", "memo": "팀 회식" }
```

| 필드 | 규칙 |
|---|---|
| `type` | 필수, `INCOME`(수입) 또는 `EXPENSE`(지출) |
| `category` | 필수, 20자 이내, `/` 사용 불가 |
| `amount` | 필수, 1 이상 1,000억 이하 정수 |
| `date` | 필수, `yyyy-MM-dd` |
| `asset`, `title`, `memo` | 선택 (각각 20, 50, 100자 이내) |

예산 설정 본문: `{ "monthlyAmount": 300000 }`

가져오기 본문은 위 내역 항목의 배열입니다: `{ "entries": [ { ... }, { ... } ] }`. 응답은 `{ "created": 3, "skipped": 1, "duplicates": [false, true, false, false] }`이며 `duplicates`는 요청과 같은 순서로 각 내역의 중복 여부입니다. 한 건이라도 검증에 실패하면 전체가 저장되지 않고, 오류 키가 `entries[1].category`처럼 몇 번째 항목인지 알려줍니다.

```bash
# 월별 요약
curl 'localhost:8080/ledger/summary?month=2026-10'

# 지출 등록
curl -X POST localhost:8080/ledger/entries -H 'Content-Type: application/json' \
  -d '{"type":"EXPENSE","category":"식비","amount":12000,"date":"2026-10-27"}'

# 식비 예산 설정 (경로의 한글 분류는 URL 인코딩이 필요합니다: 식비 = %EC%8B%9D%EB%B9%84)
curl -X PUT 'localhost:8080/ledger/budgets/%EC%8B%9D%EB%B9%84' -H 'Content-Type: application/json' \
  -d '{"monthlyAmount":300000}'
```

요약 응답에는 수입·지출·잔액, 지난달 지출(`previousExpense`), 예산 합계(`totalBudget`), 분류별 지출(`expenseByCategory`, 예산 포함), 분류별 수입, 그 달의 모든 날짜별 수입·지출(`daily`)이 들어 있습니다.
검증에 실패하면 400과 함께 대시보드·사용자 API와 같은 형식의 `{"필드": "메시지"}`를 돌려줍니다.

## 시간대

- DB의 `created_at`은 **UTC**로 저장됩니다 (`CURRENT_TIMESTAMP`).
- API 응답의 `createdAt`은 **KST(Asia/Seoul)** 로 변환되어 나갑니다 (`UserResponse`).
- SQL로 직접 조회할 때는 `users_kst` 뷰가 KST로 변환해 줍니다.

## 프로젝트 구조

```
src/main/java/com/example/demo/
├── DemoApplication.java
├── UserCheckRunner.java        # 시작 시 users_kst 조회 출력 (연결 확인용)
├── config/
│   ├── SqliteJdbcConfig.java   # SQLite 방언, LIMIT/OFFSET, 최대 페이지 크기
│   └── WebConfig.java          # /dashboard, /ledger → 정적 페이지 연결
├── dashboard/
│   ├── DashboardController.java  # GET /dashboard/stats
│   ├── DashboardService.java     # 집계 SQL (KST 기준)
│   └── DashboardStats.java       # 응답 DTO
├── ledger/
│   ├── LedgerController.java     # /ledger/entries, /summary, /budgets
│   ├── LedgerService.java        # 내역 CRUD, 월별 집계, 예산 upsert
│   ├── LedgerEntry.java          # 엔티티 (ledger_entries 테이블)
│   ├── LedgerEntryRepository.java
│   ├── EntryRequest.java / EntryResponse.java / EntryType.java
│   ├── BudgetRequest.java / BudgetResponse.java
│   ├── ImportRequest.java / ImportResult.java  # 통장 내역 가져오기 (중복 판단은 LedgerService)
│   ├── BadRequestException.java  # 의도한 400 (일반 예외까지 400으로 바꾸지 않기 위해 분리)
│   ├── LedgerSummary.java        # 월별 요약 응답 DTO
│   └── LedgerExceptionHandler.java  # 가계부 API 전용 400 처리
└── user/
    ├── User.java               # 엔티티 (users 테이블)
    ├── UserRepository.java
    ├── UserResponse.java       # 응답 DTO (UTC → KST 변환)
    └── UserController.java     # REST API, 검증, 예외 처리

src/main/resources/
├── schema.sql                  # 시작할 때 실행되는 스키마 (IF NOT EXISTS)
└── static/
    ├── index.html              # 루트(/) 시작 페이지
    ├── js/
    │   └── bank-import.js      # 통장 파일 파서 (CSV/XLSX/HTML, 열 인식, 분류 추정)
    ├── dashboard.html          # 대시보드 화면 (HTML/CSS/JS 단일 파일)
    └── ledger.html             # 가계부 화면 (HTML/CSS/JS 단일 파일)

src/test/
├── java/com/example/demo/
│   ├── dashboard/
│   │   └── DashboardApiIntegrationTest.java  # 통계 API 통합 테스트
│   ├── ledger/
│   │   ├── LedgerApiIntegrationTest.java     # 가계부 API 통합 테스트
│   │   └── LedgerImportIntegrationTest.java  # 통장 내역 가져오기·중복 판단 테스트
│   └── user/
│       ├── UserControllerTest.java       # 단위 테스트 (Mockito + standalone MockMvc)
│       └── UserApiIntegrationTest.java   # 통합 테스트 (인메모리 SQLite)
└── resources/
    └── application.properties        # 테스트용 설정 (인메모리 DB, 메인의 schema.sql 사용)
```

## 구현 시 참고한 점

- **SQLite 방언**: Spring Data JDBC가 SQLite 방언을 제공하지 않아 `AnsiDialect`를 감싼 커스텀 `JdbcDialect`를 등록했고, SQLite가 지원하는 `LIMIT … OFFSET …` 절을 직접 구현했습니다.
- **커넥션 풀 크기 1**: SQLite는 파일 단위로 쓰기를 하나만 허용하므로 `hikari.maximum-pool-size=1`로 제한했습니다. 인메모리 DB를 쓰는 테스트에서는 커넥션마다 DB가 따로 만들어지기 때문에 이 설정이 더 중요합니다.
- **중복 이메일 → 409**: SQLite 드라이버는 제약조건 위반을 SQLState 없이 error code `19`로만 알려줘서, `UncategorizedSQLException`을 따로 잡아 409로 변환합니다.
- **`created_at` 직접 설정**: Spring Data JDBC는 INSERT 때 모든 컬럼을 바인딩하므로 DB의 `DEFAULT`가 적용되지 않습니다. 그래서 `User.create()`에서 UTC 시각을 채웁니다.
- **정렬 항목 제한**: 정렬 프로퍼티가 그대로 SQL의 컬럼명이 되므로 허용 목록(`id`, `name`, `email`, `createdAt`)만 통과시킵니다.
- **이름 정렬**: SQLite 기본 비교 방식(`BINARY`)이라 한글은 유니코드 순서(가나다순), 영문은 대문자가 소문자보다 먼저 정렬됩니다.
- **배치 저장 대신 한 건씩 저장**: Spring Data JDBC의 `saveAll`은 배치 INSERT를 쓰는데, SQLite 드라이버가 배치에서 행마다의 생성 키를 돌려주지 못해 `After saving the identifier must not be null`로 실패합니다. 가져오기는 한 트랜잭션 안에서 하나씩 저장합니다.
- **일반 `IllegalArgumentException`은 400으로 바꾸지 않음**: 가계부 API의 400은 전용 예외(`BadRequestException`)만 변환합니다. 모든 `IllegalArgumentException`을 400으로 바꾸면 위와 같은 서버 내부 오류가 "잘못된 요청"으로 가려집니다.
