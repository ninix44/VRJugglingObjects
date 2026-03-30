package org.vmstudio.jugglingobjects.core.server;

import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.jugglingobjects.core.common.VisorJugglingObjects;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExampleAddonServer implements VisorAddon {
    @Override
    public void onAddonLoad() {

    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "org.vmstudio.jugglingobjects.core.server";
    }

    @Override
    public @NotNull String getAddonId() {
        return VisorJugglingObjects.MOD_ID;
    }

    @Override
    public @NotNull Component getAddonName() {
        return Component.literal(VisorJugglingObjects.MOD_NAME);
    }

    @Override
    public String getModId() {
        return VisorJugglingObjects.MOD_ID;
    }
}
