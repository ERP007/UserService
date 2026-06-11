package com.fallguys.userservice.shared.infrastructure.persistence.tenancy;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenancySchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public TenancySchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createTableIfMissing();
        renameCodeColumnIfNeeded();
        dropPrimaryKeyIfNotTenancyCode();
        dropIdColumnIfPresent();
        addTenancyCodePrimaryKeyIfMissing();
        createTypeIndexIfMissing();
    }

    private void createTableIfMissing() {
        jdbcTemplate.execute("""
                create table if not exists tenancies (
                    tenancy_code varchar(30) not null primary key,
                    name varchar(100) not null,
                    type varchar(30) not null
                )
                """);
    }

    private void renameCodeColumnIfNeeded() {
        if (!columnExists("tenancies", "tenancy_code") && columnExists("tenancies", "code")) {
            jdbcTemplate.execute("alter table tenancies rename column code to tenancy_code");
        }
    }

    private void dropPrimaryKeyIfNotTenancyCode() {
        String primaryKey = primaryKeyName();
        if (primaryKey == null || primaryKeyIsOnlyTenancyCode()) {
            return;
        }

        jdbcTemplate.execute("alter table tenancies drop constraint " + primaryKey);
    }

    private void dropIdColumnIfPresent() {
        if (columnExists("tenancies", "id")) {
            jdbcTemplate.execute("alter table tenancies drop column id");
        }
    }

    private void addTenancyCodePrimaryKeyIfMissing() {
        if (primaryKeyIsOnlyTenancyCode()) {
            return;
        }

        jdbcTemplate.execute("alter table tenancies alter column tenancy_code set not null");
        jdbcTemplate.execute("alter table tenancies add constraint pk_tenancies primary key (tenancy_code)");
    }

    private void createTypeIndexIfMissing() {
        jdbcTemplate.execute("create index if not exists idx_tenancies_type on tenancies (type)");
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = ?
                  and column_name = ?
                """, Integer.class, tableName, columnName);

        return count != null && count > 0;
    }

    private String primaryKeyName() {
        return jdbcTemplate.query("""
                select constraint_name
                from information_schema.table_constraints
                where table_schema = current_schema()
                  and table_name = 'tenancies'
                  and constraint_type = 'PRIMARY KEY'
                """, resultSet -> resultSet.next() ? resultSet.getString("constraint_name") : null);
    }

    private boolean primaryKeyIsOnlyTenancyCode() {
        return jdbcTemplate.query("""
                select column_name
                from information_schema.key_column_usage
                where table_schema = current_schema()
                  and table_name = 'tenancies'
                  and constraint_name = (
                      select constraint_name
                      from information_schema.table_constraints
                      where table_schema = current_schema()
                        and table_name = 'tenancies'
                        and constraint_type = 'PRIMARY KEY'
                  )
                order by ordinal_position
                """, (resultSet, rowNumber) -> resultSet.getString("column_name"))
                .equals(java.util.List.of("tenancy_code"));
    }
}
