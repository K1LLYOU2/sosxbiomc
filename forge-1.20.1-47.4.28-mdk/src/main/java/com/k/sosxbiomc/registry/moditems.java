package com.k.sosxbiomc.registry;

import com.k.sosxbiomc.SOSxBiomc;
import com.k.sosxbiomc.item.syringeitem;
import com.k.sosxbiomc.item.third_eye_item;
import com.k.sosxbiomc.item.third_eye_item;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class moditems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SOSxBiomc.MOD_ID);

    public static final RegistryObject<Item> EMPTY_SYRINGE =
            ITEMS.register("empty_syringe",
                    () -> new syringeitem(
                            new Item.Properties().stacksTo(1)
                    ));

    public static final RegistryObject<Item> THIRD_EYE =
        ITEMS.register("third_eye",
                () -> new third_eye_item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> MUTATE_BONE =
            ITEMS.register("mutate_bone",
                    () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> MUTATE_MEAT =
            ITEMS.register("mutate_meat",
                    () -> new Item(new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}