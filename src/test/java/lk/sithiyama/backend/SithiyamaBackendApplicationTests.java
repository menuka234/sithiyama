package lk.sithiyama.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SithiyamaBackendApplicationTests {

    @Test
    void contextLoads() {
        // Fails fast if any bean, entity mapping or repository query is broken.
    }
}
