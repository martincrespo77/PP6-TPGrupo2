package com.pp6.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.pp6.shortener.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:testdb")
class ShortenerApplicationTests {

	@Autowired
	private AppProperties appProperties;

	@Autowired
	private Clock clock;

	@Test
	void contextLoadsWithStagingDefaults() {
		assertThat(appProperties.baseUrl()).isEqualTo("http://localhost:8080");
		assertThat(appProperties.link().ttl()).isEqualTo(Duration.ofMinutes(60));
		assertThat(appProperties.alias().length()).isEqualTo(5);
		assertThat(appProperties.alias().alphabet()).doesNotContain("0", "O", "1", "l", "I");
		assertThat(clock.getZone().getId()).isEqualTo("Z");
	}
}
