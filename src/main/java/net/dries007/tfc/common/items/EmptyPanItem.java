package net.dries007.tfc.common.items;


import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.dries007.tfc.common.TFCTags;

public class EmptyPanItem extends Item
{
    private static ItemStack fill(BlockState state)
    {
        final ItemStack stack = new ItemStack(TFCItems.FILLED_PAN.get());
        stack.getOrCreateTag().put("state", NbtUtils.writeBlockState(state));
        return stack;
    }

    public EmptyPanItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        final Player player = context.getPlayer();
        if (player != null)
        {
            final Level level = context.getLevel();
            final BlockPos pos = context.getClickedPos();
            final BlockState state = level.getBlockState(pos);
            final ItemStack stack = player.getItemInHand(context.getHand());
            if (state.is(TFCTags.Blocks.CAN_BE_PANNED))
            {
                if (level.isClientSide) return InteractionResult.SUCCESS;

                level.destroyBlock(pos, false, player);
                if (!player.isCreative()) stack.shrink(1);
                player.awardStat(Stats.ITEM_USED.get(this));

                final ItemStack putStack = fill(state);
                if (!player.getInventory().add(putStack)) player.drop(putStack, false);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }
}
