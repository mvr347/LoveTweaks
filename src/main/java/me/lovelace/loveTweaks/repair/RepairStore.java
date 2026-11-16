package me.lovelace.loveTweaks.repair;

import org.bukkit.inventory.ItemStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Each mutation commits before the GUI accepts another action. Never store item maps that lose components. */
public class RepairStore implements AutoCloseable {
    private final Connection connection;
    public RepairStore(Path path) throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA busy_timeout=1000");
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("CREATE TABLE IF NOT EXISTS repair_orders (owner TEXT PRIMARY KEY, paid INTEGER NOT NULL, ready_at INTEGER NOT NULL, price INTEGER NOT NULL, items BLOB NOT NULL)");
        }
    }
    public Map<UUID, RepairOrder> load() throws SQLException {
        Map<UUID, RepairOrder> result = new HashMap<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM repair_orders")) {
            while (rs.next()) result.put(UUID.fromString(rs.getString("owner")),
                    new RepairOrder(Arrays.asList(ItemStack.deserializeItemsFromBytes(rs.getBytes("items"))),
                            rs.getBoolean("paid"), rs.getLong("ready_at"), rs.getLong("price")));
        }
        return result;
    }
    public void save(UUID owner, RepairOrder order) throws SQLException {
        if (order.emptyItems()) {
            try (PreparedStatement s = connection.prepareStatement("DELETE FROM repair_orders WHERE owner=?")) {
                s.setString(1, owner.toString()); s.executeUpdate();
            }
            return;
        }
        try (PreparedStatement s = connection.prepareStatement("INSERT INTO repair_orders(owner,paid,ready_at,price,items) VALUES(?,?,?,?,?) ON CONFLICT(owner) DO UPDATE SET paid=excluded.paid,ready_at=excluded.ready_at,price=excluded.price,items=excluded.items")) {
            s.setString(1, owner.toString()); s.setBoolean(2, order.paid());
            s.setLong(3, order.readyAt()); s.setLong(4, order.price());
            s.setBytes(5, ItemStack.serializeItemsAsBytes(order.items())); s.executeUpdate();
        }
    }
    @Override public void close() throws SQLException { connection.close(); }
}
