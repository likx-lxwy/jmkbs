package com.example.demo.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RefundChatSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public RefundChatSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS refund_chat_messages (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  order_id BIGINT NOT NULL,
                  sender_id BIGINT NOT NULL,
                  receiver_id BIGINT NOT NULL,
                  content VARCHAR(800) NOT NULL,
                  created_at DATETIME NOT NULL,
                  PRIMARY KEY (id),
                  KEY idx_refund_chat_order_id (order_id),
                  KEY idx_refund_chat_sender_id (sender_id),
                  KEY idx_refund_chat_receiver_id (receiver_id),
                  CONSTRAINT fk_refund_chat_order FOREIGN KEY (order_id) REFERENCES customer_orders (id) ON DELETE CASCADE ON UPDATE RESTRICT,
                  CONSTRAINT fk_refund_chat_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                  CONSTRAINT fk_refund_chat_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
                )
                """);
    }
}
