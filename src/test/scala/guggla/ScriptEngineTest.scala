/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guggla

import javax.imageio.spi.ServiceRegistry
import javax.script.{ ScriptEngineFactory, ScriptEngine, ScriptContext }
import java.io.StringWriter
import java.util.concurrent.{ TimeUnit, Executors, Callable, Future }
import junit.framework.Assert._
import org.junit.{Ignore, Test}
import scala.collection.JavaConversions._

/**
 * JSR 223 compliance test
 *
 * <br>
 *
 * there is no direct reference to the ScalaScriptingEngine
 *
 */
class ScriptEngineTest {

  implicit def fun2Call[R](f: () => R) = new Callable[R] { def call: R = f() }

  def getScriptEngine: ScriptEngine = {
    val factories = ServiceRegistry.lookupProviders(classOf[ScriptEngineFactory])
    val scalaEngineFactory = factories.find(_.getEngineName == "Scala Scripting Engine")
    scalaEngineFactory.map(_.getScriptEngine).getOrElse(
      throw new AssertionError("Scala Scripting Engine not found"))
  }

  /**
   *  tests a simple piece of code
   *
   *  this can be used as a reference for how to inject a simple string
   */
  @Test
  def testSimple() {
    val expected = "hello"

    //create the script
    val code = new StringBuilder()
    code.append("package guggla{")
    code.append("\n")
    code.append("class Script(args: ScriptArgs) {")
    code.append("\n")
    code.append("import args._")
    code.append("\n")
    code.append("println(\"output:\" + obj) ")
    code.append("\n")
    code.append("}}")

    //get the script engine
    val scriptEngine: ScriptEngine = getScriptEngine
    val b = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE)
    b.put("obj", expected)

    //get a reference to the output
    val writer = new StringWriter()
    scriptEngine.getContext.setWriter(writer)
    scriptEngine.eval(code.toString(), b)

    //check output
    assertEquals("output:" + expected, writer.toString.trim())
  }

  /**
   *  tests a simple piece of code
   *
   *  this can be used as a reference for how to inject a custom Object
   */
  @Test
  def testObject() {
    val scriptEngine: ScriptEngine = getScriptEngine
    val code = new StringBuilder()
    code.append("package guggla{")
    code.append("\n")
    code.append("class Script(args: ScriptArgs) {")
    code.append("\n")
    code.append("import args._")
    code.append("\n")
    code.append("println(\"output:\" + obj.saySomething()) ")
    code.append("\n")
    code.append("}}")

    val say = "hello"

    val b = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE)
    b.put("obj", new TestInject(say))

    val writer = new StringWriter()
    scriptEngine.getContext.setWriter(writer)

    scriptEngine.eval(code.toString(), b)
    assertEquals("output:" + say, writer.toString.trim())
  }

  /**
   * multi-threaded test
   *
   * the purpose of this test is to demonstrate the capabilities/faults that the current ScalaScriptingEngine implementation has.
   */
  @Test
  @Ignore("The Scala script engine is currently not thread safe")
  def testMultipleThreads() {
    val code = new StringBuilder()
    code.append("package guggla{")
    code.append("\n")
    code.append("class Script(args: ScriptArgs) {")
    code.append("\n")
    code.append("import args._")
    code.append("\n")
    code.append("println(\"output:\" + obj.saySomething()) ")
    code.append("\n")
    code.append("}}")

    val threads = 3
    val operations = 100
    val e = Executors.newFixedThreadPool(threads)
    var futures = List[Future[Boolean]]()

    for (i <- 0 to operations) {
        val say = "#" + (i % threads)
        val c: Callable[Boolean] = () => buildSayCallable(code.toString(), say)
      val f: Future[Boolean] = e.submit(c)
      futures = f :: futures
    }
    e.shutdown()
    e.awaitTermination(2 * threads * operations, TimeUnit.SECONDS)

    futures.foreach(f => {
      try {
        assertTrue(f.get(10, TimeUnit.SECONDS))
      } catch {
        case e: Exception => { e.printStackTrace(); fail(e.getMessage); }
      }
    })
  }

  def buildSayCallable(code: String, say: String): Boolean = {

    val scriptEngine: ScriptEngine = getScriptEngine
    // println("thread executing with engine: " + scriptEngine + ", say: " + say);

    val b = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE)
    b.put("obj", new TestInject(say))

    val writer = new StringWriter()
    scriptEngine.getContext.setWriter(writer)

    scriptEngine.eval(code.toString, b)
    "output:" + say == writer.toString.trim()
  }

  class TestInject(sayWhat: String) {
    def saySomething() = sayWhat

    override def toString = { "TestInject(" + sayWhat + ")"; }
  }

}

