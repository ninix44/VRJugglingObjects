package org.vmstudio.jugglingobjects.core.common;

import net.minecraft.world.entity.player.Player;

public final class AddonUtils {
    private AddonUtils() {
    }

    public static boolean canJuggle(Player player) {
        return player != null
                && player.isAlive()
                && !player.isFallFlying()
                && !player.getAbilities().flying
                && !player.isSwimming()
                && !player.isVisuallySwimming()
                && !player.isInWater()
                && !player.onClimbable()
                && !player.isPassenger();
    }
}
