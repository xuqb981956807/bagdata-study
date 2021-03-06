/*
 * Copyright 2012 - 2013 the original author or authors.
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
package org.springframework.data.solr.core.query;

/**
 * @author Christoph Strobl
 */
public interface HighlightQuery extends Query {

	/**
	 * Highlight options to apply when exectuing query
	 * 
	 * @param highlightOptions
	 * @return
	 */
	<T extends SolrDataQuery> T setHighlightOptions(HighlightOptions highlightOptions);

	/**
	 * @return null if not set
	 */
	HighlightOptions getHighlightOptions();

	/**
	 * @return true if options set
	 */
	boolean hasHighlightOptions();
}
