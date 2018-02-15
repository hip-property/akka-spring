package com.hip.akka

import akka.actor.AbstractActor
import akka.japi.pf.ReceiveBuilder
import org.springframework.context.annotation.Scope

@Target(AnnotationTarget.FUNCTION)
annotation class AkkaMessageHandler

interface ActorResponse<T> {
   val response: T
}

@Scope("prototype")
abstract class AnnotatedActor : AbstractActor() {

   protected data class DefaultActorResponse<T>(override val response: T) : ActorResponse<T>

   fun <T> reply(response: T): ActorResponse<T> {
      sender.tell(response, self)
      return DefaultActorResponse(response)
   }

   override fun createReceive(): Receive {
      return buildReceive(this::class.java)
   }

   // TODO : The reflection scanning portion of this is cachable.
   // Consider caching if that turns out to be expensive
   private fun buildReceive(type: Class<out AnnotatedActor>): Receive {
      val receive = ReceiveBuilder()
      type.declaredMethods
         .filter { it.isAnnotationPresent(AkkaMessageHandler::class.java) }
         .forEach { method ->
            if (method.parameterCount != 1) throw IllegalArgumentException("Method ${method.name} on ${type.name} must take exactly 1 argument.")
            val paramType = method.parameterTypes[0]
            receive.match(paramType, { param -> method.invoke(this, param) })
         }
      return receive.build()
   }
}
