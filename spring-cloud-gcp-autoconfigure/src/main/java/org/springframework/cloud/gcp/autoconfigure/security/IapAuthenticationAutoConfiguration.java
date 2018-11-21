/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gcp.autoconfigure.security;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.resourcemanager.ResourceManager;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gcp.autoconfigure.core.GcpContextAutoConfiguration;
import org.springframework.cloud.gcp.autoconfigure.core.environment.ConditionalOnGcpEnvironment;
import org.springframework.cloud.gcp.core.GcpEnvironment;
import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.cloud.gcp.core.MetadataProvider;
import org.springframework.cloud.gcp.security.iap.AppEngineAudienceValidator;
import org.springframework.cloud.gcp.security.iap.AudienceValidator;
import org.springframework.cloud.gcp.security.iap.ComputeEngineAudienceValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoderJwkSupport;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Autoconfiguration for extracting pre-authenticated user identity from Google Cloud IAP header.
 *
 * <p>Provides:
 * <ul>
 *   <li>a custom {@link BearerTokenResolver} extracting identity from {@code x-goog-iap-jwt-assertion} header
 *   <li>an ES256 web registry-based JWT token decoder bean with the following standard validations:
 *     <ul>
 *         <li>Issue time
 *         <li>Expiration time
 *         <li>Issuer
 *         <li>Audience (this validation is only enabled if running on AppEngine or ComputeEngine, or if a custom
 *         audience is provided through {@code spring.cloud.gcp.security.iap.audience} property)
 *     </ul>
 * </ul>
 * <p>If a custom {@link WebSecurityConfigurerAdapter} is present, it must add {@code .oauth2ResourceServer().jwt()}
 * customization to {@link org.springframework.security.config.annotation.web.builders.HttpSecurity} object. If no
 * custom {@link WebSecurityConfigurerAdapter} is found,
 * Spring Boot's default {@code OAuth2ResourceServerWebSecurityConfiguration} will add this customization.
 *
 * @author Elena Felder
 *
 * @since 1.1
 */
@Configuration
@ConditionalOnProperty(value = "spring.cloud.gcp.security.iap.enabled", matchIfMissing = true)
@ConditionalOnClass({AudienceValidator.class})
@AutoConfigureBefore(OAuth2ResourceServerAutoConfiguration.class)
@AutoConfigureAfter(GcpContextAutoConfiguration.class)
@EnableConfigurationProperties(IapAuthenticationProperties.class)
public class IapAuthenticationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public JwtDecoder iapJwtDecoder(IapAuthenticationProperties properties, GcpProjectIdProvider projectIdProvider,
			@Qualifier("jwtValidators") List<OAuth2TokenValidator<Jwt>> validators) {

		NimbusJwtDecoderJwkSupport jwkSupport
				= new NimbusJwtDecoderJwkSupport(properties.getRegistry(), properties.getAlgorithm());
		jwkSupport.setJwtValidator(new DelegatingOAuth2TokenValidator(validators));

		return jwkSupport;
	}

	@Bean
	@ConditionalOnMissingBean(name = "jwtValidators")
	List<OAuth2TokenValidator<Jwt>> jwtValidators(IapAuthenticationProperties properties,
			ObjectProvider<AudienceValidator> audienceVerifier) {
		List<OAuth2TokenValidator<Jwt>> validators = new ArrayList();
		validators.add(new JwtTimestampValidator());
		validators.add(new JwtIssuerValidator(properties.getIssuer()));
		audienceVerifier.ifAvailable(validators::add);

		return validators;
	}


	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty ("spring.cloud.gcp.security.iap.audience")
	AudienceValidator propertyBasedAudienceValidator(IapAuthenticationProperties properties) {
		return new AudienceValidator(properties.getAudience());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnGcpEnvironment(GcpEnvironment.ANY_APP_ENGINE)
	AudienceValidator appEngineBasedAudienceValidator(GcpProjectIdProvider projectIdProvider,
			ResourceManager resourceManager) {
		return new AppEngineAudienceValidator(projectIdProvider, resourceManager);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnGcpEnvironment(GcpEnvironment.ANY_CONTAINER)
	AudienceValidator computeEngineBasedAudienceValidator(GcpProjectIdProvider projectIdProvider,
			ResourceManager resourceManager, MetadataProvider metadataProvider) {
		return new ComputeEngineAudienceValidator(projectIdProvider, resourceManager, metadataProvider);
	}

	@Bean
	@ConditionalOnMissingBean
	public BearerTokenResolver iatTokenResolver(IapAuthenticationProperties properties) {
		return r -> r.getHeader(properties.getHeader());
	}
}
