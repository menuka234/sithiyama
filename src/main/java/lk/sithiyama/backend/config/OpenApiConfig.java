package lk.sithiyama.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI/Swagger metadata, including the bearer-token scheme. */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI sithiyamaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SITHIYAMA API")
                        .version("0.1.0")
                        .description("""
                                Backend for SITHIYAMA, an offline-first trail system for Sri Lankan hill forests.

                                It serves the community route library, syncs recorded tracks (including
                                Companion Node breadcrumbs), keeps trip plans and check-ins, and triages SOS alerts
                                raised over LoRa from the field.

                                `GET /api/v1/routes` endpoints are public. Everything else needs a bearer token
                                from `POST /api/v1/auth/register` or `POST /api/v1/auth/login`.
                                """)
                        .contact(new Contact().name("SITHIYAMA"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
