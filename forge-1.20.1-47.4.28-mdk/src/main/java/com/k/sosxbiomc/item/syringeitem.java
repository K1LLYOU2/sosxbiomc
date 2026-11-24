
package com.k.sosxbiomc.item;

import com.k.sosxbiomc.mutation.mutationdata;
import net.mcreator.sonsofsins.init.SonsOfSinsModFluids;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class syringeitem extends Item {

    public static final int CAPACITY = 100;
    public static final int DOSE_AMOUNT = 25;

    public syringeitem(Properties properties) {
        super(properties);
    }

    @Override
    public ICapabilityProvider initCapabilities(
            ItemStack stack,
            @Nullable CompoundTag nbt
    ) {
        return new FluidHandlerItemStack(stack, CAPACITY);
    }

    // Right-clicking a cow injects one dose.
    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity target,
            InteractionHand hand
    ) {
        if (!(target instanceof Cow cow)) {
            return InteractionResult.PASS;
        }

        Level level = cow.level();

        IFluidHandlerItem handler = FluidUtil.getFluidHandler(stack)
                .orElse(null);

        if (handler == null) {
            return InteractionResult.PASS;
        }

        FluidStack fluid = handler.getFluidInTank(0);

        if (fluid.getFluid() != SonsOfSinsModFluids.BLOOD.get()
                || fluid.getAmount() < DOSE_AMOUNT) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        FluidStack drained = handler.drain(
                DOSE_AMOUNT,
                IFluidHandler.FluidAction.EXECUTE
        );

        if (drained.getAmount() != DOSE_AMOUNT) {
            return InteractionResult.PASS;
        }

        // Adds 5 BEC, automatically respecting the 100 BEC cap.
        mutationdata.inject(cow);

        level.playSound(
                null,
                cow.blockPosition(),
                SoundEvents.GENERIC_DRINK,
                SoundSource.PLAYERS,
                1.0F,
                0.8F
        );

        return InteractionResult.sidedSuccess(false);
    }

    // Right-clicking a source Blood block fills the syringe.
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult hit = getPlayerPOVHitResult(
                level,
                player,
                ClipContext.Fluid.SOURCE_ONLY
        );

        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }

        FluidState fluidState = level.getFluidState(hit.getBlockPos());

        if (!fluidState.isSource()
                || fluidState.getType() != SonsOfSinsModFluids.BLOOD.get()) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        IFluidHandlerItem handler = FluidUtil.getFluidHandler(stack)
                .orElse(null);

        if (handler == null) {
            return InteractionResultHolder.pass(stack);
        }

        int currentAmount = handler.getFluidInTank(0).getAmount();
        int remainingCapacity = CAPACITY - currentAmount;

        if (remainingCapacity <= 0) {
            return InteractionResultHolder.pass(stack);
        }

        FluidStack blood = new FluidStack(
                SonsOfSinsModFluids.BLOOD.get(),
                remainingCapacity
        );

        int insertedAmount = handler.fill(
                blood,
                IFluidHandler.FluidAction.EXECUTE
        );

        if (insertedAmount <= 0) {
            return InteractionResultHolder.pass(stack);
        }

        level.playSound(
                null,
                hit.getBlockPos(),
                SoundEvents.BUCKET_FILL,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );

        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, level, tooltip, flag);

        FluidStack fluid = FluidUtil.getFluidHandler(stack)
                .map(handler -> handler.getFluidInTank(0))
                .orElse(FluidStack.EMPTY);

        int amount = fluid.getAmount();

        String fluidName = fluid.isEmpty()
                ? "Empty"
                : fluid.getDisplayName().getString();

        tooltip.add(Component.literal(
                fluidName + ": " + amount + " / " + CAPACITY + " mB"
        ));

        tooltip.add(Component.literal(
                (amount * 100 / CAPACITY) + "% full"
        ));
    }
}
