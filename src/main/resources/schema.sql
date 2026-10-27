CREATE TABLE IF NOT EXISTS users (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    email      TEXT UNIQUE,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE VIEW IF NOT EXISTS users_kst AS
SELECT id, name, email, datetime(created_at, '+9 hours') AS created_at_kst
FROM users;

-- 가계부 내역: 금액은 원 단위 정수, 날짜는 KST 기준 'yyyy-MM-dd' 문자열
CREATE TABLE IF NOT EXISTS ledger_entries (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    type       TEXT    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category   TEXT    NOT NULL,
    amount     INTEGER NOT NULL CHECK (amount > 0),
    entry_date TEXT    NOT NULL,
    asset      TEXT,
    title      TEXT,
    memo       TEXT,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_date ON ledger_entries (entry_date);

-- 카테고리별 월 예산
CREATE TABLE IF NOT EXISTS budgets (
    category       TEXT PRIMARY KEY,
    monthly_amount INTEGER NOT NULL CHECK (monthly_amount > 0)
);
