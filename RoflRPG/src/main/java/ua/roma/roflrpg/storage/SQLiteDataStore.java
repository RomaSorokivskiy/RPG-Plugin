package ua.roma.roflrpg.storage;

import org.bukkit.plugin.java.JavaPlugin;
import ua.roma.roflrpg.model.PlayerProfile;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public final class SQLiteDataStore {
    private final JavaPlugin plugin;
    private Connection conn;

    public SQLiteDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "rpg.db");
            if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            conn = DriverManager.getConnection(url);

            try (Statement st = conn.createStatement()) {
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS profiles (" +
                    " uuid TEXT PRIMARY KEY," +
                    " name TEXT NOT NULL," +
                    " race TEXT NOT NULL," +
                    " class TEXT NOT NULL," +
                    " level INTEGER NOT NULL," +
                    " xp INTEGER NOT NULL," +
                    " talent_points INTEGER NOT NULL," +
                    " talents TEXT NOT NULL," +
                    " max_mana INTEGER NOT NULL," +
                    " max_stamina INTEGER NOT NULL," +
                    " mana REAL NOT NULL," +
                    " stamina REAL NOT NULL" +
                    ")"
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init SQLite", e);
        }
    }

    public PlayerProfile load(UUID uuid, String name) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM profiles WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    PlayerProfile p = new PlayerProfile(uuid, name);
                    save(p);
                    return p;
                }
                PlayerProfile p = new PlayerProfile(uuid, rs.getString("name"));
                p.raceId(rs.getString("race"));
                p.classId(rs.getString("class"));
                p.level(rs.getInt("level"));
                p.xp(rs.getLong("xp"));
                p.talentPoints(rs.getInt("talent_points"));
                p.talentsCsv(rs.getString("talents"));
                p.maxMana(rs.getInt("max_mana"));
                p.maxStamina(rs.getInt("max_stamina"));
                p.mana(rs.getDouble("mana"));
                p.stamina(rs.getDouble("stamina"));
                p.lastKnownName(name);
                return p;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load profile", e);
        }
    }

    public void save(PlayerProfile p) {
        String sql =
            "INSERT INTO profiles(uuid,name,race,class,level,xp,talent_points,talents,max_mana,max_stamina,mana,stamina) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT(uuid) DO UPDATE SET " +
            " name=excluded.name," +
            " race=excluded.race," +
            " class=excluded.class," +
            " level=excluded.level," +
            " xp=excluded.xp," +
            " talent_points=excluded.talent_points," +
            " talents=excluded.talents," +
            " max_mana=excluded.max_mana," +
            " max_stamina=excluded.max_stamina," +
            " mana=excluded.mana," +
            " stamina=excluded.stamina";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.uuid().toString());
            ps.setString(2, p.lastKnownName());
            ps.setString(3, p.raceId());
            ps.setString(4, p.classId());
            ps.setInt(5, p.level());
            ps.setLong(6, p.xp());
            ps.setInt(7, p.talentPoints());
            ps.setString(8, p.talentsCsv());
            ps.setInt(9, p.maxMana());
            ps.setInt(10, p.maxStamina());
            ps.setDouble(11, p.mana());
            ps.setDouble(12, p.stamina());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save profile", e);
        }
    }

    public void close() {
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
    }
}
