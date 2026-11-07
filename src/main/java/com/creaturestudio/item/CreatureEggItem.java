package com.creaturestudio.item;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.entity.CustomCreature;
import com.creaturestudio.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Huevo que invoca una criatura personalizada concreta. */
public class CreatureEggItem extends Item {

    public CreatureEggItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(String id, @Nullable CreatureDefinition def) {
        ItemStack stack = new ItemStack(ModRegistry.CREATURE_EGG.get());
        stack.set(ModRegistry.CREATURE_ID.get(), id == null ? "" : id);
        if (def != null) {
            stack.set(DataComponents.ITEM_NAME,
                    Component.translatable("item.creaturestudio.creature_egg.named", def.name));
        }
        return stack;
    }

    @Nullable
    public static String getCreatureId(ItemStack stack) {
        return stack.get(ModRegistry.CREATURE_ID.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        CreatureDefinition def = CreatureStore.SERVER.get(getCreatureId(stack));
        if (def == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.creaturestudio.unknown_creature")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clicked);
        BlockPos pos = state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
        float yaw = player != null ? Mth.wrapDegrees(player.getYRot() + 180.0f) : level.getRandom().nextFloat() * 360.0f;
        CustomCreature creature = CustomCreature.spawn(serverLevel, def, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                yaw, true);
        if (creature != null && stack.has(DataComponents.CUSTOM_NAME)) {
            creature.setCustomName(stack.getHoverName());
        }
        if (creature != null && (player == null || !player.getAbilities().instabuild)) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CreatureDefinition d = CreatureStore.CLIENT.get(getCreatureId(stack));
        if (d == null) {
            tooltip.add(Component.translatable("tooltip.creaturestudio.unknown").withStyle(ChatFormatting.RED));
            return;
        }
        ChatFormatting color = switch (d.behavior) {
            case PASSIVE -> ChatFormatting.GREEN;
            case NEUTRAL -> ChatFormatting.YELLOW;
            case HOSTILE -> ChatFormatting.RED;
        };
        tooltip.add(Component.translatable("gui.creaturestudio.behavior." + d.behavior.key()).withStyle(color));
        tooltip.add(Component.translatable("tooltip.creaturestudio.stats",
                format(d.health), format(d.damage), format(d.scale)).withStyle(ChatFormatting.GRAY));
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal("id: " + d.id).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String format(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
