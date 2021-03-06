/*
 * Copyright 2012 - 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.solr.server.config;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.data.solr.server.support.HttpSolrClientFactoryBean;
import org.springframework.lang.Nullable;
import org.w3c.dom.Element;

/**
 * {@link CloudSolrClientBeanDefinitionParser} replaces HttpSolrServerBeanDefinitionParser from version 1.x.
 *
 * @author Christoph Strobl
 * @since 2.0
 */
public class CloudSolrClientBeanDefinitionParser extends AbstractBeanDefinitionParser {

	@Nullable
	@Override
	protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(HttpSolrClientFactoryBean.class);
		setSolrHome(element, builder);
		return getSourcedBeanDefinition(builder, element, parserContext);
	}

	private void setSolrHome(Element element, BeanDefinitionBuilder builder) {
		builder.addPropertyValue("zkHost", element.getAttribute("zkHost"));
		builder.addPropertyValue("collection", element.getAttribute("collection"));
		builder.addPropertyValue("zkClientTimeout", element.getAttribute("maxConnections"));
		builder.addPropertyValue("zkConnectTimeout", element.getAttribute("zkConnectTimeout"));
		builder.addPropertyValue("httpConnectTimeout", element.getAttribute("httpConnectTimeout"));
		builder.addPropertyValue("httpMaxConnect", element.getAttribute("httpMaxConnect"));
		builder.addPropertyValue("httpSoTimeout", element.getAttribute("httpSoTimeout"));
	}

	private AbstractBeanDefinition getSourcedBeanDefinition(BeanDefinitionBuilder builder, Element source,
			ParserContext context) {

		AbstractBeanDefinition definition = builder.getBeanDefinition();
		definition.setSource(context.extractSource(source));
		return definition;
	}

}
