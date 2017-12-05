package com.hip.akka

import akka.actor.ActorRef
import akka.pattern.PatternsCS
import akka.util.Timeout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

fun <T> ActorRef.ask(message:Any, timout:Timeout = Timeout.apply(1,TimeUnit.SECONDS)): CompletableFuture<T> {
   return PatternsCS.ask(this,message,timout).toCompletableFuture() as CompletableFuture<T>
}
