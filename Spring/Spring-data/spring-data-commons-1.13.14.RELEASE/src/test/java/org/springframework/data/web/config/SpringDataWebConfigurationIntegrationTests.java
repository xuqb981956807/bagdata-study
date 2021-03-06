/*
 * Copyright 2015-2017 the original author or authors.
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
package org.springframework.data.web.config;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.classloadersupport.HidingClassLoader;
import org.springframework.data.web.ProjectingJackson2HttpMessageConverter;
import org.springframework.data.web.XmlBeamHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.xmlbeam.XBProjector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;

/**
 * Integration test for {@link SpringDataWebConfiguration}.
 *
 * @author Christoph Strobl
 * @author Jens Schauder
 * @author Oliver Gierke
 */
public class SpringDataWebConfigurationIntegrationTests {

	@Test // DATACMNS-987
	public void shouldNotLoadJacksonConverterWhenJacksonNotPresent() {

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();

		createConfigWithClassLoader(HidingClassLoader.hide(ObjectMapper.class), triggerExtendConverters(converters));

		assertThat(converters, not(hasItem(instanceWithClassName(ProjectingJackson2HttpMessageConverter.class))));
	}

	@Test // DATACMNS-987
	public void shouldNotLoadJacksonConverterWhenJaywayNotPresent() {

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();

		createConfigWithClassLoader(HidingClassLoader.hide(DocumentContext.class), triggerExtendConverters(converters));

		assertThat(converters, not(hasItem(instanceWithClassName(ProjectingJackson2HttpMessageConverter.class))));
	}

	@Test // DATACMNS-987
	public void shouldNotLoadXBeamConverterWhenXBeamNotPresent() throws Exception {

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();

		createConfigWithClassLoader(HidingClassLoader.hide(XBProjector.class), triggerExtendConverters(converters));

		assertThat(converters, not(hasItem(instanceWithClassName(XmlBeamHttpMessageConverter.class))));
	}

	@Test // DATACMNS-987
	public void shouldLoadAllConvertersWhenDependenciesArePresent() throws Exception {

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();

		createConfigWithClassLoader(getClass().getClassLoader(), triggerExtendConverters(converters));

		assertThat(converters, hasItem(instanceWithClassName(XmlBeamHttpMessageConverter.class)));
		assertThat(converters, hasItem(instanceWithClassName(ProjectingJackson2HttpMessageConverter.class)));
	}

	@Test // DATACMNS-1152
	public void usesCustomObjectMapper() {

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();

		createConfigWithClassLoader(getClass().getClassLoader(), triggerExtendConverters(converters),
				SomeConfiguration.class);

		boolean found = false;

		for (HttpMessageConverter<?> converter : converters) {

			if (converter instanceof ProjectingJackson2HttpMessageConverter) {

				found = true;

				assertThat(ReflectionTestUtils.getField(converter, "objectMapper"),
						is(sameInstance((Object) SomeConfiguration.MAPPER)));
			}
		}

		assertThat(found, is(true));
	}

	private void createConfigWithClassLoader(ClassLoader classLoader, Consumer<SpringDataWebConfiguration> callback,
			Class<?>... additionalConfigurationClasses) {

		List<Class<?>> configClasses = new ArrayList<Class<?>>(Arrays.asList(additionalConfigurationClasses));
		configClasses.add(SpringDataWebConfiguration.class);

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				configClasses.toArray(new Class<?>[configClasses.size()]));

		context.setClassLoader(classLoader);

		try {
			callback.accept(context.getBean(SpringDataWebConfiguration.class));
		} finally {
			context.close();
		}
	}

	/**
	 * creates a Matcher that check if an object is an instance of a class with the same name as the provided class. This
	 * is necessary since we are dealing with multiple classloaders which would make a simple instanceof fail all the time
	 *
	 * @param expectedClass the class that is expected (possibly loaded by a different classloader).
	 * @return a Matcher
	 */
	private static <T> Matcher<? super T> instanceWithClassName(Class<T> expectedClass) {
		return hasProperty("class", hasProperty("name", equalTo(expectedClass.getName())));
	}

	private static Consumer<SpringDataWebConfiguration> triggerExtendConverters(
			final List<HttpMessageConverter<?>> converters) {

		return new Consumer<SpringDataWebConfiguration>() {

			@Override
			public void accept(SpringDataWebConfiguration t) {
				t.extendMessageConverters(converters);
			}
		};
	}

	@Configuration
	static class SomeConfiguration {

		static ObjectMapper MAPPER = new ObjectMapper();

		@Bean
		ObjectMapper mapper() {
			return MAPPER;
		}
	}
}
