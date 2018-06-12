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
import akka.actor.PoisonPill
import akka.actor.TypedActor.self
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.RemovalNotification
import com.hip.utils.log

/**
 * A cache of actors, which will correctly shut down (with a posion pill)
 * the actor when evicted.
 */
class ActorCacheBuilder<K>(val maxSize: Int, val creator: (K) -> ActorRef) {
   constructor(creator: (K) -> ActorRef) : this(maxSize = 10000, creator = creator)

   fun build(): LoadingCache<K, ActorRef> {
      return CacheBuilder.newBuilder()
         .maximumSize(maxSize.toLong())
         .removalListener<K, ActorRef> { notification: RemovalNotification<K, ActorRef> ->
            log().info("Cached actor for key ${notification.key} has been evicted, and will be shut down")
            notification.value.tell(PoisonPill.getInstance(), ActorRef.noSender())
         }
         .build(object : CacheLoader<K, ActorRef>() {
            override fun load(key: K): ActorRef {
               return creator(key)
            }
         })
   }

}
