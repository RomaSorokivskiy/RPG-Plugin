package ua.roma.roflrpg.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerProfile {
    private final UUID uuid;
    private String lastKnownName;

    private String raceId = "human";
    private String classId = "guest";

    private int level = 1;
    private long xp = 0;

    private int talentPoints = 0;
    /** Normal talent tree: talentId -> rank (>=1 means unlocked). */
    private final Map<String, Integer> talentRanks = new HashMap<>();

    /**
     * Reserved for the future "separate branch" plugin.
     * External plugin will unlock branch IDs here (e.g. after killing a mob).
     */
    private final Set<String> unlockedBranches = new HashSet<>();

    private int maxMana = 100;
    private int maxStamina = 100;
    private double mana = 100;
    private double stamina = 100;

    public PlayerProfile(UUID uuid, String lastKnownName) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName == null ? "" : lastKnownName;
    }

    public UUID uuid() { return uuid; }
    public String lastKnownName() { return lastKnownName; }
    public void lastKnownName(String n) { this.lastKnownName = n == null ? "" : n; }

    public String raceId() { return raceId; }
    public void raceId(String id) { this.raceId = id == null ? "human" : id; }

    public String classId() { return classId; }
    public void classId(String id) { this.classId = id == null ? "guest" : id; }

    public int level() { return level; }
    public void level(int v) { this.level = Math.max(1, v); }

    public long xp() { return xp; }
    public void xp(long v) { this.xp = Math.max(0, v); }

    public int talentPoints() { return talentPoints; }
    public void talentPoints(int v) { this.talentPoints = Math.max(0, v); }

    public int talentRank(String id) { return talentRanks.getOrDefault(id, 0); }
    public boolean hasTalent(String id) { return talentRank(id) > 0; }
    public Map<String, Integer> talentRanks() { return Collections.unmodifiableMap(talentRanks); }

    public void setTalentRank(String id, int rank) {
        if (id == null || id.isBlank()) return;
        if (rank <= 0) talentRanks.remove(id);
        else talentRanks.put(id, rank);
    }

    public void incTalentRank(String id) {
        setTalentRank(id, talentRank(id) + 1);
    }

    public void clearTalents() { talentRanks.clear(); }

    public boolean hasBranch(String id) { return unlockedBranches.contains(id); }
    public Set<String> unlockedBranches() { return Collections.unmodifiableSet(unlockedBranches); }
    public void unlockBranch(String id) { if (id != null && !id.isBlank()) unlockedBranches.add(id); }

    public int maxMana() { return maxMana; }
    public void maxMana(int v) { maxMana = Math.max(0, v); mana = Math.min(mana, maxMana); }

    public int maxStamina() { return maxStamina; }
    public void maxStamina(int v) { maxStamina = Math.max(0, v); stamina = Math.min(stamina, maxStamina); }

    public double mana() { return mana; }
    public void mana(double v) { mana = clamp(v, 0, maxMana); }

    public double stamina() { return stamina; }
    public void stamina(double v) { stamina = clamp(v, 0, maxStamina); }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public String talentsCsv() {
        if (talentRanks.isEmpty() && unlockedBranches.isEmpty()) return "";

        // Keep it stable and backward-compatible.
        // Format tokens:
        //  - talentId           (legacy, treated as rank 1)
        //  - talentId:rank      (rank >= 1)
        //  - branch:branchId    (reserved for the separate branch plugin)

        java.util.List<String> out = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> e : talentRanks.entrySet()) {
            int r = e.getValue() == null ? 0 : e.getValue();
            if (r <= 0) continue;
            out.add(e.getKey() + ":" + r);
        }
        for (String b : unlockedBranches) {
            if (b != null && !b.isBlank()) out.add("branch:" + b);
        }
        java.util.Collections.sort(out);
        return String.join(",", out);
    }

    public void talentsCsv(String csv) {
        talentRanks.clear();
        unlockedBranches.clear();
        if (csv == null || csv.isBlank()) return;
        for (String s : csv.split(",")) {
            String id = s.trim();
            if (id.isEmpty()) continue;

            if (id.startsWith("branch:")) {
                String branchId = id.substring("branch:".length()).trim();
                if (!branchId.isEmpty()) unlockedBranches.add(branchId);
                continue;
            }

            int colon = id.lastIndexOf(':');
            if (colon <= 0 || colon == id.length() - 1) {
                // legacy: just an id
                talentRanks.put(id, 1);
                continue;
            }

            String talentId = id.substring(0, colon).trim();
            String rankStr = id.substring(colon + 1).trim();
            if (talentId.isEmpty()) continue;
            try {
                int rank = Integer.parseInt(rankStr);
                if (rank > 0) talentRanks.put(talentId, rank);
            } catch (NumberFormatException ignored) {
                // fallback legacy
                talentRanks.put(id, 1);
            }
        }
    }

}
