package differencing;

import differencing.classification.Classification;
import differencing.classification.RunClassifier;
import differencing.models.Benchmark;
import differencing.models.Run;
import differencing.repositories.BenchmarkRepository;
import differencing.repositories.RunRepository;
import equiv.checking.ProjectPaths;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.tools.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DifferencingRunner {
    private final Configuration freeMarkerConfiguration;

    public static void main(String[] args) throws IOException, TemplateException {
        new DifferencingRunner().run(args[0], args[1], Integer.parseInt(args[2]));
    }

    public DifferencingRunner() throws IOException {
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

    public void run(String benchmarkDir, String toolName, int timeout) throws IOException {
        // Read the differencing configuration:
        Path parameterFilePath = Paths.get(benchmarkDir, "instrumented", "IDiff" + toolName + "-Parameters.txt");

        DifferencingParameterFactory parameterFactory = new DifferencingParameterFactory();

        if (!parameterFilePath.toFile().exists()) {
            String error = "Error: '" + parameterFilePath + "' does not exist.";

            DifferencingParameters parameters = parameterFactory.create(toolName, benchmarkDir);
            Arrays.stream(parameters.getGeneratedFiles()).forEach(file -> new File(file).delete());

            Classification result = new RunClassifier(
                false, true, false, false, Collections.emptySet()
            ).getClassification();

            Benchmark benchmark = new Benchmark(parameters.getBenchmarkName(), parameters.getExpectedResult());

            Run run = new Run(
                parameters.getBenchmarkName(),
                parameters.getToolVariant(),
                result,
                null,
                null,
                null,
                null,
                null,
                error
            );

            RunRepository.delete(run);
            BenchmarkRepository.insertOrUpdate(benchmark);
            RunRepository.insertOrUpdate(run);

            System.out.println(error);
            return;
        }

        DifferencingParameters parameters = parameterFactory.load(parameterFilePath.toFile());

        // Delete generated files from previous run(s):
        Arrays.stream(parameters.getGeneratedFiles()).forEach(file -> new File(file).delete());

        // Run the differencing:
        PrintStream systemOutput = System.out;
        PrintStream systemError = System.err;

        ByteArrayOutputStream driverOutputBuffer = new ByteArrayOutputStream();
        PrintStream driverOutput = new PrintStream(driverOutputBuffer);
        ByteArrayOutputStream driverErrorBuffer = new ByteArrayOutputStream();
        PrintStream driverError = new PrintStream(driverErrorBuffer);

        String outputPath = parameters.getOutputFile();
        String errorPath = parameters.getErrorFile();

        boolean hasSucceeded = false;
        boolean hasTimedOut = false;

        Benchmark benchmark = new Benchmark(parameters.getBenchmarkName(), parameters.getExpectedResult());
        Run run = new Run(parameters.getBenchmarkName(), parameters.getToolVariant());

        RunRepository.delete(run);
        BenchmarkRepository.insertOrUpdate(benchmark);
        RunRepository.insertOrUpdate(run);

        ExecutionListener v1ExecListener = new ExecutionListener(run, parameters, "*.IoldV" + parameters.getToolName() + ".snippet");
        ExecutionListener v2ExecListener = new ExecutionListener(run, parameters, "*.InewV" + parameters.getToolName() + ".snippet");
        PathConditionListener pcListener = new PathConditionListener(parameters);
        DifferencingListener diffListener = new DifferencingListener(run, parameters);

        long start = System.currentTimeMillis();

        String errors = "";

        try (PrintWriter outputWriter = new PrintWriter(outputPath, "UTF-8")) {
            Thread shutdownHook = new Thread(() -> {
                long finish = System.currentTimeMillis();
                float runtime = (finish - start) / 1000f;

                Classification result = new RunClassifier(
                    false, false, true, false,
                    diffListener.getPartitions()
                ).getClassification();;

                RunRepository.insertOrUpdate(new Run(
                    run.benchmark,
                    run.tool,
                    result,
                    false,
                    diffListener.isDepthLimited(),
                    diffListener.hasUif(),
                    1,
                    runtime,
                    "Program quit unexpectedly."
                ));

                outputWriter.println(driverOutputBuffer);
                outputWriter.println("Program quit unexpectedly.");
                outputWriter.flush();
            });

            try {
                System.setOut(driverOutput);
                System.setErr(driverError);

                Runtime.getRuntime().addShutdownHook(shutdownHook);

                Runnable differencing = () -> {
                    try {
                        File javaFile = this.createDifferencingDriverClass(parameters);
                        this.compile(ProjectPaths.classpath, javaFile);
                        File configFile = this.createDifferencingJpfConfiguration(parameters);

                        Config config = JPF.createConfig(new String[]{configFile.getAbsolutePath()});
                        JPF jpf = new JPF(config);
                        jpf.addListener(v1ExecListener);
                        jpf.addListener(v2ExecListener);
                        jpf.addListener(pcListener);
                        jpf.addListener(diffListener);
                        jpf.run();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

                TimeLimitedCodeBlock.runWithTimeout(differencing, timeout, TimeUnit.SECONDS);

                hasSucceeded = true;
            } catch (TimeoutException e) {
                try (PrintWriter errorWriter = new PrintWriter(errorPath, "UTF-8")) {
                    errors = ExceptionUtils.getStackTrace(e);

                    e.printStackTrace(driverError);

                    errorWriter.println("Differencing failed due to timeout (" + timeout + "s).\n");
                    errorWriter.println(driverErrorBuffer);
                    errorWriter.flush();
                }

                hasTimedOut = true;
            } catch (Exception e) {
                try (PrintWriter errorWriter = new PrintWriter(errorPath, "UTF-8")) {
                    errors = ExceptionUtils.getStackTrace(e);

                    e.printStackTrace(driverError);

                    errorWriter.println("Differencing failed due to error.\n");
                    errorWriter.println(driverErrorBuffer);
                    errorWriter.flush();
                }
            } finally {
                outputWriter.println(driverOutputBuffer);
                outputWriter.flush();

                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
        }

        String status = "SUCCESS";
        if (hasTimedOut) {
            status = "TIMEOUT";
        } else if (!hasSucceeded) {
            status = "FAILURE";
        }

        long finish = System.currentTimeMillis();
        float runtime = (finish - start) / 1000f;

        Classification result = new RunClassifier(
            false, false, !hasTimedOut && !hasSucceeded, hasTimedOut,
            diffListener.getPartitions()
        ).getClassification();;

        RunRepository.insertOrUpdate(new Run(
            run.benchmark,
            run.tool,
            result,
            hasTimedOut,
            diffListener.isDepthLimited(),
            diffListener.hasUif(),
            1,
            runtime,
            errors
        ));

        systemOutput.println(status + ": " + parameters.getTargetDirectory());

        if (!hasSucceeded && !hasTimedOut) {
            systemOutput.println(driverErrorBuffer);
        }
    }

    public File createDifferencingDriverClass(DifferencingParameters parameters) throws IOException, TemplateException {
        /* Create a data-model */
        Map<String, Object> root = new HashMap<>();
        root.put("parameters", parameters);

        /* Get the template (uses cache internally) */
        Template template = this.freeMarkerConfiguration.getTemplate("DifferencingDriverClass.ftl");

        /* Merge data-model with template */
        File file = new File(parameters.getTargetDirectory() + "/" + parameters.getTargetClassName() + ".java");
        file.getParentFile().mkdirs();

        try (PrintWriter writer = new PrintWriter(file)) {
            template.process(root, writer);
        }

        return file;
    }

    public File createDifferencingJpfConfiguration(DifferencingParameters parameters) throws IOException, TemplateException {
        /* Create a data-model */
        Map<String, Object> root = new HashMap<>();
        root.put("parameters", parameters);

        /* Get the template (uses cache internally) */
        Template template = this.freeMarkerConfiguration.getTemplate("DifferencingConfiguration.ftl");

        /* Merge data-model with template */
        File file = new File(parameters.getTargetDirectory() + "/" + parameters.getTargetClassName() + ".jpf");
        file.getParentFile().mkdirs();

        try (PrintWriter writer = new PrintWriter(file)) {
            template.process(root, writer);
        }

        return file;
    }

    // The compile method is copied from ARDiff's equiv.checking.Utils interface.
    private void compile(String classpath,File newFile) throws IOException {
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
