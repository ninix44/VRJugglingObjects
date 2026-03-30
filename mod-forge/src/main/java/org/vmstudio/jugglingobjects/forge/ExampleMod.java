package org.vmstudio.jugglingobjects.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.jugglingobjects.core.client.ExampleAddonClient;
import org.vmstudio.jugglingobjects.core.common.VisorJugglingObjects;
import org.vmstudio.jugglingobjects.core.common.JugglingObjectsNetworking;
import org.vmstudio.jugglingobjects.core.network.NetworkHelper;
import org.vmstudio.jugglingobjects.core.server.ExampleAddonServer;
import org.vmstudio.jugglingobjects.forge.network.ForgeNetworkChannel;

@Mod(VisorJugglingObjects.MOD_ID)
public class ExampleMod {
    public ExampleMod() {
        NetworkHelper.setChannel(new ForgeNetworkChannel(new ResourceLocation(VisorJugglingObjects.MOD_ID, "network")));
        JugglingObjectsNetworking.initCommon();

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
