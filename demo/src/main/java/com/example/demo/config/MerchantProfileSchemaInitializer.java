package com.example.demo.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Component
public class MerchantProfileSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public MerchantProfileSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ensureColumn("users", "merchant_store_name", "VARCHAR(120) NULL");
        ensureColumn("users", "merchant_contact_name", "VARCHAR(80) NULL");
        ensureColumn("users", "merchant_contact_phone", "VARCHAR(30) NULL");
        ensureColumn("users", "merchant_business_address", "VARCHAR(255) NULL");
        ensureColumn("users", "merchant_license_number", "VARCHAR(80) NULL");
        ensureColumn("users", "merchant_description", "VARCHAR(500) NULL");
    }

    private void ensureColumn(String tableName, String columnName, String definition) throws Exception {
        if (columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private boolean columnExists(String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, columnName)) {
                return resultSet.next();
            }
        }
    }
}
