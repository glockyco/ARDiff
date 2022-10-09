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
        "partition, " +
        "result, " +
        "has_uif, " +
        "has_uif_pc, " +
        "has_uif_v1, " +
        "has_uif_v2, " +
        "constraint_count, " +
        "errors" +
        ") " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT DO UPDATE SET " +
        "result = excluded.result, " +
        "has_uif = excluded.has_uif, " +
        "has_uif_pc = excluded.has_uif_pc, " +
        "has_uif_v1 = excluded.has_uif_v1, " +
        "has_uif_v2 = excluded.has_uif_v2, " +
        "constraint_count = excluded.constraint_count, " +
        "errors = excluded.errors";

    private static void insertOrUpdateFull(Partition partition) {
        assert partition.result != null;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(INSERT_OR_UPDATE_FULL)) {
            ps.setObject(1, partition.benchmark);
            ps.setObject(2, partition.tool);
            ps.setObject(3, partition.partition);
            ps.setObject(4, partition.result.toString());
            ps.setObject(5, partition.hasUif);
            ps.setObject(6, partition.hasUifPc);
            ps.setObject(7, partition.hasUifV1);
            ps.setObject(8, partition.hasUifV2);
            ps.setObject(9, partition.constraintCount);
            ps.setObject(10, partition.errors);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String INSERT_OR_UPDATE_PARTIAL = "" +
        "INSERT INTO partition(" +
        "benchmark, " +
        "tool, " +
        "partition " +
        ") " +
        "VALUES (?, ?, ?) " +
        "ON CONFLICT DO NOTHING;";

    private static void insertOrUpdatePartial(Partition partition) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(INSERT_OR_UPDATE_PARTIAL)) {
            ps.setObject(1, partition.benchmark);
            ps.setObject(2, partition.tool);
            ps.setObject(3, partition.partition);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
