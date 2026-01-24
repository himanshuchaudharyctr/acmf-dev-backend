package com.itstrat.acmf.apis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

@Configuration
@OpenAPIDefinition(info = @Info(title = "User API", version = "1.0", contact = @Contact(name = "ACMF Support", email = "info@it-strat.com", url = "https://www.it-strat.com"), license = @License(name = "Proprietary", url = "https://www.it-start.com/licenses/mit"), termsOfService = "https://www.it-strat.com/terms", description = "This API exposes endpoints to manage ACMF."), servers = {
		@Server(url = "${acmf.openapi.dev-url}", description = "Development"),
		@Server(url = "${acmf.openapi.prod-url}", description = "Production") })
public class OpenAPIConfig {

	@Bean
	OpenAPI customizeOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description(
                                        "Provide the JWT token. JWT token can be obtained from the Login API. For testing, use the credentials <strong>john/password</strong>")
                                .bearerFormat("JWT")));
	}
}