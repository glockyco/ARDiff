package equiv.checking;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import org.apache.commons.lang.SystemUtils;

import javax.tools.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DifferencingRunner {
    private final DifferencingParameters parameters;
    private final Configuration freeMarkerConfiguration;

    public static void main(String[] args) throws IOException, TemplateException {
        Path parameterFilePath = Paths.get(args[0]);
        DifferencingParameterFactory parameterFactory = new DifferencingParameterFactory();
        DifferencingParameters parameters = parameterFactory.load(parameterFilePath.toFile());

        int timeout = Integer.parseInt(args[1]);
        TimeUnit timeUnit = TimeUnit.SECONDS;

        PrintStream errorStream = new PrintStream(parameters.getErrorFile());

        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> {
                try {
                    new DifferencingRunner(parameters).runDifferencing();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, timeout, timeUnit);
        } catch (TimeoutException e) {
            String errorMessage = "Differencing failed due to timeout (" + timeout + " " + timeUnit + ").\n";

            System.out.println(errorMessage);
            e.printStackTrace();

            errorStream.println(errorMessage);
            e.printStackTrace(errorStream);
        } catch (Exception e) {
            String errorMessage = "Differencing failed due to error.\n";

            System.out.println(errorMessage);
            e.printStackTrace();

            errorStream.println(errorMessage);
            e.printStackTrace(errorStream);
        }

        DifferencingResultFactory resultFactory = new DifferencingResultFactory();
        DifferencingResult result = resultFactory.create(parameters);
        Path resultFilePath = Paths.get(parameters.getResultFile());
        resultFactory.persist(resultFilePath.toFile(), result);
    }

    public DifferencingRunner(DifferencingParameters parameters) throws IOException {
        this.parameters = parameters;

        // @TODO: Inject FreeMarker configuration into this file (?).
        /* Create and adjust the FreeMarker configuration singleton */
        this.freeMarkerConfiguration = new Configuration(Configuration.VERSION_2_3_31);
        this.freeMarkerConfiguration.setDirectoryForTemplateLoading(new File("src/main/resources/templates"));
        // Recommended settings for new projects:
        this.freeMarkerConfiguration.setDefaultEncoding("UTF-8");
        this.freeMarkerConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        this.freeMarkerConfiguration.setLogTemplateExceptions(false);
        this.freeMarkerConfiguration.setWrapUncheckedExceptions(true);
        this.freeMarkerConfiguration.setFallbackOnNullLoopVariable(false);
    }

    public void runDifferencing() throws TemplateException, IOException {
        File javaFile = this.createDifferencingDriverClass();
        this.compile(ProjectPaths.classpath, javaFile);
        File configFile = this.createDifferencingJpfConfiguration();

        Config config = JPF.createConfig(new String[]{configFile.getAbsolutePath()});
        JPF jpf = new JPF(config);
        jpf.addListener(new DifferencingListener(this.parameters));
        jpf.run();
    }

    public File createDifferencingDriverClass() throws IOException, TemplateException {
        /* Create a data-model */
        Map<String, Object> root = new HashMap<>();
        root.put("parameters", this.parameters);

        /* Get the template (uses cache internally) */
        Template template = this.freeMarkerConfiguration.getTemplate("DifferencingDriverClass.ftl");

        /* Merge data-model with template */
        File file = new File(this.parameters.getTargetDirectory() + "/" + this.parameters.getTargetClassName() + ".java");
        file.getParentFile().mkdirs();

        try (PrintWriter writer = new PrintWriter(file)) {
            template.process(root, writer);
        }

        return file;
    }

    public File createDifferencingJpfConfiguration() throws IOException, TemplateException {
        /* Create a data-model */
        Map<String, Object> root = new HashMap<>();
        root.put("parameters", this.parameters);

        /* Get the template (uses cache internally) */
        Template template = this.freeMarkerConfiguration.getTemplate("DifferencingConfiguration.ftl");

        /* Merge data-model with template */
        File file = new File(this.parameters.getTargetDirectory() + "/" + this.parameters.getTargetClassName() + ".jpf");
        file.getParentFile().mkdirs();

        try (PrintWriter writer = new PrintWriter(file)) {
            template.process(root, writer);
        }

        return file;
    }

    private void compile(String classpath,File newFile) throws IOException {
        //TODO here catch compilation errors, I think it's not an exception, check with Priyanshu
        File path = new File(classpath);
        path.getParentFile().mkdirs();
        //Think about whether to do it for the classpaths in the tool as well (maybe folder instrumented not automatically created)
        if(!path.exists())
            path.mkdir();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);
        String dir = classpath;
        List<String> classpathParts = Arrays.asList(classpath, ProjectPaths.jpf_core_jar, ProjectPaths.jpf_symbc_jar);
        classpath = String.join(SystemUtils.IS_OS_WINDOWS ? ";" : ":", classpathParts);
        List<String> options = Arrays.asList("-g", "-cp", classpath, "-d", dir);
        Iterable<? extends JavaFileObject> cpu =
            fileManager.getJavaFileObjectsFromFiles(Arrays.asList(new File[]{newFile}));
        boolean success = compiler.getTask(null, fileManager, diagnosticCollector, options, null, cpu).call();
        if(!success){
            List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
            String message = "Compilation error: ";
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                // read error details from the diagnostic object
                message+=diagnostic.getMessage(null);
            }
            throw new IOException("Compilation error: "+message);
        }
    }
}
