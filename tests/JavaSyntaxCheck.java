import java.io.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

public class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(dc, null, null)) {
            Iterable<? extends JavaFileObject> files = fm.getJavaFileObjectsFromStrings(Arrays.asList(args));
            JavacTask task = (JavacTask) compiler.getTask(null, fm, dc, Arrays.asList("-proc:none"), null, files);
            task.parse();
            for (Diagnostic<? extends JavaFileObject> diagnostic : dc.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    throw new AssertionError("Syntax error " + diagnostic);
            }
        }
        System.out.println("PASS: Android Java sources parsed cleanly (not an APK compilation)");
    }
}
