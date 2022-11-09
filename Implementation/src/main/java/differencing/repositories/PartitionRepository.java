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
        "neq_status, " +
        "eq_status, " +
        "has_uif, " +
        "has_uif_pc, " +
        "has_uif_v1, " +
        "has_uif_v2, " +
        "constraint_count, " +
        "runtime, " +
        "errors" +
        ") " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT DO UPDATE SET " +
        "result = excluded.result, " +
        "pc_status = excluded.pc_status, " +
        "neq_status = excluded.neq_status, " +
        "eq_status = excluded.eq_status, " +
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
            ps.setObject(6, partition.pcStatus == null ? null : partition.pcStatus.toInt());
            ps.setObject(7, partition.neqStatus == null ? null : partition.neqStatus.toInt());
            ps.setObject(8, partition.eqStatus == null ? null : partition.eqStatus.toInt());
            ps.setObject(9, partition.hasUif);
            ps.setObject(10, partition.hasUifPc);
            ps.setObject(11, partition.hasUifV1);
            ps.setObject(12, partition.hasUifV2);
            ps.setObject(13, partition.constraintCount);
            ps.setObject(14, partition.runtime);
            ps.setObject(15, partition.errors);
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
