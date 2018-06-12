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
package com.hip.akka.kafka

import com.hip.akka.*
import com.hip.kafka.EnableKafkaGateway
import com.hip.kafka.KafkaGateway
import com.hip.utils.log
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Import(KafkaEventStreamBridge::class, KafkaEventStreamWrapper::class)
@EnableKafkaGateway
annotation class EnableKafkaEventStreamBridge

@ActorBean
class KafkaEventStreamBridge(val kafkaGateway: KafkaGateway) : AnnotatedActor() {
   init {
      log().info("KafkaEventStreamBridge running")
   }

   @AkkaMessageHandler(subscribeFromEventStream = true)
   fun handleEvent(event: KafkaAkkaEvent) {
      kafkaGateway.send(event)
   }
}

/**
 * Marker interface that indicates that message
 * should be published onto Kafka
 */
interface KafkaAkkaEvent : JacksonJsonSerializable

@Component
class KafkaEventStreamWrapper(
   val actor: SingletonActor<KafkaEventStreamBridge>
) {
   @PostConstruct
   fun init() {
      actor.ref()
   }
}
// This class doesn't do anything, but it forces the creation of FundKafkaDispatcher.
// TODO: Expand ActorBean support so that we can tell Spring to eaglery instantiate some types of actors
