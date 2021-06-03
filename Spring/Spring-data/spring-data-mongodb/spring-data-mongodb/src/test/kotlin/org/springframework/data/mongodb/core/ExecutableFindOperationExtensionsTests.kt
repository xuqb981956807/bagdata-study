/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.data.mongodb.core

import com.nhaarman.mockito_kotlin.verify
import example.first.First
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

/**
 * @author Sebastien Deleuze
 * @author Mark Paluch
 */
@RunWith(MockitoJUnitRunner::class)
class ExecutableFindOperationExtensionsTests {

    @Mock(answer = Answers.RETURNS_MOCKS)
    lateinit var operation: ExecutableFindOperation

    @Mock(answer = Answers.RETURNS_MOCKS)
    lateinit var operationWithProjection: ExecutableFindOperation.FindWithProjection<First>

    @Mock(answer = Answers.RETURNS_MOCKS)
    lateinit var distinctWithProjection: ExecutableFindOperation.DistinctWithProjection

    @Test // DATAMONGO-1689
    fun `ExecutableFindOperation#query(KClass) extension should call its Java counterpart`() {

        operation.query(First::class)
        verify(operation).query(First::class.java)
    }

    @Test // DATAMONGO-1689
    fun `ExecutableFindOperation#query() with reified type parameter extension should call its Java counterpart`() {

        operation.query<First>()
        verify(operation).query(First::class.java)
    }

    @Test // DATAMONGO-1689
    fun `ExecutableFindOperation#FindOperationWithProjection#asType(KClass) extension should call its Java counterpart`() {

        operationWithProjection.asType(First::class)
        verify(operationWithProjection).`as`(First::class.java)
    }

    @Test // DATAMONGO-1689
    fun `ExecutableFindOperation#FindOperationWithProjection#asType() with reified type parameter extension should call its Java counterpart`() {

        operationWithProjection.asType()
        verify(operationWithProjection).`as`(First::class.java)
    }

    @Test // DATAMONGO-1761
    fun `ExecutableFindOperation#DistinctWithProjection#asType(KClass) extension should call its Java counterpart`() {

        distinctWithProjection.asType(First::class)
        verify(distinctWithProjection).`as`(First::class.java)
    }
}
