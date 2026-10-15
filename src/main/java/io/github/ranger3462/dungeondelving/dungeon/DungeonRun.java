package io.github.ranger3462.dungeondelving.dungeon;

import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A player's active dungeon run: which region it is in, its tier (1 to 4), and where to send the player back to when
 * they leave.
 */
public record DungeonRun(UUID player, int region, int tier, ResourceKey<Level> returnLevel, double returnX, double returnY, double returnZ,
        float returnYRot, float returnXRot) {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 4;

    public static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", player);
        tag.putInt("region", region);
        tag.putInt("tier", tier);
        tag.putString("return_level", returnLevel.location().toString());
        tag.putDouble("return_x", returnX);
        tag.putDouble("return_y", returnY);
        tag.putDouble("return_z", returnZ);
        tag.putFloat("return_y_rot", returnYRot);
        tag.putFloat("return_x_rot", returnXRot);
        return tag;
    }

    public static DungeonRun load(CompoundTag tag) {
        return new DungeonRun(
                tag.getUUID("player"),
                tag.getInt("region"),
                tag.contains("tier") ? clampTier(tag.getInt("tier")) : MIN_TIER,
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("return_level"))),
                tag.getDouble("return_x"),
                tag.getDouble("return_y"),
                tag.getDouble("return_z"),
                tag.getFloat("return_y_rot"),
                tag.getFloat("return_x_rot"));
    }
}
