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

import com.google.common.reflect.Reflection
import com.hip.akka.*
import com.hip.utils.log
import javassist.ClassPool
import javassist.LoaderClassPath
import javassist.bytecode.SignatureAttribute
import org.reflections.Reflections
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


private typealias ProxyInstance = Any
private typealias BeanName = String

class ActorProxyRegistrar : BeanFactoryPostProcessor, ApplicationContextAware {
   private data class InterfaceSpec(val superClass: Class<out Any>, val typeParams: List<Class<out Any>>)

   private var applicationContext: ApplicationContext? = null
   override fun setApplicationContext(applicationContext: ApplicationContext?) {
      this.applicationContext = applicationContext
   }

   companion object {
      // Global mutable state ... blech.
      // This is needed because we're generating classes and adding them to the runtime.
      // The runtime gets grumpy if we try to redefine classes already defined.
      // Typically, this only happens in tests, but use a shared cache to ensure we don't
      // redefine classes
      private val createdInterfaces = mutableMapOf<InterfaceSpec, Class<out Any>>()
   }

   override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
      val actorClasses = scanForActorClasses()

      actorClasses.flatMap { actorClass ->
         buildProxyDefs(actorClass).toList()
      }.forEach { (beanName, proxy) ->
         beanFactory.registerSingleton(beanName, proxy)
      }
   }


   private fun scanForActorClasses(): Set<Class<out AnnotatedActor>> {
      val actorClasses = Reflections("com.hip")
         .getSubTypesOf(AnnotatedActor::class.java)
      return actorClasses
   }

   private fun buildProxyDefs(actorClass: Class<out AnnotatedActor>): Map<BeanName, ProxyInstance> {
      return actorClass.declaredMethods
         .filter { it.isAnnotationPresent(AkkaMessageHandler::class.java) }
         .map { buildProxyDef(it, actorClass) }
         .toMap()
   }

   private fun buildProxyDef(method: Method, actorClass: Class<out AnnotatedActor>): Pair<BeanName, ProxyInstance> {
      if (method.parameterCount != 1) {
         throw IllegalArgumentException("Cannot create a proxy for ${method.name} on ${actorClass.name} - it must take exactly one parameter")
      }
      val proxy = buildActorMessageHandlerProxy(actorClass, method)
      return proxy
   }

   private fun buildActorMessageHandlerProxy(actorClass: Class<out AnnotatedActor>, method: Method): Pair<BeanName, ProxyInstance> {
      val parameterType = method.parameterTypes[0]!! as Class<Any>
      return if (method.returnType.name == "void") {
         val interfaceType = createParameterizedInterface(ActorAction::class.java, parameterType)
         val proxy = Reflection.newProxy(interfaceType, ActorActionProxy(actorClass, applicationContext!!))
         val proxyName = "${method.name}@${actorClass.name}ActorAction"
         log().info("Created ActorAction<${parameterType.simpleName}> called $proxyName")
         proxyName to proxy
      } else {
         val returnType = getReturnTypeParam(actorClass, method, method.genericReturnType) as Class<Any>
         val interfaceType = try {
            createParameterizedInterface(ActorFunction::class.java, parameterType, returnType)
         } catch (exception: Exception) {
            log().error("Exception when trying to create ActorFunction for method ${method.name} declared on ${actorClass.name}: ", exception)
            throw exception
         }

         val proxy = Reflection.newProxy(interfaceType, ActorFunctionProxy(actorClass, applicationContext!!))
         val proxyName = "${method.name}@${actorClass.name}ActorFunction"
         log().info("Created ActorFunction<${parameterType.simpleName},${returnType.simpleName}> called $proxyName")
         proxyName to proxy
      }
   }

   private fun getReturnTypeParam(actorClass: Class<out AnnotatedActor>, method: Method, returnType: Type): Type {
      if (returnType !is ParameterizedType) {
         throw IllegalArgumentException("Method ${method.name} on ${actorClass.name} is not defined correctly.  Must return an ActorResponse<T> - call reply(T) instead of returning a value")
      }

      return when {
         returnType.rawType == ActorResponse::class.java -> returnType.actualTypeArguments[0]
         returnType.rawType == Mono::class.java -> returnType.actualTypeArguments[0]
         else -> throw IllegalArgumentException("Method ${method.name} on ${actorClass.name} is not defined correctly.  Must return an ActorResponse<T> - call reply(T) instead of returning a value")
      }

   }

   private fun getActionTypeClass(parameterType: Class<Any>): Class<Any> {
      val defaultClassPool = getClassPool()
      val superInterface = defaultClassPool.getCtClass(ActorAction::class.java.name)
      val actorActionInterface = defaultClassPool.makeInterface("${parameterType.name}ActorAction", superInterface)

      val parameterTypeClassName = parameterType.canonicalName.replace(".", "/")
      val classFile = actorActionInterface.classFile
      val signatureAttribute = SignatureAttribute(
         classFile.constPool,
         "Ljava/lang/Object;Lcom/hip/akka/ActorAction<L$parameterTypeClassName;>;")

      classFile.addAttribute(signatureAttribute)
      return actorActionInterface.toClass()
   }

   private fun createParameterizedInterface(superType: Class<out Any>, vararg typeParams: Class<out Any>): Class<out Any> {
      val interfaceSpec = InterfaceSpec(superType, typeParams.toList())
      return createdInterfaces.getOrPut(interfaceSpec, {
         log().info("Creating interface ${superType.name}<${typeParams.joinToString { it.name }}>")
         val defaultClassPool = getClassPool()
         val superInterface = defaultClassPool.getCtClass(superType.name)
         val interfaceName = typeParams.joinToString(separator = "To") { it.simpleName } + superType.simpleName
         val parameterizedInterface = defaultClassPool.makeInterface(interfaceName, superInterface)

         val typeParamsSignature = typeParams.joinToString(";") { it.signatureName() }
         val signature = "${superType.signatureName()}<$typeParamsSignature;>;"
         val classFile = parameterizedInterface.classFile
         val signatureAttribute = SignatureAttribute(
            classFile.constPool,
            "Ljava/lang/Object;$signature")

         classFile.addAttribute(signatureAttribute)
         parameterizedInterface.toClass()
      })
   }

   private fun getClassPool(): ClassPool {
      val defaultClassPool = ClassPool.getDefault()
      //  add this class class loader to ensure classes loaded by spring are found
      defaultClassPool.appendClassPath(LoaderClassPath(this.javaClass.classLoader))
      return defaultClassPool
   }

   fun Class<out Any>.signatureName(): String {
      // This is the JVM spec representation for class names.
      // eg: Ljava/lang/Object
      return "L" + this.canonicalName.replace(".", "/")
   }


}

