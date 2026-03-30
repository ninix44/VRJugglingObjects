package org.vmstudio.jugglingobjects.core.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.vmstudio.jugglingobjects.core.network.NetworkHelper;

public final class JugglingObjectsNetworking {
    public static final ResourceLocation TOSS_ITEM_PACKET = new ResourceLocation(VisorJugglingObjects.MOD_ID, "toss_item");
    public static final ResourceLocation CATCH_ITEM_PACKET = new ResourceLocation(VisorJugglingObjects.MOD_ID, "catch_item");
    private static final String JUGGLE_TAG = "VRJugglingObjectsJuggleId";

    private static boolean initialized;

    private JugglingObjectsNetworking() {
    }

    public static void initCommon() {
        if (initialized) {
            return;
        }
        initialized = true;

        NetworkHelper.registerServerReceiver(TOSS_ITEM_PACKET, (buf, player) -> {
            boolean isMainHand = buf.readBoolean();
            double hx = buf.readDouble();
            double hy = buf.readDouble();
            double hz = buf.readDouble();
            double vx = buf.readDouble();
            double vy = buf.readDouble();
            double vz = buf.readDouble();

            spawnThrownItem(player, isMainHand, hx, hy, hz, vx, vy, vz);
        });

        NetworkHelper.registerServerReceiver(CATCH_ITEM_PACKET, (buf, player) -> {
            int entityId = buf.readInt();
            boolean isMainHand = buf.readBoolean();

            Entity entity = player.level().getEntity(entityId);
            if (!(entity instanceof ItemEntity itemEntity)) {
                return;
            }

            InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack caughtStack = itemEntity.getItem().copy();
            if (caughtStack.isEmpty()) {
                return;
            }

            clearJuggleTag(caughtStack);

            if (player.getItemInHand(hand).isEmpty()) {
                player.setItemInHand(hand, caughtStack);
            } else {
                player.getInventory().add(caughtStack);
            }

            itemEntity.discard();
        });
    }

    private static void spawnThrownItem(ServerPlayer player,
                                        boolean isMainHand,
                                        double hx,
                                        double hy,
                                        double hz,
                                        double vx,
                                        double vy,
                                        double vz) {
        InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack handStack = player.getItemInHand(hand);
        if (handStack.isEmpty()) {
            return;
        }

        ItemStack thrownStack = handStack.split(1);
        if (thrownStack.isEmpty()) {
            return;
        }

        markAsJugglingItem(thrownStack);

        Vec3 velocity = new Vec3(vx, vy, vz);
        Vec3 spawnOffset = velocity.lengthSqr() > 1.0E-5 ? velocity.normalize().scale(0.18) : Vec3.ZERO;

        ItemEntity itemEntity = new ItemEntity(player.level(), hx + spawnOffset.x, hy + 0.1 + spawnOffset.y, hz + spawnOffset.z, thrownStack);
        itemEntity.setDeltaMovement(
                velocity.x * 0.55,
                Math.max(velocity.y * 1.2, 0.4),
                velocity.z * 0.55
        );
        itemEntity.setPickUpDelay(40);
        player.level().addFreshEntity(itemEntity);
    }

    private static void markAsJugglingItem(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(JUGGLE_TAG, java.util.UUID.randomUUID());
    }

    private static void clearJuggleTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }

        tag.remove(JUGGLE_TAG);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }
}
