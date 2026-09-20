/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or    
 * (at your option) any later version.                                  
 *                                                                      
 * This program is distributed in the hope that it will be useful,      
 * but WITHOUT ANY WARRANTY; without even the implied warranty of       
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the        
 * GNU General Public License for more details.                         
 *                                                                      
 * You should have received a copy of the GNU General Public License    
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ics205.web

/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or    
 * (at your option) any later version.                                  
 *                                                                      
 * This program is distributed in the hope that it will be useful,      
 * but WITHOUT ANY WARRANTY; without even the implied warranty of       
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the        
 * GNU General Public License for more details.                         
 *                                                                      
 * You should have received a copy of the GNU General Public License    
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import _root_.io.github.classgraph.{ClassGraph, ClassInfo}
import com.google.inject.multibindings.Multibinder
import com.google.inject.name.Names
import com.google.inject.{Binder, TypeLiteral}

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

object AutoBind {

  def discoverImplementationsOf[T: ClassTag](packagesOnly: Seq[String]): Seq[String] = {
    val traitClass = summon[ClassTag[T]].runtimeClass
    val scan = new ClassGraph()
      .enableClassInfo()
      .ignoreClassVisibility()
      .acceptPackages(packagesOnly*)
      .scan()

    try {
      val implInfos =
        (scan.getClassesImplementing(traitClass.getName).asScala ++
          scan.getSubclasses(traitClass.getName).asScala).distinctBy(_.getName)

      implInfos.filter(isConcrete).map(_.getName).toSeq
    } finally {
      scan.close()
    }
  }

  def bindAllImplementationsOf[T: ClassTag](
                                             binder: Binder,
                                             packagesOnly: Seq[String],
                                             named: Option[String] = None,
                                             asSingleton: Boolean = false
                                           ): Unit = {
    val traitClass = summon[ClassTag[T]].runtimeClass
    val typeLiteral =
      TypeLiteral.get(traitClass).asInstanceOf[TypeLiteral[T]]

    val scan = new ClassGraph()
      .enableClassInfo()
      .ignoreClassVisibility()
      .acceptPackages(packagesOnly*)
      .scan()

    try {
      val implInfos =
        (scan.getClassesImplementing(traitClass.getName).asScala ++
          scan.getSubclasses(traitClass.getName).asScala).distinctBy(_.getName)

      val concrete = implInfos.filter(isConcrete)

      val mb: Multibinder[T] =
        named match {
          case Some(n) => Multibinder.newSetBinder(binder, typeLiteral, Names.named(n))
          case None    => Multibinder.newSetBinder(binder, typeLiteral)
        }
      concrete.foreach { ci =>
        val impl: Class[? <: T] = Class.forName(ci.getName).asInstanceOf[Class[? <: T]]
        
        // Detect Scala objects
        val isScalaObject = ci.getName.endsWith("$")
        
        if (isScalaObject) {
          try {
            val moduleField = impl.getField("MODULE$")
            val instance = moduleField.get(null).asInstanceOf[T]
            mb.addBinding().toInstance(instance)
          } catch {
            case e: Exception =>
              // Skip if it's not actually a singleton object we can access
          }
        } else {
          // For classes, check if they are likely injectable by Guice
          val hasInject = impl.getDeclaredConstructors.exists(_.isAnnotationPresent(classOf[com.google.inject.Inject])) ||
            impl.getDeclaredConstructors.exists(_.isAnnotationPresent(classOf[jakarta.inject.Inject]))
          val hasNoArg = impl.getDeclaredConstructors.exists(_.getParameterCount == 0)

          if (hasInject || hasNoArg) {
            val b = mb.addBinding().to(impl)
            if (asSingleton) b.asEagerSingleton()
          } else {
            // Log skipped class for visibility
            // System.err.println(s"Skipping non-injectable class: ${ci.getName}")
          }
        }
      }
    } finally {
      scan.close()
    }
  }

  private def isConcrete(ci: ClassInfo): Boolean =
    !ci.isAbstract && !ci.isInterface && !ci.isAnonymousInnerClass && !ci.isSynthetic
}
