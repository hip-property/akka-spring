package com.hip.akka

import akka.actor.*
import akka.pattern.PatternsCS
import akka.util.Timeout
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.hip.akka.SpringExtension.Companion.SpringExtProvider
import com.hip.akka.config.ActorProxyRegistrar
import com.hip.utils.log
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@Scope("prototype")
@Component
@Target(AnnotationTarget.CLASS)
annotation class ActorBean

@Scope("prototype")
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class ActorOf(val actorClass: KClass<out AbstractActor>)

interface ActorRefProvider<T> {
   fun ref(): ActorRef
   fun tell(message: Any) = ref().tell(message, ActorRef.noSender())
   fun tell(message: Any, sender: ActorRef) = ref().tell(message, sender)

   fun <T> ask(message: Any, timeout: Timeout = Timeout.apply(5, TimeUnit.SECONDS)): CompletableFuture<T> {
      return PatternsCS.ask(ref(), message, timeout).toCompletableFuture() as CompletableFuture<T>
   }
}

interface PrototypeActor<T> : ActorRefProvider<T> {

}

interface SingletonActor<T> : ActorRefProvider<T> {

}

internal class PrototypeActorRefProviderImpl<T : AnnotatedActor>(
   val actorFactory: ActorRefFactory,
   val type: Class<T>
) : PrototypeActor<T> {
   override fun ref(): ActorRef {
      return actorFactory.newActorRef(type)
   }
}

internal class SingletonActorRefProviderImpl<T : AnnotatedActor>(
   val actorFactory: ActorRefFactory,
   val type: Class<T>
) : SingletonActor<T> {
   private val actorRef by lazy { actorFactory.cachingActorRef(type) }
   override fun ref()= actorRef
}


class ActorRefFactory(val actorSystem: ActorSystem) {
   private val cache: LoadingCache<Class<out AnnotatedActor>, ActorRef> = CacheBuilder.newBuilder()
      .build(
         object : CacheLoader<Class<out AnnotatedActor>, ActorRef>() {
            override fun load(key: Class<out AnnotatedActor>): ActorRef {
               return newActorRef(key)
            }
         }
      )


   fun newActorRef(type: Class<out AnnotatedActor>): ActorRef {
      return actorSystem.actorOf(
         SpringExtProvider.get(actorSystem).props(type))
   }

   fun cachingActorRef(type: Class<out AnnotatedActor>): ActorRef {
      return cache.get(type)
   }
}

class SpringExtension : AbstractExtensionId<SpringExtension.SpringExt>() {

   /**
    * Is used by Akka to instantiate the Extension identified by this
    * ExtensionId, internal use only.
    */
   override fun createExtension(system: ExtendedActorSystem): SpringExt {
      return SpringExt()
   }

   /**
    * The Extension implementation.
    */
   class SpringExt : Extension {
      @Volatile private var applicationContext: ApplicationContext? = null

      /**
       * Used to initialize the Spring application context for the extension.
       * @param applicationContext
       */
      fun initialize(applicationContext: ApplicationContext) {
         this.applicationContext = applicationContext
      }

      /**
       * Create a Props for the specified actorBeanName using the
       * SpringActorProducer class.
       *
       * @param actorBeanName  The name of the actor bean to create Props for
       * @return a Props that will create the named actor bean using Spring
       */
//      fun props(actorBeanName: String): Props {
//         return Props.create(SpringActorProducer::class.java,
//            applicationContext, actorBeanName)
//      }
      fun props(actorType:Class<out Any>):Props {
         return Props.create(SpringActorProducer::class.java,
            applicationContext, actorType)
      }
   }

   companion object {

      /**
       * The identifier used to access the SpringExtension.
       */
      var SpringExtProvider = SpringExtension()
   }
}

class SpringActorProducer(internal val applicationContext: ApplicationContext,
                          internal val actorType: Class<out Actor>) : IndirectActorProducer {

   override fun produce(): Actor {
      return applicationContext.getBean(actorType) as Actor
   }

   override fun actorClass(): Class<out Actor> {
      return actorType
   }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Import(AkkaSpringConfig::class,
   ActorProxyRegistrar::class)
annotation class EnableSpringAkka

@Configuration
class AkkaSpringConfig {
   @Autowired
   lateinit var applicationContext: ApplicationContext

   /**
    * Actor system singleton for this application.
    */
   @Bean
   fun actorSystem(): ActorSystem {
      log().info("*****************AkkaSpringConfig ActorSystem wried")
      val actorSystem = ActorSystem.create("AkkaJavaSpring")
      // initialize the application context in the Akka Spring Extension
      SpringExtProvider.get(actorSystem).initialize(applicationContext)
      return actorSystem
   }

   @Bean
   fun actorRefFactory(actorSystem: ActorSystem):ActorRefFactory {
      return ActorRefFactory(actorSystem)
   }

   // In both the instances below, I've set the scope to Prototype.
   // This is to force creation each time there's an injected reference,
   // as the generics vary, and we're declaring the generic types
   // at injection point, not at bean declaration point.
   // However, this might cause complications with bean lifecycles,
   // and beans getting destroyed early.  If that's the case, revisit this.
   @Bean
   @Scope("prototype")
   fun <T : AnnotatedActor> prototypeActorRef(injectionPoint: DependencyDescriptor, actorRefFactory: ActorRefFactory): PrototypeActor<*> {
      val actorType = injectionPoint.resolvableType.getGeneric(0).rawClass!! as Class<T>
      return PrototypeActorRefProviderImpl<T>(actorRefFactory, actorType)
   }

   @Bean
   @Scope("prototype")
   fun <T : AnnotatedActor> singletonActorRef(injectionPoint: DependencyDescriptor, actorRefFactory: ActorRefFactory): SingletonActor<*> {
      val actorType = injectionPoint.resolvableType.getGeneric(0).rawClass!! as Class<T>
      return SingletonActorRefProviderImpl<T>(actorRefFactory, actorType)
   }
}
