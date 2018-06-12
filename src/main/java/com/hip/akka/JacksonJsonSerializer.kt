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
