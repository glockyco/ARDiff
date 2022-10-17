package differencing;

import com.microsoft.z3.*;
import differencing.domain.Model;
import differencing.transformer.ModelToZ3Transformer;

import java.util.HashMap;
import java.util.Map;

public class SatisfiabilityChecker {
    private final Map<String, String> settings = new HashMap<>();

    public SatisfiabilityChecker(int timeout) {
        this.settings.put("timeout", Integer.toString(timeout));
    }

    public Status checkPc(Model pcModel) {
        try (Context context = new Context(this.settings)) {
            ModelToZ3Transformer modelToZ3 = new ModelToZ3Transformer(context);
            Expr<BoolSort> pcExpr = (Expr<BoolSort>) modelToZ3.transform(pcModel);

            Solver pcSolver = context.mkSolver();
            pcSolver.add(pcExpr);
            return this.check(pcSolver);
        }
    }

    public Status checkNeq(Model pcModel, Model v1Model, Model v2Model) {
        try (Context context = new Context(this.settings)) {
            ModelToZ3Transformer modelToZ3 = new ModelToZ3Transformer(context);
            Expr<BoolSort> pcExpr = (Expr<BoolSort>) modelToZ3.transform(pcModel);
            Expr<?> v1Expr = modelToZ3.transform(v1Model);
            Expr<?> v2Expr = modelToZ3.transform(v2Model);

            Solver neqSolver = context.mkSolver();
            neqSolver.add(pcExpr);
            neqSolver.add(context.mkNot(context.mkEq(v1Expr, v2Expr)));
            return this.check(neqSolver);
        }
    }

    public Status checkEq(Model pcModel, Model v1Model, Model v2Model) {
        try (Context context = new Context(this.settings)) {
            ModelToZ3Transformer modelToZ3 = new ModelToZ3Transformer(context);
            Expr<BoolSort> pcExpr = (Expr<BoolSort>) modelToZ3.transform(pcModel);
            Expr<?> v1Expr = modelToZ3.transform(v1Model);
            Expr<?> v2Expr = modelToZ3.transform(v2Model);

            Solver eqSolver = context.mkSolver();
            eqSolver.add(pcExpr);
            eqSolver.add(context.mkEq(v1Expr, v2Expr));
            return this.check(eqSolver);
        } catch (UnsupportedOperationException e) {
            return Status.UNKNOWN;
        }
    }

    private Status check(Solver solver) {
        // @TODO: This function only exists to remove function declarations
        //    that overwrite built-in z3 functions such as:
        //    (declare-fun sin (Real) Real)
        //    from the z3 queries.
        //
        // We do this because sin, cos, etc. already exist as built-in functions
        // of z3, so if the query includes new declarations for those functions,
        // it treats them as uninterpreted functions instead, thus ignoring the
        // built-in definitions of the functions.
        //
        // Ideally, the function declarations shouldn't be added to the query
        // in the first place. However, the Java API for z3 doesn't appear to
        // offer a way to create function applications (e.g., sin(...)) without
        // also providing declarations for the functions
        //
        // If there is a way to accomplish this (i.e., to create function
        // applications without declarations), change ModelToZ3Transformer
        // accordingly (i.e., replace this.context.mkApp in the 'case SIN'
        // switch branch) and call solver.check() directly, rather than
        // recreating the solver without the unwanted declarations here.
        try (Context context = new Context(this.settings)) {
            String query = solver.toString();
            // @TODO: We should also remove the log declaration from the query.
            //    However, ARDiff doesn't do this (i.e., it also defines the
            //    log function as an uninterpreted function) so to keep our
            //    results comparable with ARDiff, we're using the "wrong"
            //    definition as well.
            query = query.replaceAll("\\(declare-fun (?:sin|cos|tan|asin|acos|atan2|atan) \\(Real\\) Real\\)", "");

            Solver s = context.mkSolver();
            s.fromString(query);
            return s.check();
        }
    }
}
