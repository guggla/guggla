Guggla the [Scala](http://www.scala-lang.org/) Script Engine.

## About

JSR 223 compliant [Scala](http://www.scala-lang.org/) Scripting engine. The scripting engine supports running in an OSGi container or as part of a stand alone application. 
It has optional support for compiling from/to a JCR repository. 

## Getting Started

This component uses a [Maven 2](http://maven.apache.org/) build environment. 
It requires a Java 5 JDK (or higher) and Maven 2.2.1 or later. We recommend to use the latest Maven version.

If you have Maven 2 installed, you can compile and package the jar using the following command:

    mvn package
    
To Install the package in a running Sling instance use:

    mvn sling:install

## Example

### How to get the Scala scripting engine

    def getScriptEngine(): ScriptEngine = {
      val factories = ServiceRegistry.lookupProviders(classOf[ScriptEngineFactory])
      val scalaEngineFactory = factories.find(_.getEngineName == "Scala Scripting Engine")
      scalaEngineFactory.map(_.getScriptEngine).getOrElse(
        throw new AssertionError("Scala Scripting Engine not found"))
    }
    
### How to create a script programatically

    @Test
    def testSimple() {
      val expected = "hello";
      //create the script
      var code = new StringBuilder();
      code.append("package guggla{");
      code.append("\n");
      code.append("class Script(args: ScriptArgs) {");
      code.append("\n");
      code.append("import args._");
      code.append("\n");
      code.append("println(\"output:\" + obj) ");
      code.append("\n");
      code.append("}}");
      //get the script engine
      val scriptEngine: ScriptEngine = getScriptEngine();
      val b = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
      b.put("obj", expected);
      //get a reference to the output
      val writer = new StringWriter();
      scriptEngine.getContext().setWriter(writer);
      scriptEngine.eval(code.toString(), b)
      //check output
      assertEquals("output:" + expected, writer.toString.trim())
    }

## License

## History

The project began its life as a scripting engine module in the [Apache Sling](http://sling.apache.com/) project.