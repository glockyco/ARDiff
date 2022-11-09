package differencing;

import differencing.classification.Classification;
import differencing.classification.RunClassifier;
import differencing.models.Benchmark;
import differencing.models.Partition;
import differencing.models.Run;
import differencing.repositories.BenchmarkRepository;
import differencing.repositories.RunRepository;
import equiv.checking.ChangeExtractor;
import equiv.checking.ProjectPaths;
import equiv.checking.SourceInstrumentation;
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

public class DifferencingRunner {
    private final Configuration freeMarkerConfiguration;

    public static void main(String[] args) throws Exception {
        // Arguments: [benchmark] [tool] [solver-timeout]
        // - [benchmark]: Path to the benchmark directory, e.g., ../benchmarks/.
        // - [tool]: SE, DSEs, Imp, ARDiffs, ARDiffR, or ARDiffH3.
        // - [solver-timeout]: Maximum time to use per diff solver query.
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

    public void run(String benchmarkDir, String toolName, int solverTimeout) throws Exception {
        // Read the differencing configuration:
        Path parameterFilePath = Paths.get(benchmarkDir, "instrumented", "IDiff" + toolName + "-Parameters.txt");

        DifferencingParameterFactory parameterFactory = new DifferencingParameterFactory();

        if (!parameterFilePath.toFile().exists()) {
            String error = "Error: '" + parameterFilePath + "' does not exist.";

            DifferencingParameters parameters = parameterFactory.create(toolName, benchmarkDir);
            Arrays.stream(parameters.getGeneratedFiles()).forEach(file -> new File(file).delete());

            Classification result = new RunClassifier(
                false, true, false, false,
                Collections.<Partition>emptySet()
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

        Benchmark benchmark = new Benchmark(parameters.getBenchmarkName(), parameters.getExpectedResult());
        Run run = new Run(parameters.getBenchmarkName(), parameters.getToolVariant());

        RunTimer.start();

        ChangeExtractor changeExtractor = new ChangeExtractor();
        ArrayList<Integer> changes = changeExtractor.obtainChanges(
            parameters.getOldVJavaFile(),
            parameters.getNewVJavaFile(),
            parameters.getTargetDirectory()
        );

        SourceInstrumentation instrumentation = InstrumentationFactory.create(
            toolName,
            solverTimeout,
            changeExtractor
        );

        boolean shouldKeepIterating;

        do { // while (shouldKeepIterating) {
            RunRepository.delete(run);
            BenchmarkRepository.insertOrUpdate(benchmark);
            RunRepository.insertOrUpdate(run);

            ExecutionListener v1ExecListener = new ExecutionListener(run, parameters, "*.IoldV" + parameters.getToolName() + parameters.getIteration() + ".snippet");
            ExecutionListener v2ExecListener = new ExecutionListener(run, parameters, "*.InewV" + parameters.getToolName() + parameters.getIteration() + ".snippet");
            PathConditionListener pcListener = new PathConditionListener(parameters);
            DifferencingListener diffListener = new DifferencingListener(run, parameters, solverTimeout);

            boolean hasSucceeded = false;
            String errors = "";

            try (
                PrintWriter outputWriter = new PrintWriter(outputPath, "UTF-8");
                PrintWriter errorWriter = new PrintWriter(errorPath, "UTF-8")
            ) {
                Thread shutdownHook = new Thread(() -> {
                    Classification result = new RunClassifier(
                        false, false, false, true,
                        diffListener.getPartitions()
                    ).getClassification();

                    RunRepository.insertOrUpdate(new Run(
                        parameters.getBenchmarkName(),
                        parameters.getToolVariant(),
                        result,
                        true,
                        diffListener.isDepthLimited(),
                        diffListener.hasUif(),
                        parameters.getIteration(),
                        RunTimer.getTime(),
                        ""
                    ));

                    outputWriter.println(driverOutputBuffer);
                    outputWriter.println("Forced program shutdown.");
                    outputWriter.flush();

                    systemError.println("TIMEOUT: " + parameters.getTargetDirectory() + " -> " + result);
                    systemError.flush();
                });

                try {
                    System.setOut(driverOutput);
                    System.setErr(driverError);

                    Runtime.getRuntime().addShutdownHook(shutdownHook);

                    instrumentation.runInstrumentation(parameters.getIteration(), changes);

                    File javaFile = this.createDifferencingDriverClass(parameters);
                    this.compile(ProjectPaths.classpath, javaFile);
                    File configFile = this.createDifferencingJpfConfiguration(parameters, solverTimeout);

                    Config config = JPF.createConfig(new String[]{configFile.getAbsolutePath()});
                    JPF jpf = new JPF(config);
                    jpf.addListener(v1ExecListener);
                    jpf.addListener(v2ExecListener);
                    jpf.addListener(pcListener);
                    jpf.addListener(diffListener);
                    jpf.run();

                    hasSucceeded = true;
                } catch (Exception e) {
                    errors = ExceptionUtils.getStackTrace(e);

                    e.printStackTrace(driverError);

                    errorWriter.println("Differencing failed due to error.\n");
                    errorWriter.println(driverErrorBuffer);
                    errorWriter.flush();

                    systemError.println(errors);
                } finally {
                    outputWriter.println(driverOutputBuffer);
                    outputWriter.flush();

                    System.setOut(systemOutput);
                    System.setErr(systemError);

                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                }
            }

            Classification result = new RunClassifier(
                false, false, !hasSucceeded, false,
                diffListener.getPartitions()
            ).getClassification();

            if (hasSucceeded) {
                systemOutput.print("Iteration " + parameters.getIteration() + " - SUCCESS: ");
                systemOutput.println(parameters.getTargetDirectory() + " -> " + result);
            } else {
                systemError.println("Iteration " + parameters.getIteration() + " - ERROR: ");
                systemError.println(parameters.getTargetDirectory() + " -> " + result);
            }

            boolean isFinalResult = result == Classification.EQ || result == Classification.NEQ || result == Classification.ERROR;

            shouldKeepIterating = false;
            if (!isFinalResult) {
                String nextToRefine = instrumentation.getNextToRefine(
                    diffListener.getContext(),
                    diffListener.getV1Summary(),
                    diffListener.getV2Summary(),
                    diffListener.getVariables()
                );

                if (!nextToRefine.isEmpty()) {
                    instrumentation.expandFunction(nextToRefine, changes);
                    shouldKeepIterating = true;
                } else {
                    systemOutput.println("Nothing left to refine.");
                }
            }

            // Creating a new run to ensure we provide all necessary data.
            run = new Run(
                parameters.getBenchmarkName(),
                parameters.getToolVariant(),
                result,
                false,
                diffListener.isDepthLimited(),
                diffListener.hasUif(),
                parameters.getIteration(),
                RunTimer.getTime(),
                errors
            );

            RunRepository.insertOrUpdate(run);

            parameters.startNextIteration();
            diffListener.close();
        } while (shouldKeepIterating);
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

    public File createDifferencingJpfConfiguration(DifferencingParameters parameters, int timeout) throws IOException, TemplateException {
        /* Create a data-model */
        Map<String, Object> root = new HashMap<>();
        root.put("parameters", parameters);
        root.put("timeout", timeout * 1000);

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
