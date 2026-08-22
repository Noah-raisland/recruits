package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.command.RecruitCommandAuthority;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.network.compat.RecruitsMessage;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/**
 * Serverbound hire payload.
 *
 * Unlike the other compatibility packets, this class implements the modern
 * CoreLib/NeoForge payload methods directly instead of reflecting into the old
 * FriendlyByteBuf/NetworkContext shape. This keeps hiring reliable on a
 * dedicated NeoForge server while leaving the rest of the port untouched.
 */
public class MessageHire implements RecruitsMessage<MessageHire> {

    public static final CustomPacketPayload.Type<MessageHire> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "message_hire")
    );

    private UUID player;
    private UUID recruit;
    private UUID groupUUID;

    public MessageHire() {
    }

    public MessageHire(UUID player, UUID recruit, UUID groupUUID) {
        this.player = player;
        this.recruit = recruit;
        this.groupUUID = groupUUID;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return PacketFlow.SERVERBOUND;
    }

    @Override
    public CustomPacketPayload.Type<MessageHire> type() {
        return TYPE;
    }

    @Override
    public MessageHire fromBytes(RegistryFriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.recruit = buf.readUUID();
        this.groupUUID = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUUID(this.recruit);
        buf.writeUUID(this.groupUUID);
    }

    @Override
    public void executeServerSide(IPayloadContext context) {
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
    }
}
