package com.talhanation.recruits.gametest;

import com.mojang.authlib.GameProfile;
import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.init.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Main.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecruitsPortGameTests {
    @GameTest(template = "port_empty", templateNamespace = Main.MOD_ID)
    public static void paidHiringAndPersistence(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PortHireTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 12));
        var recruit = spawn(helper, ModEntityTypes.RECRUIT.get());
        recruit.setCost(4);
        helper.assertTrue(CommandEvents.handleRecruiting(player, null, recruit, false), "Paid hire failed on server");
        helper.assertTrue(recruit.isOwned() && player.getUUID().equals(recruit.getOwnerUUID()), "Hiring did not assign owner");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 8, "Hiring charged incorrect currency");
        helper.assertTrue(recruit.getFollowState() == 2, "Hired recruit did not enter following state");
        recruit.setCustomName(Component.literal("Persistence Recruit"));
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        recruit.getInventory().setItem(0, new ItemStack(Items.BREAD, 7));
        CompoundTag saved = new CompoundTag();
        helper.assertTrue(recruit.save(saved), "Recruit did not save");
        var restored = ModEntityTypes.RECRUIT.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "Could not reconstruct saved recruit");
        restored.load(saved);
        helper.assertTrue(restored.isOwned() && player.getUUID().equals(restored.getOwnerUUID()), "Saved owner lost");
        helper.assertTrue(restored.getUUID().equals(recruit.getUUID()), "Saved UUID lost");
        helper.assertTrue(restored.getName().getString().equals("Persistence Recruit"), "Saved name lost");
        helper.assertTrue(restored.getMainHandItem().is(Items.IRON_SWORD), "Saved equipment lost");
        helper.assertTrue(restored.getInventory().countItem(Items.BREAD) == 7, "Saved inventory lost");
        helper.succeed();
    }

    @GameTest(template = "port_empty", templateNamespace = Main.MOD_ID)
    public static void insufficientCurrencyRejectsHiring(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PortPoorTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
        var recruit = spawn(helper, ModEntityTypes.RECRUIT.get());
        recruit.setCost(4);
        helper.assertTrue(!CommandEvents.handleRecruiting(player, null, recruit, false), "Unaffordable hire succeeded");
        helper.assertTrue(!recruit.isOwned(), "Failed hire assigned ownership");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 3, "Failed hire charged currency");
        helper.succeed();
    }

    @GameTest(template = "port_empty", templateNamespace = Main.MOD_ID)
    public static void allSixUnitTypesSpawn(GameTestHelper helper) {
        for (EntityType<? extends AbstractRecruitEntity> type : List.of(
                ModEntityTypes.RECRUIT.get(), ModEntityTypes.RECRUIT_SHIELDMAN.get(),
                ModEntityTypes.BOWMAN.get(), ModEntityTypes.CROSSBOWMAN.get(),
                ModEntityTypes.HORSEMAN.get(), ModEntityTypes.NOMAD.get())) {
            var recruit = spawn(helper, type);
            helper.assertTrue(recruit.isAlive() && recruit.getMaxHealth() > 0, "Unit lacks attributes: " + type);
            helper.assertTrue(!recruit.getMainHandItem().isEmpty(), "Unit lacks starting equipment: " + type);
        }
        helper.succeed();
    }

    private static AbstractRecruitEntity spawn(GameTestHelper helper, EntityType<? extends AbstractRecruitEntity> type) {
        var recruit = type.create(helper.getLevel());
        helper.assertTrue(recruit != null, "Unit creation failed: " + type);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        recruit.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        recruit.initSpawn();
        recruit.setNoAi(true);
        helper.getLevel().addFreshEntity(recruit);
        return recruit;
    }
}
