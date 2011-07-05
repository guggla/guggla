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

import java.io.{ BufferedReader, Reader, IOException, OutputStream, InputStream }
import java.util.concurrent.locks.{ ReadWriteLock, ReentrantReadWriteLock }
import javax.script.{ AbstractScriptEngine, Bindings, SimpleBindings, ScriptEngineFactory, ScriptException, ScriptContext }
import guggla.interpreter.{ Bindings => ScalaBindings }
import guggla.util.Utils.makeIdentifier
import guggla.settings.ScriptInfo
import org.slf4j.LoggerFactory
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.io.AbstractFile
import guggla.settings.{ SettingsProvider, DefaultSettingsProvider }
import ScalaScriptEngineFactory._
import org.slf4j.LoggerFactory
import guggla.interpreter.ScalaInterpreter
import scala.tools.nsc.Settings
import java.security.MessageDigest
import scala.collection.JavaConversions._

/**
 * JSR 223 compliant {@link ScriptEngine} for Scala.
 * Scripts must be of the following form:
 *
 * <pre>
 * package my.cool.script
 * class foo(args: fooArgs) {
 *   import args._ // import the bindings
 *
 *   println("bar:" + bar)
 * }
 * </pre>
 *
 * Here it is assumed that the {@Bindings} passed for script evaluation contains a
 * value for the name <em>bar</em>.
 *
 * The parameter <code>args</code> contains statically typed bindings generated from the
 * <code>Bindings</code> passed to the script engine. The individual values in
 * <code>args</code> appear to be of all visible types. This is achieved using implicit
 * conversion when necessary: for a value v of type T let S be the smallest super type of T
 * which is accessible (i.e. class loading succeeds). Then v is exposed with static type
 * S in <code>args</code>. Let further be J the set of interface implemented by T which
 * are not implemented by S already. For each interface I in J which has no super type
 * I' of I in J an implicit conversion from S to I is included in <code>args</code>.
 */
