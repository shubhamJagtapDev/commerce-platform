package com.commerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogApplicationTests {

    @Test
    void applicationTypeExistsIndependently() {
        assertThat(CatalogApplication.class).isNotNull();
    }
}
