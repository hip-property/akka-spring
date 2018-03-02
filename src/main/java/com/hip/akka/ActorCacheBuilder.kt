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
            notification.value.tell(PoisonPill.getInstance(), self())
         }
         .build(object : CacheLoader<K, ActorRef>() {
            override fun load(key: K): ActorRef {
               return creator(key)
            }
         })
   }

}
