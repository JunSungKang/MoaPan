package com.moapan.architecture;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;
class PortableDomainTest {
 @Test void corePoliciesCompileWithoutApplicationOrFrameworkClasspath() throws Exception {
  Path output=Path.of("build/portable-domain").toAbsolutePath();Files.createDirectories(output);
  int result=ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-classpath",output.toString(),"-d",output.toString(),
   "src/main/java/com/moapan/article/domain/ArticleInput.java",
   "src/main/java/com/moapan/article/domain/ContentProcessor.java",
   "src/main/java/com/moapan/classification/domain/TopicTaxonomy.java");
  assertEquals(0,result,"Reusable core must compile without Spring, database or model runtime");
 }
}
