package org.vmstudio.jugglingobjects.core.client.tasks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vmstudio.jugglingobjects.core.common.JugglingObjectsNetworking;
import org.vmstudio.jugglingobjects.core.network.NetworkHelper;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.player.VRLocalPlayer;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseClient;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.tasks.RegisterVisorTask;
import org.vmstudio.visor.api.client.tasks.TaskType;
import org.vmstudio.visor.api.client.tasks.VisorTask;
import org.vmstudio.visor.api.client.gui.overlays.VROverlay;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.addon.VisorAddon;

@RegisterVisorTask
public class TaskJuggleItems extends VisorTask {
    public static final String ID = "juggle_items";
    private static final String OVERLAY_HOTBAR_MAIN = "hotbar_mainhand";
    private static final String OVERLAY_HOTBAR_OFF = "hotbar_offhand";
    private static final String OVERLAY_GAME_SCREEN = "game_screen";
    private static final String OVERLAY_KEYBOARD = "keyboard";
    private static final String OVERLAY_SETTINGS = "settings";
    private static final String OVERLAY_OPTIONS_MENU = "options_menu";

    private static final double TOSS_THRESHOLD = 0.15;
    private static final double PULL_RANGE = 0.8;
    private static final double CATCH_RANGE = 0.25;
    private static final int TRAIL_INTERVAL = 3;

    private Vec3 lastRelMain;
    private Vec3 lastRelOff;
    private int mainCooldown;
    private int offCooldown;

    public TaskJuggleItems(@NotNull VisorAddon owner) {
        super(owner);
    }

    @Override
    protected void onRun(@Nullable LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == null || mc.level == null || mc.isPaused()) {
            return;
        }

        if (isGuiInteractionBlocked(mc)) {
            onClear(player);
            return;
        }

        VRLocalPlayer vrPlayer = VisorAPI.client().getVRLocalPlayer();
        if (vrPlayer == null || !VisorAPI.clientState().playMode().canPlayVR()) {
            return;
        }

        PlayerPoseClient poseTick = vrPlayer.getPoseData(PlayerPoseType.TICK);
        PlayerPoseClient poseRel = vrPlayer.getPoseData(PlayerPoseType.RELATIVE);

        if (mainCooldown > 0) {
            mainCooldown--;
        }
        if (offCooldown > 0) {
            offCooldown--;
        }

