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
