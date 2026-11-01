package com.moapan;
import com.sun.source.tree.*;import com.sun.source.util.*;import java.nio.file.*;import java.net.URI;import java.util.*;import javax.tools.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class MapperScopeTest {
 @Test void productionDefaultMappersAreOwnedResourcesNotCreatedInsideOperations()throws Exception{
  try(var files=Files.walk(Path.of("src/main/java"))){for(var f:files.filter(p->p.toString().endsWith(".java")).toList())assertEquals(0,perOperation(Files.readString(f)),f.toString());}
 }
 @Test void checkerRejectsBothInlineDefaultMapperConstructionFormsAndAcceptsOwnedFields()throws Exception{
  assertEquals(1,perOperation("class A{void x(){new JsonMapper();}}"));
  assertEquals(1,perOperation("class A{java.util.function.Supplier<JsonMapper> factory=()->new JsonMapper();}"));
  assertEquals(1,perOperation("class A{void x(){JsonMapper.builder().build();}}"));
  assertEquals(0,perOperation("class A{private static final JsonMapper JSON=JsonMapper.builder().build();void x(){JSON.readTree(\"{}\");}}"));
 }
 @Test void malformedInputIsAnInspectionFailure() {assertThrows(IllegalArgumentException.class,()->perOperation("class A{"));}
 static int perOperation(String source)throws Exception{
  var compiler=ToolProvider.getSystemJavaCompiler();assertNotNull(compiler);var diagnostics=new DiagnosticCollector<JavaFileObject>();var file=new SimpleJavaFileObject(URI.create("string:///Audit.java"),JavaFileObject.Kind.SOURCE){@Override public CharSequence getCharContent(boolean ignore){return source;}};
  try(var manager=compiler.getStandardFileManager(diagnostics,null,null)){
   var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,List.of(file));var parsed=task.parse();int[] found={0};
   for(var unit:parsed)new TreePathScanner<Void,Void>(){
    void check(String text){String s=text.replaceAll("\\s+","");if(!s.matches("(?:new(?:tools\\.jackson\\.databind\\.json\\.)?JsonMapper\\(\\)|(?:tools\\.jackson\\.databind\\.json\\.)?JsonMapper\\.builder\\(\\)\\.build\\(\\))"))return;for(var path=getCurrentPath().getParentPath();path!=null;path=path.getParentPath())if((path.getLeaf()instanceof MethodTree||path.getLeaf()instanceof LambdaExpressionTree)){found[0]++;return;}}
    @Override public Void visitNewClass(NewClassTree n,Void v){check(n.toString());return super.visitNewClass(n,v);}
    @Override public Void visitMethodInvocation(MethodInvocationTree n,Void v){check(n.toString());return super.visitMethodInvocation(n,v);}
   }.scan(unit,null);
   if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))throw new IllegalArgumentException("Unparsed source");return found[0];
  }
 }
}
