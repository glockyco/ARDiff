package SE;

import br.usp.each.saeg.asm.defuse.Variable;
import com.microsoft.z3.Status;
import differencing.DifferencingParameterFactory;
import differencing.DifferencingParameters;
import equiv.checking.*;
import equiv.checking.SymbolicExecutionRunner.SMTSummary;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SE {
    protected String path;
    protected String methodPath1, methodPath2;
    protected String classPath1, classPath2;
    protected long[] times = new long[5], totalTimes = new long[5];
    protected int bound;
    protected int timeout;
    protected String toolName;
    protected String SMTSolver;
    protected int minInt, maxInt;
    protected double minDouble, maxDouble;
    protected long minLong, maxLong;
    protected boolean parseFromSMTLib;
    protected boolean ranByUser = false;
    protected boolean Z3_TERMINAL = false;

    public SE(
        String path,
        String path1,
        String path2,
        int bound,
        int timeout,
        String tool,
        String SMTSolver,
        int minInt,
        int maxInt,
        double minDouble,
        double maxDouble,
        long minLong,
        long maxLong,
        boolean parseFromSMTLib,
        boolean ranByUser,
        boolean z3_TERMINAL
    ) {
        this.methodPath1 = path1;
        this.methodPath2 = path2;
        this.path = path;
        this.bound = bound;
        this.timeout = timeout;
        this.toolName = tool;
        this.SMTSolver = SMTSolver;
        this.minInt = minInt;
        this.maxInt = maxInt;
        this.minDouble = minDouble;
        this.maxDouble = maxDouble;
        this.minLong = minLong;
        this.maxLong = maxLong;
        this.parseFromSMTLib = parseFromSMTLib;
        this.ranByUser = ranByUser;
        this.Z3_TERMINAL = z3_TERMINAL;
    }

    public void setPathToDummy(String classpath) {
        if (ranByUser) {
            this.path = this.path + "instrumented";
            int index = this.methodPath1.lastIndexOf("/");
            int index2 = this.methodPath2.lastIndexOf("/");
            String package1 = this.methodPath1.substring(index + 1);
            String package2 = this.methodPath2.substring(index2 + 1);
            this.methodPath1 = this.path + "/" + package1;
            this.methodPath2 = this.path + "/" + package2;
            this.classPath1 = "target/classes/" + classpath + "/" + package1.split("\\.")[0] + ".class";
            this.classPath2 = "target/classes/" + classpath + "/" + package2.split("\\.")[0] + ".class";
        } else {
            this.path += "instrumented";
            int index = this.methodPath1.lastIndexOf("/");
            this.methodPath1 = this.path + this.methodPath1.substring(index);
            this.methodPath2 = this.path + this.methodPath2.substring(index);
            String package1 = this.methodPath1.split("benchmarks/")[1].split("\\.")[0];
            String package2 = this.methodPath2.split("benchmarks/")[1].split("\\.")[0];
            String classPath1 = "target/classes/demo/benchmarks/" + package1 + ".class";
            String classPath2 = "target/classes/demo/benchmarks/" + package2 + ".class";
            this.methodPath1 = classPath1;
            this.methodPath2 = classPath2;
        }
    }

    public SMTSummary runTool() throws Exception {
        Path benchmarkPath = Paths.get(this.path);
        try {
            ChangeExtractor changeExtractor = new ChangeExtractor();
            String path = this.ranByUser ? this.path + "instrumented" : this.path;
            changeExtractor.obtainChanges(this.methodPath1, this.methodPath2, ranByUser, path);
            this.setPathToDummy(changeExtractor.getClasspath());

            SMTSummary summary = this.runEquivalenceChecking();
            String result = this.equivalenceResult(summary);

            System.out.println(result);

            String outputs = this.path.split("instrumented")[0];

            Path resultPath = Paths.get(outputs + "outputs/" + this.toolName + ".txt");
            resultPath.getParent().toFile().mkdirs();
            Files.write(resultPath, result.getBytes());

            Path modelsPath = Paths.get(outputs + "z3models/" + this.toolName + ".txt");
            modelsPath.getParent().toFile().mkdirs();
            Files.write(modelsPath, summary.toWrite.getBytes());

            summary.isDepthLimited = OutputClassifier.isDepthLimited(benchmarkPath, this.toolName);
            summary.classification = OutputClassifier.classify(benchmarkPath, this.toolName);

            return summary;
        } catch (Exception e) {
            System.out.println("An error/exception occurred when instrumenting the files or running the equivalence checking. Please report this issue to us.\n\n");
            e.printStackTrace();
            throw e;
        }
    }

    public SMTSummary runEquivalenceChecking() throws Exception {
        long start = System.nanoTime();

        ClassNode classNode1 = new ClassNode();
        ClassReader classReader1 = new ClassReader(new FileInputStream(this.classPath1));
        classReader1.accept(classNode1, 0);
        List<MethodNode> methods1 = classNode1.methods;
        MethodNode method1 = methods1.get(1); // method 0 is by default the "init" method

        ClassNode classNode2 = new ClassNode();
        ClassReader classReader2 = new ClassReader(new FileInputStream(this.classPath2));
        classReader2.accept(classNode2, 0);
        List<MethodNode> Methods2 = classNode2.methods;
        MethodNode method2 = Methods2.get(1); // method 0 is by default the "init" method

        String v1ClassName = "I" + classNode1.name.substring(classNode1.name.lastIndexOf("/") + 1);
        String v2ClassName = "I" + classNode2.name.substring(classNode2.name.lastIndexOf("/") + 1);

        long end = System.nanoTime();
        this.times[0] = end - start;
        this.totalTimes[0] += this.times[0];
        start = System.nanoTime();

        /*****Generating the main methods of each class ******/
        DefUseExtractor def = new DefUseExtractor();
        Variable[] variables = def.getVariables(method1);
        String[] methodParams = def.extractParams(method1);
        String[] constructorParams = def.extractParamsConstructor(methods1.get(0));
        Map<String, String> variablesNamesTypesMapping = def.getVariableTypesMapping();

        Instrumentation instrument = new Instrumentation(this.path, this.toolName);

        String mainMethod1 = instrument.getMainProcedure(v1ClassName, method1.name, methodParams, constructorParams, variablesNamesTypesMapping);
        String mainMethod2 = instrument.getMainProcedure(v2ClassName, method2.name, methodParams, constructorParams, variablesNamesTypesMapping);

        /**************Creating the new class files and the modified procedures ***************/
        instrument.setMethods(methods1);
        instrument.saveNewProcedure(this.methodPath1, v1ClassName, new ArrayList<>(), new HashMap<>(), mainMethod1);

        instrument.setMethods(Methods2);
        instrument.saveNewProcedure(this.methodPath2, v2ClassName, new ArrayList<>(), new HashMap<>(), mainMethod2);

        end = System.nanoTime();
        this.times[1] = end - start;
        this.totalTimes[1] += this.times[1];

        /**********************Creating the the differencing parameters ******************/

        DifferencingParameterFactory factory = new DifferencingParameterFactory();

        DifferencingParameters parameters = factory.create(
            this.toolName,
            this.path,
            instrument.packageName(),
            v1ClassName,
            v2ClassName,
            method1.desc,
            methodParams,
            variablesNamesTypesMapping
        );

        Path filepath = Paths.get(parameters.getParameterFile());
        factory.persist(filepath.toFile(), parameters);

        /**********************Running the symbolic execution ******************/

        start = System.nanoTime();

        SymbolicExecutionRunner symbEx = new SymbolicExecutionRunner(
            this.path,
            instrument.packageName(),
            v1ClassName + this.toolName,
            v2ClassName + this.toolName,
            method2.name,
            methodParams.length,
            this.bound,
            this.timeout,
            this.SMTSolver,
            this.minInt,
            this.maxInt,
            this.minDouble,
            this.maxDouble,
            this.minLong,
            this.maxLong,
            this.parseFromSMTLib,
            this.Z3_TERMINAL
        );

        symbEx.creatingJpfFiles();
        symbEx.runningJavaPathFinder();

        end = System.nanoTime();
        this.times[2] = end - start;
        this.totalTimes[2] += this.times[2];
        start = System.nanoTime();

        SMTSummary summary = symbEx.createSMTSummary();

        end = System.nanoTime();
        this.times[3] = end - start;
        this.totalTimes[3] += this.times[3];

        this.times[4] = symbEx.z3time;
        this.totalTimes[4] += this.times[4];

        return summary;
    }

    /**
     * This function outputs the result of equivalence checking based on the input
     *
     * @param smtSummary the summary of the runs
     * @return the final output
     * @throws IOException
     */
    public String equivalenceResult(SMTSummary smtSummary) throws IOException {
        //check the status here
        String result = "-----------------------Results-------------------------------------------\n";
        result += "  -Def-use and uninterpreted functions : " + this.times[1] / (Math.pow(10, 6)) + "\n";
        result += "  -Symbolic execution  : " + this.times[2] / (Math.pow(10, 6)) + " ms\n";
        result += "  -Creating Z3 expressions  : " + this.times[3] / (Math.pow(10, 6)) + " ms\n";
        result += "  -Constraint solving : " + this.times[4] / (Math.pow(10, 6)) + " ms\n";

        if (smtSummary.status == Status.UNSATISFIABLE) {
            result += "Output : EQUIVALENT";
        } else if (smtSummary.status == Status.SATISFIABLE) {
            result += "Output : NOT EQUIVALENT";
        } else if (smtSummary.status == Status.UNKNOWN) {
            result += "Output : UNKNOWN \n";
            result += "Reason: " + smtSummary.reasonUnknown;
        } else {
            throw new RuntimeException("Unknown solver status '" + smtSummary.status + "'.");
        }

        smtSummary.context.close();
        result += "\n-----------------------END-------------------------------------------\n";
        return result;
    }
}
