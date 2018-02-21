package com.hip.akka

import akka.actor.AbstractActor
import akka.japi.pf.ReceiveBuilder
import akka.persistence.AbstractPersistentActor
import com.hip.utils.log
import org.springframework.context.annotation.Scope
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import scala.Option
import java.util.concurrent.CompletableFuture

@Target(AnnotationTarget.FUNCTION)
annotation class AkkaMessageHandler

interface ActorResponse<out T> {
   val response: T
}


internal class AnnotatedReceiveBuilder(val target: AbstractActor) {
   fun build(): AbstractActor.Receive = buildReceive(target::class.java)

   private fun buildReceive(type: Class<out AbstractActor>): AbstractActor.Receive {
      val receive = ReceiveBuilder()
      type.declaredMethods
         .filter { it.isAnnotationPresent(AkkaMessageHandler::class.java) }
         .forEach { method ->
            if (method.parameterCount != 1) throw IllegalArgumentException("Method ${method.name} on ${type.name} must take exactly 1 argument.")
            val paramType = method.parameterTypes[0]
            method.isAccessible = true
            receive.match(paramType, { param -> method.invoke(target, param) })
         }
      receive.matchAny { message -> log().warn("Received unmatched message of type ${message::class.java.name} which will be ignored") }
      return receive.build()
   }
}

internal data class DefaultActorResponse<T>(override val response: T) : ActorResponse<T>
@Scope("prototype")
abstract class AnnotatedActor : AbstractActor() {
   fun <T> reply(response: T): ActorResponse<T> {
      sender.tell(response, self)
      return DefaultActorResponse(response)
   }

   //   override fun preRestart(reason: Throwable?, message: Option<Any>?) {
//      super.preRestart(reason, message)
//   }
//   override fun postStop() {
//      log().error("Actor ${this.javaClass.simpleName} - $this.nam stopping")
//      super.postStop()
//   }
   override fun createReceive(): Receive {
      return AnnotatedReceiveBuilder(this).build()
   }
}

abstract class AnnotatedPersistentActor(private val persistenceId: String) : AbstractPersistentActor() {
   override fun persistenceId(): String = persistenceId

   protected var isReplaying = false
   fun <T> reply(response: T): ActorResponse<T> {
      sender.tell(response, self)
      return DefaultActorResponse(response)
   }

   override fun preStart() {
      this.isReplaying = true
   }
   override fun onReplaySuccess() {
      super.onReplaySuccess()
      this.isReplaying = false
   }

   protected fun <T, U> persistEvent(event: T, callback: (T) -> U): Mono<U> {
      val future = CompletableFuture<U>()
      if (isReplaying) {
         return Mono.just(callback(event))
      } else {
         super.persist(event, { persisted ->
            val result = callback.invoke(persisted)
            future.complete(result)
         })
         return future.toMono()
      }

   }

   override fun preRestart(reason: Throwable?, message: Option<Any>?) {
      super.preRestart(reason, message)
   }

   override fun postStop() {
      log().error("Actor ${this.javaClass.simpleName} - $this.nam stopping")
      super.postStop()
   }

   override fun createReceive(): Receive {
      return AnnotatedReceiveBuilder(this).build()
   }

   override fun onPersistFailure(cause: Throwable?, event: Any?, seqNr: Long) {
      log().error("Persistence failed")
      super.onPersistFailure(cause, event, seqNr)
   }


   override fun onPersistRejected(cause: Throwable?, event: Any?, seqNr: Long) {
      log().error("Persistence rejected")
      super.onPersistRejected(cause, event, seqNr)
   }

   override fun createReceiveRecover(): Receive {
      // TODO : We may wish to consider additional behaviours
      // during replay
      return AnnotatedReceiveBuilder(this).build()
//      return ReceiveBuilder.create()
//         .matchAny { message -> log().warn("ReceiveRecover got message: $message") }
//         .build()
   }
}
