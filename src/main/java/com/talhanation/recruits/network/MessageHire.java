package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.command.RecruitCommandAuthority;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import com.talhanation.recruits.network.compat.RecruitsMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.PacketFlow;
import com.talhanation.recruits.network.compat.RecruitsNetworkContext;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessageHire implements RecruitsMessage<MessageHire> {

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

    public PacketFlow getExecutingSide()  {
        return PacketFlow.SERVERBOUND;
    }

    public void executeServerSide(RecruitsNetworkContext context) {
        ServerPlayer sender = Objects.requireNonNull(context.getSender());
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
            // Client group state can be stale after joining or migrating a world. Never use
            // another player's group; instead fall back to one of the sender's server-owned groups.
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

        final RecruitsGroup hireGroup = group;
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

        if (!CommandEvents.handleRecruiting(sender, hireGroup, target, true)) {
            // handleRecruiting already reports normal failures such as not enough currency or
            // hitting the recruit limit. This message also exposes event/mod cancellations.
            sender.sendSystemMessage(Component.literal("Recruits: the server rejected the hire. Check your currency/recruit limit; if both are fine, another mod may be cancelling the hire event."));
        }
    }

    public MessageHire fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.recruit = buf.readUUID();
        this.groupUUID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUUID(this.recruit);
        buf.writeUUID(this.groupUUID);
    }
}
