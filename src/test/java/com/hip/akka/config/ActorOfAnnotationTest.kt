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
import com.hip.utils.Ids
import com.winterbe.expekt.expect
import org.awaitility.Awaitility.await
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.test.context.junit4.SpringRunner
import java.util.concurrent.TimeUnit

@EnableSpringAkka
@RunWith(SpringRunner::class)
@Import(Service1UsingCachedActor::class,
   Service2UsingCachedActor::class,
   Service1UsingPrototypeActor::class,
   Service2UsingPrototypeActor::class,
   Dependency::class, MyActor::class)
class ActorOfAnnotationTest {

   @Autowired
   lateinit var cache1: Service1UsingCachedActor
   @Autowired
   lateinit var cache2: Service2UsingCachedActor
   @Autowired
   lateinit var proto1: Service1UsingPrototypeActor
   @Autowired
   lateinit var proto2: Service2UsingPrototypeActor

   @Autowired
   lateinit var dependency: Dependency

   @Before
   fun setup() {
      dependency.reset()
   }

   @Test
   @Ignore("Flakey, can't work out why")
   fun given_dependencyIsCachedActorOf_then_theCorrectActorIsInjected() {
      cache1.invoke()
      cache2.invoke()
      await().atMost(5, TimeUnit.SECONDS).until { dependency.invocations.isNotEmpty() }
      expect(dependency.invocations.keys).to.have.size(1)
      expect(dependency.invocations.values.first()).to.equal(2)
   }

   @Test
   @Ignore("Flakey, can't work out why")
   fun given_dependencyIsProtoActorOf_then_theCorrectActorIsInjected() {
      proto1.invoke()
      proto2.invoke()
      await().atMost(5, TimeUnit.SECONDS).until { dependency.invocations.size == 2 }
      dependency.invocations.values.forEach { expect(it).to.equal(1) }
   }
}

@Component
class Service1UsingPrototypeActor(actor: PrototypeActor<MyActor>) : BaseService(actor)

@Component
class Service2UsingPrototypeActor(actor: PrototypeActor<MyActor>) : BaseService(actor)

@Component
class Service1UsingCachedActor(actor: SingletonActor<MyActor>) : BaseService(actor)

@Component
class Service2UsingCachedActor(actor: SingletonActor<MyActor>) : BaseService(actor)

abstract class BaseService(
   val actor: ActorRefProvider<MyActor>
) {
   fun invoke() {
      actor.tell("Hello")
   }
}

class Dependency {
   val invocations: MutableMap<String, Int> = mutableMapOf()

   fun reset() {
      invocations.clear()
   }

   fun invoke(id: String) {
      invocations.put(id,
         invocations.getOrDefault(id, 0) + 1
      )
   }
}

@ActorBean
class MyActor(val dependency: Dependency) : AnnotatedActor() {
   val id: String = Ids.newId()
   @AkkaMessageHandler
   fun handle(message: String) {
      dependency.invoke(id)
   }
}
