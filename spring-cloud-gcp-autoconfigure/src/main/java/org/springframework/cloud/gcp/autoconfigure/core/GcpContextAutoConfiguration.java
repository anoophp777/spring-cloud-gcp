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

package org.springframework.cloud.gcp.autoconfigure.core;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.resourcemanager.ResourceManagerOptions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gcp.core.DefaultCredentialsProvider;
import org.springframework.cloud.gcp.core.DefaultGcpEnvironmentProvider;
import org.springframework.cloud.gcp.core.DefaultGcpMetadataProvider;
import org.springframework.cloud.gcp.core.DefaultGcpProjectIdProvider;
import org.springframework.cloud.gcp.core.GcpEnvironmentProvider;
import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.cloud.gcp.core.MetadataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Base starter for Google Cloud Projects. Provides defaults for {@link com.google.auth.oauth2.GoogleCredentials}.
 * Binds properties from {@link GcpProperties}.
 *
 * @author Vinicius Carvalho
 * @author João André Martins
 * @author Mike Eltsufin
 */
@Configuration
@EnableConfigurationProperties(GcpProperties.class)
public class GcpContextAutoConfiguration {
	private static final Log LOGGER = LogFactory.getLog(GcpContextAutoConfiguration.class);

	private final GcpProperties gcpProperties;

	public GcpContextAutoConfiguration(GcpProperties gcpProperties) {
		this.gcpProperties = gcpProperties;
	}

	@Bean
	@ConditionalOnMissingBean
	public CredentialsProvider googleCredentials() throws Exception {
		return new DefaultCredentialsProvider(this.gcpProperties);
	}

	/**
	 * @return a {@link GcpProjectIdProvider} that returns the project ID in the properties or, if
	 * none, the project ID from the GOOGLE_CLOUD_PROJECT envvar and Metadata Server
	 */
	@Bean
	@ConditionalOnMissingBean
	public GcpProjectIdProvider gcpProjectIdProvider() {
		GcpProjectIdProvider projectIdProvider =
				this.gcpProperties.getProjectId() != null
						? () -> this.gcpProperties.getProjectId()
						: new DefaultGcpProjectIdProvider();

		if (LOGGER.isInfoEnabled()) {
			LOGGER.info("The default project ID is " + projectIdProvider.getProjectId());
		}

		return projectIdProvider;
	}

	/**
	 * Provides default implementation for determining GCP environment.
	 * Can be overridden to avoid interacting with real environment.
	 */
	@Bean
	@ConditionalOnMissingBean
	public static GcpEnvironmentProvider gcpEnvironmentProvider() {
		return new DefaultGcpEnvironmentProvider();
	}

	/**
	 * Provides default implementation of Cloud Resource Manager.
	 */
	@Bean
	@ConditionalOnMissingBean
	public ResourceManager gcpResourceManager() {
		return ResourceManagerOptions.getDefaultInstance().getService();
	}

	/**
	 * Provides default implementation of Cloud Resource Manager.
	 */
	@Bean
	@ConditionalOnMissingBean
	public MetadataProvider gcpMetadataProvider() {
		return new DefaultGcpMetadataProvider();
	}
}
