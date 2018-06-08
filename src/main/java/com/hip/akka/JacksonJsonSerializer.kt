package com.hip.akka

import akka.serialization.Serializer
import com.hip.json.jackson
import scala.Option
import java.io.Serializable

/**
 * AKKA serialiser using jackson/json
 */
class JacksonJsonSerializer : Serializer {

   private val objectMapper = jackson()

   override fun fromBinary(bytes: ByteArray?, manifest: Option<Class<*>>?): Any {
      val clazz = manifest!!.get()
      return objectMapper.readValue(bytes, clazz)
   }

   override fun identifier(): Int {
      return 67567521
   }

   override fun toBinary(o: Any?): ByteArray {
      return objectMapper.writeValueAsBytes(o)
   }

   override fun includeManifest(): Boolean {
      return true
   }
}

interface JacksonJsonSerializable : Serializable
