package org.vmstudio.jugglingobjects.fabric;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.jugglingobjects.core.client.ExampleAddonClient;
import org.vmstudio.jugglingobjects.core.server.ExampleAddonServer;
import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        if (ModLoader.get().isDedicatedServer()) {
            VisorAPI.registerAddon(
                    new ExampleAddonServer()
            );
        } else {
            VisorAPI.registerAddon(
                    new ExampleAddonClient()
            );
        }
    }
}
