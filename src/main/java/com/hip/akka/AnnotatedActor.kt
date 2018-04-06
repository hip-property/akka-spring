package com.hip.akka

import akka.actor.AbstractActor
import akka.japi.pf.ReceiveBuilder
import akka.persistence.AbstractPersistentActor
import akka.persistence.RecoveryCompleted
import com.hip.utils.log
import org.springframework.context.annotation.Scope
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import scala.Option
import java.util.concurrent.CompletableFuture

@Target(AnnotationTarget.FUNCTION)
annotation class AkkaMessageHandler(val subscribeFromEventStream: Boolean = false)

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
            val annotation = method.getAnnotation(AkkaMessageHandler::class.java)
            if (method.parameterCount != 1) throw IllegalArgumentException("Method ${method.name} on ${type.name} must take exactly 1 argument.")
            val paramType = method.parameterTypes[0]
            method.isAccessible = true
            receive.match(paramType, { param ->
               try {
                  method.invoke(target, param)
               } catch (exception: Exception) {
                  log().error("Failed to invoke method ${method.name} with param of type ${param::class.java.name} - threw ${exception::class.java.name}")
                  throw exception
               }
            })

            if (annotation.subscribeFromEventStream) {
               target.context.system.eventStream().subscribe(target.self(), paramType)
            }
         }
      receive.matchAny { message -> log().warn("${target::class.java.name} received unmatched message of type ${message::class.java.name} which will be ignored") }
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

   override fun createReceive(): Receive {
      return AnnotatedReceiveBuilder(this).build()
   }
}

abstract class AnnotatedPersistentActor(private val persistenceId: String) : AbstractPersistentActor() {
   override fun persistenceId(): String = persistenceId

   @AkkaMessageHandler
   protected fun handleRecoveryComplete(message: RecoveryCompleted) {
      log().info("Recovery complete")
   }

   protected fun <T> stashWhile(func: () -> T): T {
      context.become(ReceiveBuilder.create()
         .matchAny({ stash() })
         .build()
      )

      val response = try {
         func()
      } catch (exception: Exception) {
         log().error("Exception whilst wrapping stashed behaviour.  Will rethrow, and unstash to continue", exception)
         context.unbecome()
         throw exception
      }
      context.unbecome()
      return response
   }

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
      return if (isReplaying) {
         Mono.just(callback(event))
      } else {
         super.persist(event, { persisted ->
            val result = callback.invoke(persisted)
            future.complete(result)
         })
         future.toMono()
      }
   }

   override fun preRestart(reason: Throwable, message: Option<Any>) {
      reason.printStackTrace()
      log().warn("Restarting from error: ${reason.message}, message - ${message}")
      super.preRestart(reason, message)
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
