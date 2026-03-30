package org.vmstudio.jugglingobjects.forge;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.jugglingobjects.core.client.ExampleAddonClient;
import org.vmstudio.jugglingobjects.core.common.VisorJugglingObjects;
import org.vmstudio.jugglingobjects.core.server.ExampleAddonServer;
import net.minecraftforge.fml.common.Mod;

@Mod(VisorJugglingObjects.MOD_ID)
public class ExampleMod {
    public ExampleMod() {
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
