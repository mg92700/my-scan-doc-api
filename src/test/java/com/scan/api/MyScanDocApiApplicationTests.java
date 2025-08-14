package com.scan.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"app.outbox.relay.enabled=false",
		"app.kafka.topic-doc-created=documents.created"
})
class MyScanDocApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
