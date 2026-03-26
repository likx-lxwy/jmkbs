package com.example.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DemoApplicationTests {

    @Test
    void applicationClassIsAvailable() {
        assertThat(DemoApplication.class).isNotNull();
    }

}
