package com.solesonic.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
//@EnableAutoConfiguration(exclude = {
//        OAuth2ClientAutoConfiguration.class,
//        ReactiveOAuth2ClientAutoConfiguration.class
//})
class SolesonicMcpServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
