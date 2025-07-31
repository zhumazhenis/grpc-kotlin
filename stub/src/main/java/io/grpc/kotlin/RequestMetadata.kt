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

import io.grpc.Metadata
import kotlin.coroutines.CoroutineContext

/**
 * The request [Metadata] for the current server RPC.
 *
 * @throws IllegalStateException if accessed outside a grpc-kotlin server RPC
 */
val CoroutineContext.requestMetadata: Metadata
  get() =
    checkNotNull(this[MetadataElement]) {
        "requestMetadata is only available inside a grpc-kotlin server RPC"
      }
      .value

/** A [CoroutineContext.Element] that holds the gRPC request [Metadata]. */
internal class MetadataElement(val value: Metadata) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<MetadataElement>

  override val key: CoroutineContext.Key<MetadataElement>
    get() = Key
}
