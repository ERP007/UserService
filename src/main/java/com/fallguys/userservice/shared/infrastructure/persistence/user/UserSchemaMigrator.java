package com.fallguys.userservice.shared.infrastructure.persistence.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public UserSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateTenancyNameColumn();
        dropTenancyForeignKeyIfPresent();
        dropTenanciesTableIfPresent();
    }

    /**
     * users.tenancy 컬럼을 users.tenancy_name으로 이전한다.
     *
     * 흐름:
     * 1) 기존 tenancies 테이블이 있으면 users.tenancy_code 기준으로 tenancies.name을 먼저 복사한다.
     * 2) 복사할 이름이 없으면 기존 users.tenancy 값, 마지막으로 users.tenancy_code를 fallback으로 사용한다.
     * 3) 더 이상 사용하지 않는 users.tenancy 컬럼과 check constraint를 제거한다.
     *
     * 트랜잭션: 애플리케이션 기동 시 스키마 보정용 쓰기 작업이다.
     */
    private void migrateTenancyNameColumn() {
        if (!tableExists("users")) {
            return;
        }

        jdbcTemplate.execute("alter table users drop constraint if exists users_tenancy_check");

        if (!columnExists("users", "tenancy_name")) {
            jdbcTemplate.execute("alter table users add column tenancy_name varchar(100)");
        } else {
            jdbcTemplate.execute("alter table users alter column tenancy_name type varchar(100)");
        }

        if (tableExists("tenancies") && columnExists("tenancies", "name") && columnExists("tenancies", "tenancy_code")) {
            jdbcTemplate.execute("""
                    update users u
                       set tenancy_name = t.name
                      from tenancies t
                     where u.tenancy_code = t.tenancy_code
                       and (u.tenancy_name is null or btrim(u.tenancy_name) = '')
                    """);
        }

        if (columnExists("users", "tenancy")) {
            jdbcTemplate.execute("""
                    update users
                       set tenancy_name = coalesce(nullif(btrim(tenancy_name), ''), tenancy, tenancy_code)
                    """);
            jdbcTemplate.execute("alter table users drop column tenancy");
        }

        jdbcTemplate.execute("""
                update users
                   set tenancy_name = tenancy_code
                 where tenancy_name is null or btrim(tenancy_name) = ''
                """);
        jdbcTemplate.execute("alter table users alter column tenancy_name set not null");
    }

    private void dropTenancyForeignKeyIfPresent() {
        if (!tableExists("users")) {
            return;
        }

        jdbcTemplate.execute("alter table users drop constraint if exists fk_users_tenancy");
        jdbcTemplate.execute("""
                do $$
                declare
                    constraint_to_drop text;
                begin
                    for constraint_to_drop in
                        select c.conname
                          from pg_constraint c
                          join pg_class source_table on source_table.oid = c.conrelid
                          join pg_namespace source_schema on source_schema.oid = source_table.relnamespace
                          join pg_class target_table on target_table.oid = c.confrelid
                          join pg_namespace target_schema on target_schema.oid = target_table.relnamespace
                         where c.contype = 'f'
                           and source_schema.nspname = current_schema()
                           and source_table.relname = 'users'
                           and target_schema.nspname = current_schema()
                           and target_table.relname = 'tenancies'
                    loop
                        execute format(
                            'alter table %I.%I drop constraint %I',
                            current_schema(),
                            'users',
                            constraint_to_drop
                        );
                    end loop;
                end $$;
                """);
    }

    private void dropTenanciesTableIfPresent() {
        jdbcTemplate.execute("drop table if exists tenancies");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name = ?
                """, Integer.class, tableName);

        return count != null && count > 0;
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
}
