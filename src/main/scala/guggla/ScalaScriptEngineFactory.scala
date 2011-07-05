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

import javax.script.{ ScriptException, ScriptEngine, ScriptEngineFactory, ScriptContext }
import guggla.interpreter.ScalaInterpreter
import guggla.settings.{ ScriptInfo, DefaultScriptInfo, SettingsProvider, DefaultSettingsProvider }
import org.slf4j.LoggerFactory
import scala.tools.nsc.Settings
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import collection.JavaConversions._

object ScalaScriptEngineFactory {
  private val NL = System.getProperty("line.separator");

  val SCALA_SETTINGS = "scala.settings"
  val SCALA_REPORTER = "scala.reporter"
  val SCALA_CLASSPATH_X = "scala.classpath.x"

  val ENGINE_NAME = "Scala Scripting Engine"
  val LANGUAGE_VERSION = "2.8.1"
  val ENGINE_VERSION = "0.9/scala " + LANGUAGE_VERSION
  val EXTENSIONS = List("scala")
  val LANGUAGE_NAME = "Scala"
  val MIME_TYPES = List("application/x-scala")
  val NAMES = List("scala")

}

/**
 * JSR 223 compliant {@link ScriptEngineFactory} for Scala.
 * {@link ScriptInfo} and {@link SettingsProvider} may be used to parametrize
 * this factory. When running inside an OSGi container, ScriptInfo and
 * SettingsProvider are looked up and injected by the Service Component Runtime.
 */
class ScalaScriptEngineFactory extends ScriptEngineFactory {

  import ScalaScriptEngineFactory._

  //
  //  @volatile
  private var scriptInfo: ScriptInfo = new DefaultScriptInfo();
  //
  //  @volatile
  //  private var settingsProvider: SettingsProvider =
  //    new AbstractSettingsProvider {}
  //
  //  private var scalaInterpreter: ScalaInterpreter = null;

  // -----------------------------------------------------< ScriptEngineFactory >---

  override def getEngineName: String = ENGINE_NAME
  override def getEngineVersion: String = ENGINE_VERSION
  override def getExtensions = EXTENSIONS
  override def getLanguageName: String = LANGUAGE_NAME
  override def getLanguageVersion = LANGUAGE_VERSION
  override def getMimeTypes = MIME_TYPES
  override def getNames = NAMES

  override def getParameter(key: String): String = key.toUpperCase match {
    case ScriptEngine.ENGINE => getEngineName
    case ScriptEngine.ENGINE_VERSION => getEngineVersion
    case ScriptEngine.NAME => getNames.head
    case ScriptEngine.LANGUAGE => getLanguageName
    case ScriptEngine.LANGUAGE_VERSION => getLanguageVersion
    case "threading" => "multithreaded"
    case _ => null
  }

  override def getMethodCallSyntax(obj: String, method: String, args: String*): String =
    obj + "." + method + "(" + args.mkString(",") + ")"

  override def getOutputStatement(toDisplay: String): String =
    "println(\"" + toDisplay + "\")"

  //TODO rename
  def getProgram(statements: String*): String = {

    def packageOf(className: String) = {
      val i = className.lastIndexOf('.')
      if (i >= 0) className.substring(0, i)
      else null
    }

    def classOf(className: String) = {
      val i = className.lastIndexOf('.')
      if (i == className.length()) ""
      else className.substring(i + 1)
    }

    val qClassName = scriptInfo.getDefaultScriptClass
    val packageName = packageOf(qClassName);
    val className = classOf(qClassName);

    "package " + packageName + " {" + NL +
      "  class " + className + "(args: " + className + "Args) {" + NL +
      statements.mkString(NL) +
      "  }" + NL +
      "}" + NL;
  }

  override def getScriptEngine(): ScriptEngine = new ScalaScriptEngine(this, scriptInfo)

  // -----------------------------------------------------< SCR integration >---

  def setScriptInfo(scriptInfo: ScriptInfo) {
    if (scriptInfo == null)
      throw new IllegalArgumentException("ScriptInfo may not be null")

    if (scriptInfo != this.scriptInfo)
      this.scriptInfo = scriptInfo
  }

  protected def unsetScriptInfo(scriptInfo: ScriptInfo) {
    this.scriptInfo = new DefaultScriptInfo();
  }

  def getScriptInfoProvider: ScriptInfo = scriptInfo

  //  def setSettingsProvider(settingsProvider: SettingsProvider) {
  //    if (settingsProvider == null)
  //      throw new IllegalArgumentException("SettingsProvider may not be null")
  //
  //    if (this.settingsProvider != settingsProvider) {
  //      this.settingsProvider = settingsProvider
  //      scalaInterpreter = null
  //    }
  //  }
  //
  //  protected def unsetSettingsProvider(settingsProvider: SettingsProvider) {
  //    this.settingsProvider = new AbstractSettingsProvider {}
  //  }
  //
  //  def getSettingsProvider: SettingsProvider = settingsProvider

  // -----------------------------------------------------< private >---

}
