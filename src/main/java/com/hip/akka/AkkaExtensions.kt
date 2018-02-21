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
