package com.creaturestudio.block;

import com.creaturestudio.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Mesa de trabajo: al usarla se abre el Estudio de Criaturas. */
public class CreatureStudioBlock extends Block {

    public CreatureStudioBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            // Solo se ejecuta en el cliente; en un servidor dedicado esta rama nunca se alcanza.
            ClientAccess.openLibrary();
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
