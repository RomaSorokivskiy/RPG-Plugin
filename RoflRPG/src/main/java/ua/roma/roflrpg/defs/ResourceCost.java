package ua.roma.roflrpg.defs;

public record ResourceCost(ResourceType type, double amount) {
    public static ResourceCost none() { return new ResourceCost(ResourceType.NONE, 0); }
}
