package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.dialect.JdbcArrayColumns;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/**
 * Spring Data JDBC는 SQLite 방언을 기본 제공하지 않으므로 ANSI 방언을 JdbcDialect로 감싸 등록한다.
 */
@Configuration
public class SqliteJdbcConfig extends AbstractJdbcConfiguration {

    private static final int MAX_PAGE_SIZE = 100;

    private static final class SqliteDialect extends AnsiDialect implements JdbcDialect {
        static final SqliteDialect INSTANCE = new SqliteDialect();

        @Override
        public JdbcArrayColumns getArraySupport() {
            return JdbcArrayColumns.unsupported();
        }

        // AnsiDialect의 "OFFSET n ROWS FETCH FIRST m ROWS ONLY"는 SQLite가 지원하지 않는다
        @Override
        public LimitClause limit() {
            return SQLITE_LIMIT_CLAUSE;
        }
    }

    private static final LimitClause SQLITE_LIMIT_CLAUSE = new LimitClause() {
        @Override
        public String getLimit(long limit) {
            return "LIMIT " + limit;
        }

        @Override
        public String getOffset(long offset) {
            // SQLite는 OFFSET만 단독으로 쓸 수 없고 LIMIT이 필요하다 (-1은 제한 없음)
            return "LIMIT -1 OFFSET " + offset;
        }

        @Override
        public String getLimitOffset(long limit, long offset) {
            return "LIMIT " + limit + " OFFSET " + offset;
        }

        @Override
        public Position getClausePosition() {
            return Position.AFTER_ORDER_BY;
        }
    };

    /** 한 번에 요청할 수 있는 최대 페이지 크기 */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> resolver.setMaxPageSize(MAX_PAGE_SIZE);
    }

    @Override
    public JdbcDialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return SqliteDialect.INSTANCE;
    }
}
