package equiv.checking.repositories;

import equiv.checking.models.Run;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RunRepository extends Repository {
    private static final String INSERT_OR_UPDATE = "" +
        "INSERT INTO run(" +
        "benchmark, " +
        "tool, " +
        "result, " +
        "has_succeeded, " +
        "is_depth_limited, " +
        "has_uif, " +
        "iteration_count, " +
        "runtime, " +
        "errors" +
        ") " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT DO UPDATE SET " +
        "result = excluded.result, " +
        "has_succeeded = excluded.has_succeeded, " +
        "is_depth_limited = excluded.is_depth_limited, " +
        "has_uif = excluded.has_uif, " +
        "iteration_count = excluded.iteration_count, " +
        "runtime = excluded.runtime, " +
        "errors = excluded.errors";

    public static void insertOrUpdate(Run run) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(INSERT_OR_UPDATE)) {
            ps.setObject(1, run.benchmark);
            ps.setObject(2, run.tool);
            ps.setObject(3, run.result);
            ps.setObject(4, run.hasSucceeded);
            ps.setObject(5, run.isDepthLimited);
            ps.setObject(6, run.hasUif);
            ps.setObject(7, run.iterationCount);
            ps.setObject(8, run.runtime);
            ps.setObject(9, run.errors);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
