/*
 * Copyright 2025 gRPC authors.
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

package io.grpc.kotlin

import com.google.common.truth.Truth.assertThat
import io.grpc.BindableService
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.examples.helloworld.GreeterGrpcKt
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.examples.helloworld.MultiHelloRequest
import io.grpc.examples.helloworld.helloReply
import io.grpc.examples.helloworld.helloRequest
import io.grpc.examples.helloworld.multiHelloRequest
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import kotlin.coroutines.coroutineContext
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RequestMetadataTest {
  @Rule @JvmField val grpcCleanup = GrpcCleanupRule()

  @Test
  fun requestMetadata_unaryRpc_returnsMetadata() {
    val keyA = Metadata.Key.of("test-header-a", Metadata.ASCII_STRING_MARSHALLER)
    val keyB = Metadata.Key.of("test-header-b", Metadata.ASCII_STRING_MARSHALLER)
    val clientStub =
      GreeterGrpcKt.GreeterCoroutineStub(
        testChannel(
          object : GreeterGrpcKt.GreeterCoroutineImplBase() {
            override suspend fun sayHello(request: HelloRequest): HelloReply {
              val metadata = coroutineContext.requestMetadata
              return helloReply { message = listOf(metadata[keyA], metadata[keyB]).joinToString() }
            }
          }
        )
      )
    val metadata = Metadata()
    metadata.put(keyA, "Test message A")
    metadata.put(keyB, "Test message B")

    val response = runBlocking { clientStub.sayHello(helloRequest {}, metadata) }

    assertThat(response.message).isEqualTo("Test message A, Test message B")
  }

  @Test
  fun requestMetadata_insideWithContext_returnsMetadata() {
    val key = Metadata.Key.of("test-header", Metadata.ASCII_STRING_MARSHALLER)
    val clientStub =
      GreeterGrpcKt.GreeterCoroutineStub(
        testChannel(
          object : GreeterGrpcKt.GreeterCoroutineImplBase() {
            override suspend fun sayHello(request: HelloRequest): HelloReply =
              withContext(Dispatchers.Default) {
                helloReply { message = coroutineContext.requestMetadata[key].orEmpty() }
              }
          }
        )
      )
    val metadata = Metadata().apply { put(key, "Dispatched message") }

    val response = runBlocking { clientStub.sayHello(helloRequest {}, metadata) }

    assertThat(response.message).isEqualTo("Dispatched message")
  }

  @Test
  fun requestMetadata_insideStreamingFlow_returnsMetadata() {
    val key = Metadata.Key.of("test-header", Metadata.ASCII_STRING_MARSHALLER)
    val clientStub =
      GreeterGrpcKt.GreeterCoroutineStub(
        testChannel(
          object : GreeterGrpcKt.GreeterCoroutineImplBase() {
            override fun serverStreamSayHello(request: MultiHelloRequest): Flow<HelloReply> = flow {
              val headerValue = coroutineContext.requestMetadata[key]
              for (name in request.nameList) {
                emit(helloReply { message = "$headerValue: $name" })
              }
            }
          }
        )
      )
    val metadata = Metadata().apply { put(key, "Hello") }

    val responses = runBlocking {
      clientStub
        .serverStreamSayHello(
          multiHelloRequest {
            name += "Alice"
            name += "Bob"
          },
          metadata
        )
        .toList()
    }

    assertThat(responses.map { it.message }).containsExactly("Hello: Alice", "Hello: Bob").inOrder()
  }

  @Test
  fun requestMetadata_outsideRpc_throwsIllegalStateException() {
    val exception =
      assertFailsWith<IllegalStateException> { runBlocking { coroutineContext.requestMetadata } }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("requestMetadata is only available inside a grpc-kotlin server RPC")
  }

  private fun testChannel(service: BindableService): Channel {
    val serverName = InProcessServerBuilder.generateName()
    grpcCleanup.register(
      InProcessServerBuilder.forName(serverName)
        .directExecutor()
        .addService(service)
        .build()
        .start()
    )
    return grpcCleanup.register(
      InProcessChannelBuilder.forName(serverName).directExecutor().build()
    )
  }
}
