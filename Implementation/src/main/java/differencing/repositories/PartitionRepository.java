package differencing.repositories;

import differencing.models.Partition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PartitionRepository extends Repository {
    public static void insertOrUpdate(Iterable<Partition> partitions) {
        for (Partition partition: partitions) {
            insertOrUpdate(partition);
        }
    }

    public static void insertOrUpdate(Partition partition) {
        if (partition.result != null) {
            insertOrUpdateFull(partition);
        } else {
            insertOrUpdatePartial(partition);
        }
    }

    private static final String INSERT_OR_UPDATE_FULL = "" +
        "INSERT INTO partition(" +
        "benchmark, " +
        "tool, " +
        "iteration, " +
        "partition, " +
        "result, " +
        "pc_status, " +
        "pc_model, " +
        "pc_reason_unknown, " +
        "pc_statistics, " +
        "neq_status, " +
        "neq_model, " +
        "neq_reason_unknown, " +
        "neq_statistics, " +
        "eq_status, " +
        "eq_model, " +
        "eq_reason_unknown, " +
        "eq_statistics, " +
        "has_uif, " +
        "has_uif_pc, " +
        "has_uif_v1, " +
        "has_uif_v2, " +
        "constraint_count, " +
        "runtime, " +
        "errors" +
        ") " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT DO UPDATE SET " +
        "result = excluded.result, " +
        "pc_status = excluded.pc_status, " +
        "pc_model = excluded.pc_model, " +
        "pc_reason_unknown = excluded.pc_reason_unknown, " +
        "pc_statistics = excluded.pc_statistics, " +
        "neq_status = excluded.neq_status, " +
        "neq_model = excluded.neq_model, " +
        "neq_reason_unknown = excluded.neq_reason_unknown, " +
        "neq_statistics = excluded.neq_statistics, " +
        "eq_status = excluded.eq_status, " +
        "eq_model = excluded.eq_model, " +
        "eq_reason_unknown = excluded.eq_reason_unknown, " +
        "eq_statistics = excluded.eq_statistics, " +
        "has_uif = excluded.has_uif, " +
        "has_uif_pc = excluded.has_uif_pc, " +
        "has_uif_v1 = excluded.has_uif_v1, " +
        "has_uif_v2 = excluded.has_uif_v2, " +
        "constraint_count = excluded.constraint_count, " +
        "runtime = excluded.runtime, " +
        "errors = excluded.errors";

    private static void insertOrUpdateFull(Partition partition) {
        assert partition.result != null;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(INSERT_OR_UPDATE_FULL)) {
            ps.setObject(1, partition.benchmark);
            ps.setObject(2, partition.tool);
            ps.setObject(3, partition.iteration);
            ps.setObject(4, partition.partition);
            ps.setObject(5, partition.result.toString());
            ps.setObject(6, partition.pcResult == null ? null : partition.pcResult.status.toInt());
            ps.setObject(7, partition.pcResult == null ? null : partition.pcResult.model);
            ps.setObject(8, partition.pcResult == null ? null : partition.pcResult.reasonUnknown);
            ps.setObject(9, partition.pcResult == null ? null : partition.pcResult.statistics);
            ps.setObject(10, partition.neqResult == null ? null : partition.neqResult.status.toInt());
            ps.setObject(11, partition.neqResult == null ? null : partition.neqResult.model);
            ps.setObject(12, partition.neqResult == null ? null : partition.neqResult.reasonUnknown);
            ps.setObject(13, partition.neqResult == null ? null : partition.neqResult.statistics);
            ps.setObject(14, partition.eqResult == null ? null : partition.eqResult.status.toInt());
            ps.setObject(15, partition.eqResult == null ? null : partition.eqResult.model);
            ps.setObject(16, partition.eqResult == null ? null : partition.eqResult.reasonUnknown);
            ps.setObject(17, partition.eqResult == null ? null : partition.eqResult.statistics);
            ps.setObject(18, partition.hasUif);
            ps.setObject(19, partition.hasUifPc);
            ps.setObject(20, partition.hasUifV1);
            ps.setObject(21, partition.hasUifV2);
            ps.setObject(22, partition.constraintCount);
            ps.setObject(23, partition.runtime);
            ps.setObject(24, partition.errors);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String INSERT_OR_UPDATE_PARTIAL = "" +
        "INSERT INTO partition(" +
        "benchmark, " +
        "tool, " +
        "iteration, " +
        "partition " +
        ") " +
        "VALUES (?, ?, ?, ?) " +
        "ON CONFLICT DO NOTHING;";

    private static void insertOrUpdatePartial(Partition partition) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(INSERT_OR_UPDATE_PARTIAL)) {
            ps.setObject(1, partition.benchmark);
            ps.setObject(2, partition.tool);
            ps.setObject(3, partition.iteration);
            ps.setObject(4, partition.partition);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
