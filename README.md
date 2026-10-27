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

DB 파일(`demo.db`)은 프로젝트 루트에 있습니다. 파일이 없다면 아래 명령으로 만들 수 있습니다.

```bash
sqlite3 demo.db <<'SQL'
CREATE TABLE users (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    email      TEXT UNIQUE,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE VIEW users_kst AS
SELECT id, name, email, datetime(created_at, '+9 hours') AS created_at_kst
FROM users;
SQL
```

> 접속 URL은 `jdbc:sqlite:demo.db`(상대경로)입니다. 앱은 **프로젝트 루트에서 실행**해야 이 파일을 찾습니다.
> 다른 위치에서 실행하면 빈 DB 파일이 새로 만들어질 수 있습니다.

### 2. 실행

```bash
./gradlew bootRun
```

기본 포트는 `8080`입니다. 시작할 때 `users_kst` 뷰의 내용이 콘솔에 출력됩니다(`UserCheckRunner`, 연결 확인용).

### 3. 테스트

```bash
./gradlew test
```

테스트는 실제 `demo.db`를 사용하지 않고 인메모리 SQLite를 사용합니다. 설정은 `src/test/resources/`의 `application.properties`와 `schema.sql`에 분리되어 있습니다.

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
│   └── SqliteJdbcConfig.java   # SQLite 방언, LIMIT/OFFSET, 최대 페이지 크기
└── user/
    ├── User.java               # 엔티티 (users 테이블)
    ├── UserRepository.java
    ├── UserResponse.java       # 응답 DTO (UTC → KST 변환)
    └── UserController.java     # REST API, 검증, 예외 처리

src/test/
├── java/com/example/demo/user/
│   ├── UserControllerTest.java       # 단위 테스트 (Mockito + standalone MockMvc)
│   └── UserApiIntegrationTest.java   # 통합 테스트 (인메모리 SQLite)
└── resources/
    ├── application.properties        # 테스트용 설정 (인메모리 DB)
    └── schema.sql                    # 테스트용 스키마
```

## 구현 시 참고한 점

- **SQLite 방언**: Spring Data JDBC가 SQLite 방언을 제공하지 않아 `AnsiDialect`를 감싼 커스텀 `JdbcDialect`를 등록했고, SQLite가 지원하는 `LIMIT … OFFSET …` 절을 직접 구현했습니다.
- **커넥션 풀 크기 1**: SQLite는 파일 단위로 쓰기를 하나만 허용하므로 `hikari.maximum-pool-size=1`로 제한했습니다. 인메모리 DB를 쓰는 테스트에서는 커넥션마다 DB가 따로 만들어지기 때문에 이 설정이 더 중요합니다.
- **중복 이메일 → 409**: SQLite 드라이버는 제약조건 위반을 SQLState 없이 error code `19`로만 알려줘서, `UncategorizedSQLException`을 따로 잡아 409로 변환합니다.
- **`created_at` 직접 설정**: Spring Data JDBC는 INSERT 때 모든 컬럼을 바인딩하므로 DB의 `DEFAULT`가 적용되지 않습니다. 그래서 `User.create()`에서 UTC 시각을 채웁니다.
- **정렬 항목 제한**: 정렬 프로퍼티가 그대로 SQL의 컬럼명이 되므로 허용 목록(`id`, `name`, `email`, `createdAt`)만 통과시킵니다.
- **이름 정렬**: SQLite 기본 비교 방식(`BINARY`)이라 한글은 유니코드 순서(가나다순), 영문은 대문자가 소문자보다 먼저 정렬됩니다.
