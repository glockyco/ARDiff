package differencing.repositories;

import differencing.models.PartitionInstruction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PartitionInstructionRepository extends Repository {
    private static final String INSERT_OR_UPDATE = "" +
        "INSERT INTO partition_instruction(" +
        "benchmark, " +
        "tool, " +
        "iteration, " +
        "partition, " +
        "version, " +
        "method, " +
        "instruction_index, " +
        "execution_index, " +
        "state, " +
        "choice" +
        ") " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT DO UPDATE SET " +
        "state = excluded.state, " +
        "choice = excluded.choice";

    public static void insertOrUpdate(Iterable<PartitionInstruction> partitionInstructions) {
        for (PartitionInstruction partitionInstruction: partitionInstructions) {
            insertOrUpdate(partitionInstruction);
        }
    }

    public static void insertOrUpdate(PartitionInstruction partitionInstruction) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(INSERT_OR_UPDATE)) {
            ps.setObject(1, partitionInstruction.benchmark);
            ps.setObject(2, partitionInstruction.tool);
            ps.setObject(3, partitionInstruction.iteration);
            ps.setObject(4, partitionInstruction.partition);
            ps.setObject(5, partitionInstruction.version);
            ps.setObject(6, partitionInstruction.method);
            ps.setObject(7, partitionInstruction.instructionIndex);
            ps.setObject(8, partitionInstruction.executionIndex);
            ps.setObject(9, partitionInstruction.state);
            ps.setObject(10, partitionInstruction.choice);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
