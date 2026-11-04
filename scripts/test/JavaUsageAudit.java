import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.tools.*;

/** Compiler-symbol evidence, not an automatic deletion tool. Framework/record hooks stay roots. */
public class JavaUsageAudit {
    private record Imported(Element element,CompilationUnitTree unit,String source) {}
    public static void main(String[] args)throws Exception {
        Path root=Path.of("").toRealPath(),output=root.resolve("build/reports/web-quality/java-usage.tsv");
        var files=new ArrayList<Path>();
        for(String source:List.of("src/main/java","src/test/java","src/integrationTest/java","src/e2eTest/java"))try(var walk=Files.walk(root.resolve(source))){walk.filter(p->p.toString().endsWith(".java")).forEach(files::add);}
        var diagnostics=new DiagnosticCollector<JavaFileObject>();var compiler=ToolProvider.getSystemJavaCompiler();
        try(var manager=compiler.getStandardFileManager(diagnostics,null,null)){
            String cp=Files.readString(root.resolve("build/reports/model-smoke.classpath"));
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none","-classpath",cp,"-Xlint:none"),null,manager.getJavaFileObjectsFromPaths(files));
            var units=new ArrayList<CompilationUnitTree>();task.parse().forEach(units::add);task.analyze();
            long errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
            if(errors>0){for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)System.err.println(d);throw new IllegalStateException("Audit compilation failed: "+errors);}
            Trees trees=Trees.instance(task);var declarations=new LinkedHashMap<Element,String>();var uses=new HashMap<Element,Integer>();var imports=new ArrayList<Imported>();var localUses=new HashMap<CompilationUnitTree,Set<Element>>();
            for(var unit:units)new TreePathScanner<Void,Void>(){
                boolean main(){return unit.getSourceFile().getName().contains("/src/main/java/");}
                void use(){var element=trees.getElement(getCurrentPath());if(element!=null){uses.merge(element,1,Integer::sum);localUses.computeIfAbsent(unit,k->new HashSet<>()).add(element);}}
                void declaration(Tree tree){var element=trees.getElement(getCurrentPath());if(!main()||element==null||trees.getSourcePositions().getStartPosition(unit,tree)<0)return;
                    if(element.getModifiers().contains(Modifier.PRIVATE)&&element.getAnnotationMirrors().isEmpty()
                            &&element.getEnclosingElement().getKind()!=ElementKind.RECORD&&!element.getSimpleName().contentEquals("serialVersionUID")){
                        long line=unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit,tree));declarations.put(element,unit.getSourceFile().getName()+":"+line+"\t"+element);
                    }
                }
                @Override public Void visitImport(ImportTree tree,Void unused){if(main()&&!tree.getQualifiedIdentifier().toString().endsWith(".*")){var element=trees.getElement(new TreePath(getCurrentPath(),tree.getQualifiedIdentifier()));if(element!=null)imports.add(new Imported(element,unit,unit.getSourceFile().getName()+"\t"+tree.toString().strip()));}return null;}
                @Override public Void visitMethod(MethodTree tree,Void unused){declaration(tree);return super.visitMethod(tree,unused);}
                @Override public Void visitVariable(VariableTree tree,Void unused){if(getCurrentPath().getParentPath().getLeaf() instanceof ClassTree)declaration(tree);return super.visitVariable(tree,unused);}
                @Override public Void visitIdentifier(IdentifierTree tree,Void unused){use();return super.visitIdentifier(tree,unused);}
                @Override public Void visitMemberSelect(MemberSelectTree tree,Void unused){use();return super.visitMemberSelect(tree,unused);}
                @Override public Void visitMemberReference(MemberReferenceTree tree,Void unused){use();return super.visitMemberReference(tree,unused);}
                @Override public Void visitNewClass(NewClassTree tree,Void unused){use();return super.visitNewClass(tree,unused);}
            }.scan(unit,null);
            var unusedImports=new ArrayList<String>();imports.forEach(i->{if(!localUses.getOrDefault(i.unit(),Set.of()).contains(i.element()))unusedImports.add(i.source());});Files.createDirectories(output.getParent());Files.write(output.resolveSibling("unused-imports.tsv"),unusedImports);
            var lines=new ArrayList<String>();lines.add("source\tdeclaration\treferences");declarations.forEach((e,s)->lines.add(s+"\t"+uses.getOrDefault(e,0)));Files.createDirectories(output.getParent());Files.write(output,lines);
            System.out.println("Compiler usage audit: "+files.size()+" files, "+declarations.size()+" private declarations, "+declarations.keySet().stream().filter(e->uses.getOrDefault(e,0)==0).count()+" candidates; no automatic deletion.");
        }
    }
}
