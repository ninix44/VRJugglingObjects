package org.vmstudio.jugglingobjects.fabric;

import net.fabricmc.api.ModInitializer;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.jugglingobjects.core.client.ExampleAddonClient;
import org.vmstudio.jugglingobjects.core.common.JugglingObjectsNetworking;
import org.vmstudio.jugglingobjects.core.network.NetworkHelper;
import org.vmstudio.jugglingobjects.core.server.ExampleAddonServer;
import org.vmstudio.jugglingobjects.fabric.network.FabricNetworkChannel;

public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        NetworkHelper.setChannel(new FabricNetworkChannel());
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
