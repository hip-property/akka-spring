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
package com.hip.akka.config

import com.hip.akka.*
import com.winterbe.expekt.expect
import org.awaitility.Awaitility.await
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.test.context.junit4.SpringRunner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(SpringRunner::class)
class ActorProxyRegistrarTest {

   @Autowired
   lateinit var component: UsesFunction

   @Autowired
   lateinit var observer:Observer

   @Test
   fun canInjectAndInvokeBehaviour() {
      component.greet("Hello, world")
      await().atMost(1, TimeUnit.SECONDS).until { observer.invoked }
   }

   @Test
   fun canInjectAndInvokeFunction() {
      val response = component.ask("Jimmy!").get()
      expect(response.message).to.equal("Hello, Jimmy!")
   }

   @EnableSpringAkka
   @Configuration
   @Import(UsesFunction::class,  Observer::class, GreetingActor::class)
   class Config


}

data class Greeting(val message: String)
data class Question(val message:String)
data class GreetingResponse(val message: String)

@Component
class Observer {
   var invoked:Boolean = false
}

@ActorBean
class GreetingActor(val observer: Observer) : AnnotatedActor() {

   @AkkaMessageHandler
   fun greet(greeting: Greeting) {
      observer.invoked = true
   }

   @AkkaMessageHandler
   fun ask(question: Question):ActorResponse<GreetingResponse> {
      return reply(GreetingResponse("Hello, ${question.message}"))
   }
}

@Component
class UsesFunction(
   private val greeter: ActorAction<Greeting>,
   private val asker: ActorFunction<Question, GreetingResponse>
) {

   fun greet(message: String) {
      greeter.tell(Greeting(message))
   }

   fun ask(message: String): CompletableFuture<GreetingResponse> {
      return asker.ask(Question(message))
   }
}
