package org.vmstudio.jugglingobjects.core.client;

import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.jugglingobjects.core.client.overlays.VROverlayExample;
import org.vmstudio.jugglingobjects.core.common.VisorJugglingObjects;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExampleAddonClient implements VisorAddon {
    @Override
    public void onAddonLoad() {
        VisorAPI.addonManager().getRegistries()
                .overlays()
                .registerComponents(
                        List.of(
                                new VROverlayExample(
                                        this,
                                        VROverlayExample.ID
                                )
                        )
                );
    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "org.vmstudio.jugglingobjects.core.client";
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
