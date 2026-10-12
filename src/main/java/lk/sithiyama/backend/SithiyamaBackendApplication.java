package lk.sithiyama.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * SITHIYAMA backend - the offline-first trail safety platform.
 *
 * <p>Worked on by the SITHIYAMA team (Menuka Roshen &amp; Thenuka) for the
 * Intellicon '26 Buildathon.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SithiyamaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SithiyamaBackendApplication.class, args);
    }
}