        handleToss(mc, poseTick, poseRel, InteractionHand.MAIN_HAND, HandType.MAIN, true);
        renderJugglingTrails(mc, player);
        handleToss(mc, poseTick, poseRel, InteractionHand.OFF_HAND, HandType.OFFHAND, false);
        handleCatching(mc, poseTick);
    }

    @Override
    protected void onClear(@Nullable LocalPlayer player) {
        lastRelMain = null;
        lastRelOff = null;
        mainCooldown = 0;
        offCooldown = 0;
    }

    @Override
    public boolean isActive(@Nullable LocalPlayer player) {
        return player != null && VisorAPI.clientState().stateMode().isActive();
    }

    @Override
    public @NotNull TaskType getType() {
        return TaskType.VR_PLAYER_TICK;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    private void handleToss(Minecraft mc,
                            PlayerPoseClient poseTick,
                            PlayerPoseClient poseRel,
                            InteractionHand hand,
                            HandType handType,
                            boolean isMainHand) {
        if (mc.player == null) {
            return;
        }

        if (mc.player.getItemInHand(hand).isEmpty()) {
            updateLastRelativePosition(poseRel, isMainHand);
            return;
        }

        if ((isMainHand && mainCooldown > 0) || (!isMainHand && offCooldown > 0)) {
            return;
        }

        Vec3 currentRel = getRelativeHandPosition(poseRel, isMainHand);
        Vec3 lastRel = isMainHand ? lastRelMain : lastRelOff;
        if (lastRel == null) {
            setLastRelativePosition(isMainHand, currentRel);
            return;
        }

        Vec3 deltaRel = currentRel.subtract(lastRel);
        setLastRelativePosition(isMainHand, currentRel);

        Vector3f jomlDelta = new Vector3f((float) deltaRel.x, (float) deltaRel.y, (float) deltaRel.z);
        Vector3f worldOrientedDelta = poseTick.convertPositionFrom(PlayerPoseType.RELATIVE, jomlDelta)
                .add(poseTick.getOrigin().mul(-1, new Vector3f()));

        Vec3 finalVelocity = new Vec3(worldOrientedDelta.x(), worldOrientedDelta.y(), worldOrientedDelta.z());
        double speed = finalVelocity.length();
        if (speed < TOSS_THRESHOLD) {
            return;
        }

        if (finalVelocity.y <= speed * 0.5) {
            return;
        }

        Vec3 worldPos = getWorldHandPosition(poseTick, isMainHand);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(isMainHand);
        buf.writeDouble(worldPos.x);
        buf.writeDouble(worldPos.y);
        buf.writeDouble(worldPos.z);
        buf.writeDouble(finalVelocity.x);
        buf.writeDouble(finalVelocity.y);
        buf.writeDouble(finalVelocity.z);

        NetworkHelper.sendToServer(JugglingObjectsNetworking.TOSS_ITEM_PACKET, buf);

        VisorAPI.client().getInputManager().triggerHapticPulse(handType, 100f, 0.2f, 0.05f);
        mc.player.playSound(SoundEvents.ITEM_PICKUP, 0.2f, 1.35f + mc.level.random.nextFloat() * 0.15f);
        setCooldown(isMainHand, 15);
    }

    private void handleCatching(Minecraft mc, PlayerPoseClient poseTick) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        AABB catchBox = mc.player.getBoundingBox().inflate(3.0);
        Vec3 mainPos = getWorldHandPosition(poseTick, true);
        Vec3 offPos = getWorldHandPosition(poseTick, false);

        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, catchBox)) {
            if (item.tickCount <= 10 || item.getItem().isEmpty()) {
                continue;
            }

            boolean mainEmpty = mc.player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
            boolean offEmpty = mc.player.getItemInHand(InteractionHand.OFF_HAND).isEmpty();
            if (!mainEmpty && !offEmpty) {
                break;
            }

            double distMain = mainPos.distanceTo(item.position());
            double distOff = offPos.distanceTo(item.position());

            boolean canCatchMain = mainEmpty && distMain < PULL_RANGE;
            boolean canCatchOff = offEmpty && distOff < PULL_RANGE;
            boolean targetMain = canCatchMain && (!canCatchOff || distMain <= distOff);
            boolean targetOff = canCatchOff && (!canCatchMain || distOff < distMain);

            if (targetMain) {
                if (distMain < CATCH_RANGE) {
                    performCatch(item, true, HandType.MAIN);
                } else {
                    pullItemTowards(item, mainPos);
                }
            } else if (targetOff) {
                if (distOff < CATCH_RANGE) {
                    performCatch(item, false, HandType.OFFHAND);
                } else {
                    pullItemTowards(item, offPos);
                }
            } else if (item.isNoGravity()) {
                item.setNoGravity(false);
            }
        }
    }

    private void renderJugglingTrails(Minecraft mc, LocalPlayer player) {
        if (mc.level == null) {
            return;
        }

        AABB searchBox = player.getBoundingBox().inflate(6.0);
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, searchBox)) {
            if (!JugglingObjectsNetworking.isJugglingItem(item.getItem())) {
                continue;
            }

            spawnTrailParticles(mc, item);
        }
    }

    private void spawnTrailParticles(Minecraft mc, ItemEntity item) {
        if (item.onGround()) {
            return;
        }

        if (item.getDeltaMovement().lengthSqr() < 0.0025) {
            return;
        }

        if (item.tickCount % TRAIL_INTERVAL != 0) {
            return;
        }

        Vec3 pos = item.position();
        Vec3 velocity = item.getDeltaMovement();
        double px = pos.x + (mc.level.random.nextDouble() - 0.5) * 0.08;
        double py = pos.y + 0.1 + (mc.level.random.nextDouble() - 0.5) * 0.08;
        double pz = pos.z + (mc.level.random.nextDouble() - 0.5) * 0.08;

        ItemStack stack = item.getItem();

        if (stack.is(Items.TORCH) || stack.is(Items.CAMPFIRE) || stack.is(Items.LANTERN)) {
            mc.level.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.005, 0.0);
            if (item.tickCount % 6 == 0) {
                mc.level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.01, 0.0);
            }
            return;
        }

        if (stack.is(Items.SOUL_TORCH) || stack.is(Items.SOUL_LANTERN) || stack.is(Items.SOUL_CAMPFIRE)) {
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0.0, 0.005, 0.0);
            if (item.tickCount % 6 == 0) {
                mc.level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.008, 0.0);
            }
            return;
        }

        if (stack.is(Items.SNOWBALL) || stack.is(Items.SNOW_BLOCK) || stack.is(Items.POWDER_SNOW_BUCKET)) {
            mc.level.addParticle(ParticleTypes.SNOWFLAKE, px, py, pz, 0.0, 0.01, 0.0);
            if (item.tickCount % 6 == 0) {
                mc.level.addParticle(ParticleTypes.ITEM_SNOWBALL, px, py, pz, 0.0, 0.01, 0.0);
            }
            return;
        }

        if (stack.is(Items.SLIME_BALL)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.45f, 0.9f, 0.35f), 1.0f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.MAGMA_CREAM)) {
            mc.level.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.005, 0.0);
            mc.level.addParticle(new DustParticleOptions(new Vector3f(1.0f, 0.45f, 0.1f), 0.9f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.ENDER_EYE) || stack.is(Items.CHORUS_FRUIT)) {
            mc.level.addParticle(ParticleTypes.PORTAL, px, py, pz, velocity.x * -0.08, 0.02, velocity.z * -0.08);
            return;
        }

        if (stack.is(Items.BLAZE_POWDER) || stack.is(Items.BLAZE_ROD)) {
            mc.level.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.01, 0.0);
            return;
        }

        if (stack.is(Items.GLOWSTONE_DUST)) {
            mc.level.addParticle(ParticleTypes.WAX_ON, px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.REDSTONE)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 1.0f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.AMETHYST_SHARD)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.78f, 0.45f, 1.0f), 0.9f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.DIAMOND) || stack.is(Items.DIAMOND_BLOCK)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.25f, 0.95f, 1.0f), 0.9f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.EMERALD) || stack.is(Items.EMERALD_BLOCK)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.2f, 0.95f, 0.45f), 0.9f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_NUGGET) || stack.is(Items.GOLD_BLOCK) || stack.is(Items.RAW_GOLD)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.2f), 0.9f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_NUGGET) || stack.is(Items.IRON_BLOCK) || stack.is(Items.RAW_IRON)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.82f, 0.84f, 0.88f), 0.85f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.COPPER_INGOT) || stack.is(Items.COPPER_BLOCK) || stack.is(Items.RAW_COPPER)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.88f, 0.52f, 0.3f), 0.85f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_SCRAP)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.32f, 0.32f, 0.36f), 0.85f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.BONE)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.93f, 0.91f, 0.84f), 0.8f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.ROTTEN_FLESH)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.45f, 0.62f, 0.22f), 0.8f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.SPIDER_EYE) || stack.is(Items.FERMENTED_SPIDER_EYE)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.75f, 0.12f, 0.18f), 0.82f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.PAPER)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.97f, 0.97f, 0.93f), 0.75f), px, py, pz, 0.0, 0.002, 0.0);
            return;
        }

        if (stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.7f, 0.5f, 0.9f), 0.8f), px, py, pz, 0.0, 0.003, 0.0);
            return;
        }

        if (stack.is(Items.LAPIS_LAZULI) || stack.is(Items.LAPIS_BLOCK)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.2f, 0.35f, 0.95f), 0.85f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.QUARTZ) || stack.is(Items.QUARTZ_BLOCK)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.95f, 0.92f, 0.9f), 0.78f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || stack.is(Items.COAL_BLOCK)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.18f, 0.18f, 0.2f), 0.82f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.PRISMARINE_CRYSTALS) || stack.is(Items.HEART_OF_THE_SEA) || stack.is(Items.NAUTILUS_SHELL)) {
            mc.level.addParticle(ParticleTypes.BUBBLE, px, py, pz, 0.0, 0.02, 0.0);
            return;
        }

        if (stack.is(Items.ECHO_SHARD)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.15f, 0.65f, 0.9f), 0.9f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.NETHER_STAR)) {
            mc.level.addParticle(ParticleTypes.END_ROD, px, py, pz, 0.0, 0.01, 0.0);
            return;
        }

        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, px, py, pz, 0.0, 0.02, 0.0);
            return;
        }

        if (stack.is(Items.FEATHER)) {
            mc.level.addParticle(ParticleTypes.CLOUD, px, py, pz, 0.0, 0.005, 0.0);
            return;
        }

        if (stack.is(Items.HONEY_BOTTLE) || stack.is(Items.HONEYCOMB)) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.95f, 0.7f, 0.15f), 0.9f), px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(ItemTags.MUSIC_DISCS)) {
            mc.level.addParticle(ParticleTypes.NOTE, px, py, pz, 0.0, 0.0, 0.0);
            return;
        }

        if (stack.is(Items.GLOW_INK_SAC)) {
            mc.level.addParticle(ParticleTypes.GLOW, px, py, pz, 0.0, 0.0, 0.0);
        }
    }

    private void performCatch(ItemEntity item, boolean isMainHand, HandType handType) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(item.getId());
        buf.writeBoolean(isMainHand);
        NetworkHelper.sendToServer(JugglingObjectsNetworking.CATCH_ITEM_PACKET, buf);

        VisorAPI.client().getInputManager().triggerHapticPulse(handType, 200f, 0.8f, 0.15f);
        mc.player.playSound(SoundEvents.ITEM_PICKUP, 0.8f, 2.0f);
        setCooldown(isMainHand, 5);
        item.discard();
    }

    private void pullItemTowards(ItemEntity item, Vec3 targetPos) {
        Vec3 diff = targetPos.subtract(item.position());
        if (diff.lengthSqr() <= 1.0E-5) {
            return;
        }

        item.setDeltaMovement(diff.normalize().scale(0.3));
        item.setNoGravity(true);
    }

    private void updateLastRelativePosition(PlayerPoseClient poseRel, boolean isMainHand) {
        setLastRelativePosition(isMainHand, getRelativeHandPosition(poseRel, isMainHand));
    }

    private void setLastRelativePosition(boolean isMainHand, Vec3 position) {
        if (isMainHand) {
            lastRelMain = position;
        } else {
            lastRelOff = position;
        }
    }

    private void setCooldown(boolean isMainHand, int ticks) {
        if (isMainHand) {
            mainCooldown = ticks;
        } else {
            offCooldown = ticks;
        }
    }

    private Vec3 getRelativeHandPosition(PlayerPoseClient poseRel, boolean isMainHand) {
        return jomlToVec3(isMainHand ? poseRel.getMainHand().getPosition() : poseRel.getOffhand().getPosition());
    }

    private Vec3 getWorldHandPosition(PlayerPoseClient poseTick, boolean isMainHand) {
        return jomlToVec3(isMainHand ? poseTick.getMainHand().getPosition() : poseTick.getOffhand().getPosition());
    }

    private Vec3 jomlToVec3(Vector3fc vec) {
        return new Vec3(vec.x(), vec.y(), vec.z());
    }

    private boolean isGuiInteractionBlocked(Minecraft mc) {
        if (mc.screen != null) {
            return true;
        }

        var guiManager = VisorAPI.client().getGuiManager();
        if (guiManager.getCursorHandler().isAnyHandFocused()) {
            return true;
        }

        return isOverlayVisible(guiManager.getOverlayManager().getOverlay(OVERLAY_HOTBAR_MAIN))
                || isOverlayVisible(guiManager.getOverlayManager().getOverlay(OVERLAY_HOTBAR_OFF))
                || isOverlayVisible(guiManager.getOverlayManager().getOverlay(OVERLAY_GAME_SCREEN))
                || isOverlayVisible(guiManager.getOverlayManager().getOverlay(OVERLAY_KEYBOARD))
                || isOverlayVisible(guiManager.getOverlayManager().getOverlay(OVERLAY_SETTINGS))
                || isOverlayVisible(guiManager.getOverlayManager().getOverlay(OVERLAY_OPTIONS_MENU));
    }

    private boolean isOverlayVisible(VROverlay overlay) {
        return overlay != null && overlay.isVisible();
    }
}
