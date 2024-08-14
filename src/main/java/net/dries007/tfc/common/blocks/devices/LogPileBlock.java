/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.blocks.devices;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.LogPileBlockEntity;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blocks.EntityBlockExtension;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.IForgeBlockExtension;
import net.dries007.tfc.common.blocks.TFCBlockStateProperties;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.util.Helpers;

public class LogPileBlock extends DeviceBlock implements IForgeBlockExtension, EntityBlockExtension
{
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public static final IntegerProperty COUNT = TFCBlockStateProperties.COUNT_1_16;

    public static final VoxelShape[][] SHAPES_BY_DIR_BY_COUNT = Util.make(new VoxelShape[2][16], shapes -> {
        double[][] box2ByCount = new double[16][6];
        double[][] box1ByCount = new double[16][6];
        int layer;
        int row;
        for (int i = 0; i < 16; i++)
        {
            layer = i / 4;
            row = i % 4 + 1;
            box2ByCount[i] = new double[] {0, 4 * layer, 0, 16, 4 * layer + 4, 4 * row};
            box1ByCount[i] = new double[] {0, 0, 0, 16, 4 * (layer), 16};
        }

        for (int dir = 0; dir < 2; dir++)
        {
            Direction direction = Direction.SOUTH;
            if (dir == 1)
            {
                direction = Direction.EAST;
            }

            for (int count = 0; count < 16; count++)
            {
                VoxelShape box1 = Helpers.rotateShape(direction, box1ByCount[count][0], box1ByCount[count][1], box1ByCount[count][2], box1ByCount[count][3], box1ByCount[count][4], box1ByCount[count][5]);
                VoxelShape box2 = Helpers.rotateShape(direction, box2ByCount[count][0], box2ByCount[count][1], box2ByCount[count][2], box2ByCount[count][3], box2ByCount[count][4], box2ByCount[count][5]);
                shapes[dir][count] = Shapes.or(box1, box2);
            }
        }
    });

    public LogPileBlock(ExtendedProperties properties)
    {
        super(properties, InventoryRemoveBehavior.DROP);
        registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.X).setValue(COUNT, 1));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        BurningLogPileBlock.lightLogPile(level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis()).setValue(COUNT, 1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder.add(AXIS).add(COUNT));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor levelAccess, BlockPos currentPos, BlockPos facingPos)
    {
        if (!levelAccess.isClientSide() && levelAccess instanceof Level level)
        {
            if ((facing == Direction.DOWN && !facingState.isFaceSturdy(levelAccess, facingPos, Direction.UP)) && !(facingState.getBlock() instanceof LogPileBlock))
            {
                return Blocks.AIR.defaultBlockState();
            }
            if (Helpers.isBlock(facingState, BlockTags.FIRE))
            {
                BurningLogPileBlock.lightLogPile(level, currentPos);
            }
        }
        return super.updateShape(state, facing, facingState, levelAccess, currentPos, facingPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!player.isShiftKeyDown())
        {
            level.getBlockEntity(pos, TFCBlockEntities.LOG_PILE.get()).ifPresent(logPile -> {
                if (Helpers.isItem(stack.getItem(), TFCTags.Items.LOG_PILE_LOGS))
                {
                    if (!level.isClientSide)
                    {
                        if (Helpers.insertOne(logPile, stack))
                        {
                            Helpers.playPlaceSound(level, pos, state);
                            stack.shrink(1);
                        }

                        // TODO gross and protoype-y, should act like ingot piles
                        else if (level.getBlockState(pos.above()).isAir())
                        {
                            level.setBlockAndUpdate(pos.above(), TFCBlocks.LOG_PILE.get().defaultBlockState());
                            if (level.getBlockEntity(pos.above()) instanceof LogPileBlockEntity pileAbove)
                            {
                                BlockState stateAbove = level.getBlockState(pos.above());
                                if (Helpers.insertOne(pileAbove, stack))
                                {
                                    Helpers.playPlaceSound(level, pos.above(), stateAbove);
                                    stack.shrink(1);
                                }
                                else
                                {
                                    level.removeBlock(pos.above(), false);
                                }
                            }
                        }
                        else if (level.getBlockState(pos.above()).getBlock() instanceof LogPileBlock pileBlockAbove)
                        {
                            BlockState stateAbove = level.getBlockState(pos.above());
                            pileBlockAbove.useItemOn(stack, stateAbove, level, pos.above(), player, hand, hitResult);
                        }
                    }
                }
                else
                {
                    if (player instanceof ServerPlayer serverPlayer)
                    {
                        serverPlayer.openMenu(logPile, pos);
                    }
                }
            });
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player)
    {
        return level.getBlockEntity(pos, TFCBlockEntities.LOG_PILE.get())
            .map(pile -> {
                for (int i = 0; i < pile.getInventory().getSlots(); i++)
                {
                    final ItemStack stack = pile.getInventory().getStackInSlot(i);
                    if (!stack.isEmpty())
                    {
                        return stack.copy();
                    }
                }
                return ItemStack.EMPTY;
            }).orElse(ItemStack.EMPTY);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        BlockState blockstate = level.getBlockState(pos.below());
        return Block.isFaceFull(blockstate.getCollisionShape(level, pos.below()), Direction.UP) || blockstate.getBlock() instanceof LogPileBlock;
    }

    protected VoxelShape getShapeByDirByCount(Direction.Axis axis, int count)
    {
        count--;
        if (axis == Direction.Axis.X)
        {
            return SHAPES_BY_DIR_BY_COUNT[0][count];
        }
        return SHAPES_BY_DIR_BY_COUNT[1][count];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter levle, BlockPos pos, CollisionContext context)
    {
        return getShapeByDirByCount(state.getValue(AXIS), state.getValue(COUNT));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return getShapeByDirByCount(state.getValue(AXIS), state.getValue(COUNT));
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return getShapeByDirByCount(state.getValue(AXIS), state.getValue(COUNT));
    }
}
