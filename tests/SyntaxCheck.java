import com.sun.source.util.JavacTask;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Syntax-only check of Android sources when android.jar is not available. */
public final class SyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<File> files = new ArrayList<>();
        for (String path : args) files.add(new File(path));
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none"), null, manager.getJavaFileObjectsFromFiles(files));
            task.parse();
        }
        boolean error = false;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                error = true;
                System.err.println(d);
            }
        }
        if (error) throw new AssertionError("Java syntax check failed");
        System.out.println("PASS: parsed " + files.size() + " Android Java sources");
    }
}
