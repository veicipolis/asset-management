package com.veici.asset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class AssetManagementApplicationTests {

	@Test
	void contextLoads() {
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void shouldUseDedicatedTestDatabase() {
		assertEquals(
				"asset_management_test",
				jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)
		);

		assertEquals(
				"asset_test@localhost",
				jdbcTemplate.queryForObject("SELECT CURRENT_USER()", String.class)
		);
	}

}