class ScalaScriptEngine(factory: ScalaScriptEngineFactory, scriptInfo: ScriptInfo)
  extends AbstractScriptEngine {

  private val log = LoggerFactory.getLogger(classOf[ScalaScriptEngineFactory]);

  private val rwLock = new ReentrantReadWriteLock();

  private val NL = System.getProperty("line.separator");

  // -----------------------------------------------------< AbstractScriptEngine >---

  def createBindings: Bindings =
    new SimpleBindings

  def getFactory: ScriptEngineFactory =
    factory;

  @throws(classOf[ScriptException])
  def eval(reader: Reader, context: ScriptContext) = {
    val script = new StringBuilder;
    try {
      val bufferedScript = new BufferedReader(reader);

      var nextLine = bufferedScript.readLine
      while (nextLine != null) {
        script.append(nextLine)
        script.append(NL)
        nextLine = bufferedScript.readLine
      }
    } catch {
      case e: IOException => throw new ScriptException(e)
    }

    eval(script.toString, context)
  }

  def bindingsToScalaBindings(context: ScriptContext): ScalaBindings = {

    val bindings = context.getBindings(ScriptContext.ENGINE_SCOPE)
    val scalaBindings = ScalaBindings()

    for (val key <- bindings.keySet) {
      val value = bindings.get(key)
      if (value == null) log.debug("{} has null value. skipping", key)
      else scalaBindings.putValue(makeIdentifier(key), value)
    }
    return scalaBindings;
  }

  @throws(classOf[ScriptException])
  def eval(script: String, context: ScriptContext) = {
    try {

      val scriptClass = scriptInfo.getScriptClass(script, context)
      val scalaBindings = bindingsToScalaBindings(context);

      // xxx: Scripts need to be compiled every time.
      // The preamble for injecting the bindings into the script
      // depends on the actual types of the bindings. So effectively
      // there is a specific script generated for each type of bindings.

      val interpreter = writeLocked(rwLock) {
        getScalaInterpreter(context, script, scalaBindings, scriptClass);
      }

      //      var result: Reporter = writeLocked(rwLock) {
      //        interpreter.compile(scriptClass, script, scalaBindings)
      //      }
      //
      //      if (result != null && result.hasErrors)
      //        throw new ScriptException(result.toString)

      val result = readLocked(rwLock) {
        val outputStream = new OutputStream {
          val writer = context.getWriter

          @throws(classOf[IOException])
          def write(b: Int) {
            writer.write(b)
          }

          @throws(classOf[IOException])
          override def flush() {
            writer.flush()
          }
        }

        val inputStream = new InputStream {
          val reader = context.getReader

          @throws(classOf[IOException])
          def read() = reader.read();
        }

        val result = interpreter.execute(scriptClass, scalaBindings, inputStream, outputStream)
        outputStream.flush()
        result
      }
      if (result.hasErrors)
        throw new ScriptException(result.toString)
      else
        result
    } catch {
      case e: ScriptException => throw e
      case e: Exception => throw new ScriptException("Error executing script").initCause(e)
    }
  }

  // -----------------------------------------------------< private >---

  private def readLocked[T](lock: ReadWriteLock)(thunk: => T) = {
    lock.readLock.lock()
    try {
      thunk
    } finally {
      lock.readLock.unlock()
    }
  }

  private def writeLocked[T](lock: ReadWriteLock)(thunk: => T) = {
    lock.writeLock.lock()
    try {
      thunk
    } finally {
      lock.writeLock.unlock()
    }
  }

  @throws(classOf[ScriptException])
  def getScalaInterpreter(context: ScriptContext, script: String, bindings: ScalaBindings, scriptClass: String): ScalaInterpreter = {

    //TODO fix this for JCR and BND
    //TODO tmp dir?
    val f = new java.io.File("tmp")
    f.mkdir()
    val tmp: AbstractFile = AbstractFile.getDirectory(f.getAbsolutePath);
    val wName = hashScript(script, bindings);

    val isFirstRun = Option(tmp.lookupName(wName, true)).isEmpty

    val settingsProvider: SettingsProvider = new DefaultSettingsProvider(tmp.subdirectoryNamed(wName));

    context.getAttribute(SCALA_SETTINGS) match {
      case settings: Settings => settingsProvider.setScalaSettings(settings)
      case x => if (x != null) log.warn("Invalid settings: {}", x);
    }

    context.getAttribute(SCALA_REPORTER) match {
      case reporter: Reporter => settingsProvider.setReporter(reporter)
      case x => if (x != null) log.warn("Invalid reporter: {}", x);
    }

    context.getAttribute(SCALA_CLASSPATH_X) match {
      case classpath: Array[AbstractFile] => settingsProvider.setClasspathX(classpath)
      case x => if (x != null) log.warn("Invalid classpathx: {}", x);
    }

    log.debug("Creating Scala script engine from settings {}", settingsProvider);

    val scalaInterpreter = new ScalaInterpreter(settingsProvider.getSettings,
      settingsProvider.getReporter, settingsProvider.getClasspathX);

    if (isFirstRun) {
      scalaInterpreter.compile(scriptClass, script, bindings)
    }
    return scalaInterpreter;
  }

  def hashScript(script: String, bindings: ScalaBindings): String = {
    val all = script + "|" + bindings.toString
    val sha = hexDigest(all.getBytes("UTF-8"));
    return sha;
  }

  // source http://scala-tools.org/mvnsites/liftweb/lift-util/scaladocs/net/liftweb/util/SecurityHelpers.scala.html

  /** create an hex encoded SHA hash from a Byte array */
  def hexDigest(in: Array[Byte]): String = {
    val binHash = (MessageDigest.getInstance("SHA")).digest(in)
    hexEncode(binHash)
  }

  /** create an hex encoded SHA-256 hash from a Byte array */
  def hexDigest256(in: Array[Byte]): String = {
    val binHash = (MessageDigest.getInstance("SHA-256")).digest(in)
    hexEncode(binHash)
  }

  /** encode a Byte array as hexadecimal characters */
  def hexEncode(in: Array[Byte]): String = {
    val sb = new StringBuilder
    val len = in.length
    def addDigit(in: Array[Byte], pos: Int, len: Int, sb: StringBuilder) {
      if (pos < len) {
        val b: Int = in(pos)
        val msb = (b & 0xf0) >> 4
        val lsb = (b & 0x0f)
        sb.append((if (msb < 10) ('0' + msb).asInstanceOf[Char] else ('a' + (msb - 10)).asInstanceOf[Char]))
        sb.append((if (lsb < 10) ('0' + lsb).asInstanceOf[Char] else ('a' + (lsb - 10)).asInstanceOf[Char]))

        addDigit(in, pos + 1, len, sb)
      }
    }
    addDigit(in, 0, len, sb)
    sb.toString
  }
}
