package equiv.checking;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DifferencingRunner {
    private final String[] methodParams;
    private final Map<String, String> variablesNamesTypesMapping;
    private final String declarations;
    private final String path;
    private final String v1ClassName;
    private final String v1MethodDescription;
    private final String v2ClassName;
    private final String v2MethodDescription;
    private final String toolName;
    private final Instrumentation instrumentation;

    private final Configuration freeMarkerConfiguration;

    public DifferencingRunner(
        String[] methodParams,
        Map<String, String> variablesNamesTypesMapping,
        String declarations,
        String path,
        String v1ClassName,
        String v1MethodDescription,
        String v2ClassName,
        String v2MethodDescription,
        String toolName,
        Instrumentation instrumentation
    ) throws IOException {
        this.methodParams = methodParams;
        this.variablesNamesTypesMapping = variablesNamesTypesMapping;
        this.declarations = declarations;
        this.path = path;
        this.v1ClassName = v1ClassName;
        this.v1MethodDescription = v1MethodDescription;
        this.v2ClassName = v2ClassName;
        this.v2MethodDescription = v2MethodDescription;
        this.toolName = toolName;
        this.instrumentation = instrumentation;

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

    public void runDifferencing() throws Exception {
        String symbolicParameters = String.join("#", Collections.nCopies(this.methodParams.length, "sym"));

        String inputParameters = Arrays.stream(this.methodParams)
            .map(p -> Type.getType(variablesNamesTypesMapping.get(p)).getClassName() + " " + p)
            .collect(Collectors.joining(", "));

        String inputVariables = String.join(", ", methodParams);

        String inputValues = Arrays.stream(methodParams)
            .map(p -> this.instrumentation.valueBasedOnType(Type.getType(variablesNamesTypesMapping.get(p)).getClassName()))
            .collect(Collectors.joining(", "));

        DifferencingParameters parameters = new DifferencingParameters(
            this.path,
            this.instrumentation.packageName(),
            "IDiff" + this.toolName,

            symbolicParameters,
            inputParameters,
            inputVariables,
            inputValues,

            this.instrumentation.packageName(),
            this.v1ClassName + this.toolName,
            Type.getMethodType(this.v1MethodDescription).getReturnType().getClassName(),

            this.instrumentation.packageName(),
            this.v2ClassName + this.toolName,
            Type.getMethodType(this.v2MethodDescription).getReturnType().getClassName()
        );

        File javaFile = this.saveDifferencingDriverClass(parameters);
        this.instrumentation.compile(Paths.classpath, javaFile);
        File configFile = this.saveDifferencingJpfConfiguration(parameters);

        Config config = JPF.createConfig(new String[]{configFile.getAbsolutePath()});
        JPF jpf = new JPF(config);
        jpf.addListener(new DifferencingListener(parameters, this.declarations));
        jpf.run();
    }

    public File saveDifferencingDriverClass(DifferencingParameters parameters) throws Exception {
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

    public File saveDifferencingJpfConfiguration(DifferencingParameters parameters) throws Exception {
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
}
