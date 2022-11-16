package differencing;

import differencing.classification.Classification;
import differencing.classification.IterationClassifier;
import differencing.classification.RunClassifier;
import differencing.models.Benchmark;
import differencing.models.Iteration;
import differencing.models.Run;
import differencing.repositories.BenchmarkRepository;
import differencing.repositories.IterationRepository;
import differencing.repositories.RunRepository;
import differencing.repositories.TimeRepository;
import equiv.checking.ChangeExtractor;
import equiv.checking.ProjectPaths;
import equiv.checking.SourceInstrumentation;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.symbc.SymbolicListener;
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

        //------------------------------------------------------------------------------------------
        // Check whether the necessary "configuration" file exists.
        // If not, terminate the program with a BASE_TOOL_MISSING result.

        if (!parameterFilePath.toFile().exists()) {
            String error = "Error: '" + parameterFilePath + "' does not exist.";

            DifferencingParameters parameters = parameterFactory.create(toolName, benchmarkDir);
            Arrays.stream(parameters.getGeneratedFiles()).forEach(file -> new File(file).delete());

            Benchmark benchmark = new Benchmark(parameters.getBenchmarkName(), parameters.getExpectedResult());

            Run run = new Run(
                parameters.getBenchmarkName(),
                parameters.getToolVariant(),
                Classification.BASE_TOOL_MISSING,
                null, null, null, null, null, null,
                error
            );

            RunRepository.delete(run);
            BenchmarkRepository.insertOrUpdate(benchmark);
            RunRepository.insertOrUpdate(run);

            System.out.println(error);
            return;
        }

        //------------------------------------------------------------------------------------------

        DifferencingParameters parameters = parameterFactory.load(parameterFilePath.toFile());

        Arrays.stream(parameters.getGeneratedFiles()).forEach(file -> new File(file).delete());

        StopWatches.start("run");
        StopWatches.start("run:initialization");

        Benchmark benchmark = new Benchmark(parameters.getBenchmarkName(), parameters.getExpectedResult());
        BenchmarkRepository.insertOrUpdate(benchmark);

        Run run = new Run(parameters.getBenchmarkName(), parameters.getToolVariant());
        RunRepository.delete(run);
        RunRepository.insertOrUpdate(run);

        Map<Integer, Iteration> iterations = new HashMap<>();
        Map<Integer, DifferencingListener> diffListeners = new HashMap<>();

        //----------------------------------------------------------------------
        // "Redirect" output to log files.

        PrintStream systemOutput = System.out;
        PrintStream systemError = System.err;

        PrintStream outputStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(parameters.getOutputFile())));
        PrintStream errorStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(parameters.getErrorFile())));

        System.setOut(outputStream);
        System.setErr(errorStream);

        //------------------------------------------------------------------
        // Create a shutdown handler in case the run / iteration is
        // forcefully stopped because it exceeds the timeout.

        Thread shutdownHook = new Thread(() -> {
            try {
                Iteration currentIteration = iterations.get(iterations.size());
                DifferencingListener diffListener = diffListeners.get(currentIteration.iteration);

                Iteration finishedIteration = this.finalizeIteration(currentIteration, diffListener, true, false, "");
                iterations.put(finishedIteration.iteration, finishedIteration);

                Run finishedRun = this.finalizeRun(iterations, true, false, "");

                TimeRepository.insertOrUpdate(TimeFactory.create(finishedRun, StopWatches.getTimes()));

                systemError.println("TIMEOUT: " + parameters.getTargetDirectory() + " -> " + finishedRun.result);
            } catch (Exception e) {
                e.printStackTrace(systemError);
                e.printStackTrace(errorStream);
                throw e;
            }
        });

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        //----------------------------------------------------------------------

        try {
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

            StopWatches.stop("run:initialization");

            boolean shouldKeepIterating;

            do { // while (shouldKeepIterating)
                StopWatches.start("iteration-" + (iterations.size() + 1));
                StopWatches.start("iteration-" + (iterations.size() + 1) + ":initialization");

                Iteration iteration = new Iteration(run.benchmark, run.tool, iterations.size() + 1);
                iterations.put(iteration.iteration, iteration);

                IterationRepository.insertOrUpdate(iteration);

                parameters.setIteration(iteration.iteration);

                IgnoreUnreachablePathsListener unreachableListener = new IgnoreUnreachablePathsListener(solverTimeout);
                ExecutionListener v1ExecListener = new ExecutionListener(iteration, parameters, "*.IoldV" + parameters.getToolName() + iteration.iteration + ".snippet");
                ExecutionListener v2ExecListener = new ExecutionListener(iteration, parameters, "*.InewV" + parameters.getToolName() + iteration.iteration + ".snippet");
                PathConditionListener pcListener = new PathConditionListener(iteration, parameters);
                DifferencingListener diffListener = new DifferencingListener(iteration, parameters, solverTimeout);

                diffListeners.put(iteration.iteration, diffListener);
                if (diffListeners.containsKey(iteration.iteration - 1)) {
                    diffListeners.get(iteration.iteration - 1).close();
                    diffListeners.remove(iteration.iteration - 1);
                }

                //--------------------------------------------------------------
                // Execute the actual equivalence checking / differencing.
                // Each iteration consists of a source code instrumentation
                // step and a symbolic execution step.

                boolean hasSucceeded = false;
                String errors = "";

                try {
                    StopWatches.stop("iteration-" + iteration.iteration + ":initialization");
                    StopWatches.start("iteration-" + iteration.iteration + ":instrumentation");

                    instrumentation.runInstrumentation(iteration.iteration, changes);

                    StopWatches.stop("iteration-" + iteration.iteration + ":instrumentation");
                    StopWatches.start("iteration-" + iteration.iteration + ":symbolic-execution");

                    File javaFile = this.createDifferencingDriverClass(parameters);
                    this.compile(ProjectPaths.classpath, javaFile);
                    File configFile = this.createDifferencingJpfConfiguration(parameters, solverTimeout);

                    Config config = JPF.createConfig(new String[]{configFile.getAbsolutePath()});
                    JPF jpf = new JPF(config);
                    jpf.addListener(unreachableListener);
                    jpf.addListener(new SymbolicListener(config, jpf));
                    jpf.addListener(v1ExecListener);
                    jpf.addListener(v2ExecListener);
                    jpf.addListener(pcListener);
                    jpf.addListener(diffListener);
                    jpf.run();

                    hasSucceeded = true;
                } catch (Exception e) {
                    errors = ExceptionUtils.getStackTrace(e);
                    e.printStackTrace(systemError);
                    e.printStackTrace(errorStream);
                } finally {
                    StopWatches.stop("iteration-" + iteration.iteration + ":symbolic-execution");
                }

                //------------------------------------------------------------------
                // Write the results of this iteration to the DB + console.

                StopWatches.start("iteration-" + iteration.iteration + ":classification");

                iteration = this.finalizeIteration(iteration, diffListener, false, !hasSucceeded, errors);
                iterations.put(iteration.iteration, iteration);

                if (hasSucceeded) {
                    systemOutput.print("Iteration " + iteration.iteration + " - SUCCESS: ");
                    systemOutput.println(parameters.getTargetDirectory() + " -> " + iteration.result);
                } else {
                    systemError.println("Iteration " + iteration.iteration + " - ERROR: ");
                    systemError.println(parameters.getTargetDirectory() + " -> " + iteration.result);
                }

                StopWatches.stop("iteration-" + iteration.iteration + ":classification");

                //------------------------------------------------------------------
                // Check if we should do another iteration.
                // If yes, update the instrumentation object accordingly so a
                // more concretized version of the source code is generated by
                // the instrumentation step of the next iteration.

                StopWatches.start("iteration-" + iteration.iteration + ":refinement");

                boolean isFinalResult =
                    iteration.result == Classification.EQ
                        || iteration.result == Classification.NEQ
                        || iteration.result == Classification.ERROR;

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

                StopWatches.stop("iteration-" + iteration.iteration + ":refinement");
                StopWatches.stop("iteration-" + iteration.iteration);
            } while (shouldKeepIterating);

            //----------------------------------------------------------------------
            // Write the overall run results to the DB.

            StopWatches.start("run:finalization");

            Run finishedRun = this.finalizeRun(iterations, false, false, "");

            StopWatches.stop("run:finalization");
            StopWatches.stop("run");

            TimeRepository.insertOrUpdate(TimeFactory.create(finishedRun, StopWatches.getTimes()));

            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (Exception e) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);

            e.printStackTrace(systemError);
            e.printStackTrace(errorStream);

            try {
                Run finishedRun = this.finalizeRun(iterations, false, true, ExceptionUtils.getStackTrace(e));

                systemError.println("ERROR: " + parameters.getTargetDirectory() + " -> " + finishedRun.result);
            } catch (Exception ex) {
                ex.printStackTrace(systemError);
                ex.printStackTrace(errorStream);
            }
        }
    }

    public Iteration finalizeIteration(
        Iteration iteration,
        DifferencingListener diffListener,
        boolean hasTimedOut,
        boolean isError,
        String errors
    ) {
        Classification result = new IterationClassifier(
            false, false, isError, hasTimedOut,
            diffListener.getPartitions()
        ).getClassification();

        Iteration finishedIteration = new Iteration(
            iteration.benchmark,
            iteration.tool,
            iteration.iteration,
            result,
            hasTimedOut,
            diffListener.isDepthLimited(),
            diffListener.hasUif(),
            diffListener.getPartitions().size(),
            StopWatches.getTime("iteration-" + iteration.iteration),
            errors
        );

        IterationRepository.insertOrUpdate(finishedIteration);

        return finishedIteration;
    }

    public Run finalizeRun(
        Map<Integer, Iteration> iterations,
        boolean hasTimedOut,
        boolean isError,
        String errors
    ) {
        RunClassifier runClassifier = new RunClassifier(iterations);
        Iteration resultIteration = runClassifier.getClassificationIteration();
        Iteration lastIteration = iterations.get(iterations.size());

        Run run = new Run(
            resultIteration.benchmark,
            resultIteration.tool,
            isError ? Classification.ERROR : resultIteration.result,
            hasTimedOut || lastIteration.hasTimedOut,
            resultIteration.isDepthLimited,
            resultIteration.hasUif,
            lastIteration.iteration,
            resultIteration.iteration,
            StopWatches.getTime("run"),
            errors + lastIteration.errors
        );

        RunRepository.insertOrUpdate(run);

        return run;
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
