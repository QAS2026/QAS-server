package com.qas.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import java.util.List;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableConfigurationProperties({GitHubProperties.class, FrontendProperties.class})
public class SecurityConfig {
    @Bean
    ClientRegistrationRepository clientRegistrations(GitHubProperties properties) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("github")
                .clientId(properties.configured() ? properties.clientId() : "not-configured")
                .clientSecret(properties.configured() ? properties.clientSecret() : "not-configured")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("offline_access")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id").clientName("GitHub").build());
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository repository) {
        var manager = new DefaultOAuth2AuthorizedClientManager(registrations, repository);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode().refreshToken().build());
        return manager;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository authorizedClients, GitHubLoginFailureHandler loginFailureHandler,
            FrontendProperties frontendProperties) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return http.cors(Customizer.withDefaults()).authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/config", "/api/csrf", "/api/auth/login", "/error", "/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2Login(oauth -> oauth.authorizedClientRepository(authorizedClients).authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                        .defaultSuccessUrl(frontendProperties.homeUrl(), true).failureHandler(loginFailureHandler))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                        .invalidateHttpSession(true).deleteCookies("JSESSIONID"))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' https://avatars.githubusercontent.com; "
                        + "style-src 'self'; script-src 'self'; connect-src 'self'; "
                        + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(FrontendProperties frontendProperties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendProperties.baseUrl()));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

}
