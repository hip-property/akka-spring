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
