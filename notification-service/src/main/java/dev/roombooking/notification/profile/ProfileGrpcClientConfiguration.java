package dev.roombooking.notification.profile;

import dev.roombooking.profile.grpc.v1.UserProfileServiceGrpc;
import io.grpc.ClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.List;
import java.util.function.Supplier;

@Configuration(proxyBeanMethods = false)
class ProfileGrpcClientConfiguration {
    private static final String REGISTRATION_ID = "profile-service";
    private static final String SERVICE_PRINCIPAL = "notification-service";

    @Bean
    OAuth2AuthorizedClientManager serviceOAuth2AuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients) {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    UserProfileServiceGrpc.UserProfileServiceBlockingStub userProfileServiceStub(
            GrpcChannelFactory channelFactory,
            OAuth2AuthorizedClientManager authorizedClientManager) {
        ClientInterceptor authentication = new BearerTokenAuthenticationInterceptor(
                (Supplier<String>) () -> accessToken(authorizedClientManager));
        ChannelBuilderOptions options = ChannelBuilderOptions.defaults()
                .withInterceptors(List.of(authentication));
        return UserProfileServiceGrpc.newBlockingStub(
                channelFactory.createChannel(REGISTRATION_ID, options));
    }

    private String accessToken(OAuth2AuthorizedClientManager manager) {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(SERVICE_PRINCIPAL)
                .build();
        OAuth2AuthorizedClient client = manager.authorize(request);
        if (client == null) {
            throw new IllegalStateException("Keycloak did not authorize notification-service");
        }
        return client.getAccessToken().getTokenValue();
    }
}
