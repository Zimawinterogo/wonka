import com.sun.source.util.JavacTask;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Source-only syntax check: does not claim to resolve Minecraft/Fabric API. */
public final class SourceSyntaxAudit {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("src/main/java");
        List<File> sourceFiles;
        try (Stream<Path> files = Files.walk(root)) {
            sourceFiles = files.filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile).sorted().toList();
        }
        if (sourceFiles.size() < 45) throw new IllegalStateException("Too few Java source files: " + sourceFiles.size());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK compiler not installed");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var units = manager.getJavaFileObjectsFromFiles(sourceFiles);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "21"), null, units);
            int parsed = 0;
            for (var unit : task.parse()) parsed++;
            var errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
            for (var error : errors) System.err.println(error);
            if (!errors.isEmpty()) throw new IllegalStateException("Syntax errors: " + errors.size());
            if (parsed != sourceFiles.size()) throw new IllegalStateException("Parsed " + parsed + "/" + sourceFiles.size());
            System.out.println("PASS: parsed all " + parsed + " Java source files using javac --release 21 (syntax only)");
        }
    }
}
