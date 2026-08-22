package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.command.RecruitCommandAuthority;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/**
 * Native NeoForge serverbound payload for hiring recruits.
 *
 * This intentionally does not go through CoreLib's legacy Message compatibility
 * wrapper. Hiring is a critical interaction and must execute reliably on a
 * dedicated server.
 */
public class MessageHire implements CustomPacketPayload {

    public static final Type<MessageHire> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "message_hire")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MessageHire> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MessageHire decode(RegistryFriendlyByteBuf buf) {
            return new MessageHire(buf.readUUID(), buf.readUUID(), buf.readUUID());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, MessageHire packet) {
            buf.writeUUID(packet.player);
            buf.writeUUID(packet.recruit);
            buf.writeUUID(packet.groupUUID);
        }
    };

    private final UUID player;
    private final UUID recruit;
    private final UUID groupUUID;

    public MessageHire(UUID player, UUID recruit, UUID groupUUID) {
        this.player = player;
        this.recruit = recruit;
        this.groupUUID = groupUUID;
    }

    @Override
    public Type<MessageHire> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            if (!sender.getUUID().equals(this.player)) {
                sender.sendSystemMessage(Component.literal("Recruits: hire request rejected because the player ID did not match."));
                return;
            }

            if (RecruitEvents.recruitsGroupsManager == null) {
                sender.sendSystemMessage(Component.literal("Recruits: groups are not ready on the server yet."));
                return;
            }

            RecruitsGroup group = RecruitCommandAuthority.ownedGroup(sender, groupUUID);
            if (group == null) {
                List<RecruitsGroup> ownedGroups = RecruitEvents.recruitsGroupsManager.getPlayerGroups(sender);
                if (!ownedGroups.isEmpty()) {
                    group = ownedGroups.get(0);
                    RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(sender);
                }
            }

            if (group == null) {
                sender.sendSystemMessage(Component.literal("Recruits: no valid group was available for hiring."));
                return;
            }

            List<AbstractRecruitEntity> matches = sender.getCommandSenderWorld().getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    sender.getBoundingBox().inflate(16.0D),
                    v -> v.getUUID().equals(this.recruit) && v.isAlive()
            );

            if (matches.isEmpty()) {
                sender.sendSystemMessage(Component.literal("Recruits: the recruit could not be found on the server. Try standing closer and hiring again."));
                return;
            }

            AbstractRecruitEntity target = matches.get(0);
            if (target.isOwned()) {
                sender.sendSystemMessage(Component.literal("Recruits: that recruit is already owned."));
                return;
            }
            if (!target.canBeHired()) {
                sender.sendSystemMessage(Component.literal("Recruits: that recruit cannot currently be hired."));
                return;
            }

            if (!CommandEvents.handleRecruiting(sender, group, target, true)) {
                sender.sendSystemMessage(Component.literal("Recruits: the server rejected the hire. Check your currency/recruit limit; if both are fine, another mod may be cancelling the hire event."));
            }
        });
    }
}
