package com.hip.akka

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.PatternsCS
import akka.util.Timeout
import com.hip.akka.SpringExtension.Companion.SpringExtProvider
import org.springframework.context.ApplicationContext
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

interface ActorAction<T> {
   fun tell(message:T)
}

interface ActorFunction<Req,Resp> {
   fun ask(message:Req):CompletableFuture<Resp>
}

internal abstract class ActorProxy(val actorClass:Class<out AnnotatedActor>, val applicationContext: ApplicationContext)  : InvocationHandler {

   private val actorSystem by lazy {
      applicationContext.getBean(ActorSystem::class.java)
   }
   override fun invoke(proxy: Any, method: Method, args: Array<out Any>): Any {
      val actorRef = actorSystem.actorOf(
         SpringExtProvider.get(actorSystem).props(actorClass)
      )
      return invokeActor(actorRef,args[0])
   }
   abstract fun invokeActor(actorRef: ActorRef, message:Any):Any
}
internal class ActorActionProxy(actorClass:Class<out AnnotatedActor>, applicationContext: ApplicationContext) : ActorProxy(actorClass, applicationContext){
   override fun invokeActor(actorRef: ActorRef, message: Any): Any {
      actorRef.tell(message, ActorRef.noSender())
      return true
   }
}

internal class ActorFunctionProxy(actorClass: Class<out AnnotatedActor>, applicationContext: ApplicationContext) : ActorProxy(actorClass, applicationContext) {
   override fun invokeActor(actorRef: ActorRef, message: Any): Any {
      return PatternsCS.ask(actorRef,message, Timeout.apply(2,TimeUnit.SECONDS)).toCompletableFuture()
   }

}
