/*-
 * =========================================================BeginLicense
 * Akka Spring utils
 * .
 * Copyright (C) 2018 HiP Property
 * .
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
 * ===========================================================EndLicense
 */
package com.hip.akka

import akka.actor.ActorRef
import akka.pattern.PatternsCS
import akka.util.Timeout
import com.hip.utils.log
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private object ExceptionHanlder {
   private val logExceptions = { message: Any, throwable: Exception ->
      log().error("An exception was thrown during an ask with type ${message::class.java.name}: $throwable")
   }
}

fun <T> ActorRef.ask(message: Any, timeout: Timeout = Timeout.apply(1, TimeUnit.SECONDS)): CompletableFuture<T> {
   return PatternsCS.ask(this, message, timeout).exceptionally { ex ->
         log().error("An exception was thrown during an Ask: $ex")
         throw ex
      }
      .toCompletableFuture() as CompletableFuture<T>
}
