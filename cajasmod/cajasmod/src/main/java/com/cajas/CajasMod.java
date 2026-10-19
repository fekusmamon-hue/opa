package com.cajas;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CajasMod implements ModInitializer {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Random RND = new Random();
    static Path dir, cfgFile, placeFile;
    static JsonObject cfg;
    static JsonObject placements = new JsonObject(); // "dimension|x,y,z" -> id de caja

    @Override
    public void onInitialize() {
        dir = FabricLoader.getInstance().getConfigDir().resolve("cajasmod");
        cfgFile = dir.resolve("cajas_config.json");
        placeFile = dir.resolve("colocadas.json");
        load();

        // Clic derecho = abrir | clic izquierdo = ver contenido
        UseBlockCallback.EVENT.register((p, w, h, hit) -> {
            if (w.isClientSide || h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            String id = placements.has(key(w.dimension().location().toString(), hit.getBlockPos()))
                    ? placements.get(key(w.dimension().location().toString(), hit.getBlockPos())).getAsString() : null;
            if (id == null) return InteractionResult.PASS;
            open((ServerPlayer) p, id);
            return InteractionResult.SUCCESS;
        });
        AttackBlockCallback.EVENT.register((p, w, h, pos, dir) -> {
            if (w.isClientSide) return InteractionResult.PASS;
            String k = key(w.dimension().location().toString(), pos);
            if (!placements.has(k)) return InteractionResult.PASS;
            preview((ServerPlayer) p, placements.get(k).getAsString());
            return InteractionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((p, w, h, e, hit) -> {
            if (w.isClientSide || h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            String id = tagId(e);
            if (id == null) return InteractionResult.PASS;
            open((ServerPlayer) p, id);
            return InteractionResult.SUCCESS;
        });
        AttackEntityCallback.EVENT.register((p, w, h, e, hit) -> {
            if (w.isClientSide) return InteractionResult.PASS;
            String id = tagId(e);
            if (id == null) return InteractionResult.PASS;
            preview((ServerPlayer) p, id);
            return InteractionResult.FAIL;
        });

        CommandRegistrationCallback.EVENT.register((disp, ctx, env) -> disp.register(
            Commands.literal("cajas").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reload").executes(c -> { load(); c.getSource().sendSuccess(() -> Component.literal("Recargado"), false); return 1; }))
                .then(Commands.literal("bloque").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    HitResult r = p.pick(6, 0, false);
                    if (!(r instanceof BlockHitResult b)) return 0;
                    placements.addProperty(key(p.level().dimension().location().toString(), b.getBlockPos()), StringArgumentType.getString(c, "id"));
                    save(); c.getSource().sendSuccess(() -> Component.literal("Bloque convertido en caja"), false); return 1;
                })))
                .then(Commands.literal("pokemon").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(c, "id");
                    JsonObject box = box(id);
                    if (box == null || !box.has("pokemon")) return 0;
                    CobbleBridge.spawnStatic(p.serverLevel(), box.getAsJsonObject("pokemon").get("species").getAsString(),
                            p.getX(), p.getY(), p.getZ(), "caja:" + id);
                    return 1;
                })))
                .then(Commands.literal("dar").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    open(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id"), true); return 1;
                })))
        ));
    }

    static String key(String dim, BlockPos p) { return dim + "|" + p.getX() + "," + p.getY() + "," + p.getZ(); }

    static String tagId(net.minecraft.world.entity.Entity e) {
        for (String t : e.getTags()) if (t.startsWith("caja:")) return t.substring(5);
        return null;
    }

    static JsonObject box(String id) {
        for (JsonElement el : cfg.getAsJsonArray("boxes"))
            if (el.getAsJsonObject().get("id").getAsString().equals(id)) return el.getAsJsonObject();
        return null;
    }

    static void open(ServerPlayer p, String id) { open(p, id, false); }

    static void open(ServerPlayer p, String id, boolean free) {
        JsonObject b = box(id);
        if (b == null || !b.get("enabled").getAsBoolean()) { p.sendSystemMessage(Component.literal("Esta caja no esta disponible ahora.")); return; }
        int price = b.get("price").getAsInt();
        if (!free && price > 0 && !charge(p, price)) { p.sendSystemMessage(Component.literal("Necesitas " + price + " monedas de oro.")); return; }
        JsonArray rewards = b.getAsJsonArray("rewards");
        int total = 0;
        for (JsonElement r : rewards) total += r.getAsJsonObject().get("chance").getAsInt();
        int roll = RND.nextInt(Math.max(total, 1)), acc = 0;
        for (JsonElement el : rewards) {
            JsonObject r = el.getAsJsonObject();
            acc += r.get("chance").getAsInt();
            if (roll < acc) { give(p, r); return; }
        }
    }

    static void give(ServerPlayer p, JsonObject r) {
        if (r.get("type").getAsString().equals("pokemon")) {
            for (int i = 0; i < r.get("count").getAsInt(); i++)
                CobbleBridge.give(p, r.get("pool").getAsString(), r.has("shiny") && r.get("shiny").getAsBoolean(),
                        r.has("hidden_ability") && r.get("hidden_ability").getAsBoolean(), RND);
        } else {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.get("item").getAsString()));
            ItemStack st = new ItemStack(item, r.get("count").getAsInt());
            p.getInventory().placeItemBackInInventory(st);
            p.sendSystemMessage(Component.literal("Recibiste: " + st.getCount() + "x " + st.getHoverName().getString()));
        }
    }

    static void preview(ServerPlayer p, String id) {
        JsonObject b = box(id);
        if (b == null) return;
        int total = 0;
        for (JsonElement r : b.getAsJsonArray("rewards")) total += r.getAsJsonObject().get("chance").getAsInt();
        p.sendSystemMessage(Component.literal("== " + b.get("name").getAsString() + " (precio " + b.get("price").getAsInt() + " monedas de oro) =="));
        for (JsonElement el : b.getAsJsonArray("rewards")) {
            JsonObject r = el.getAsJsonObject();
            double pct = 100.0 * r.get("chance").getAsInt() / Math.max(total, 1);
            String what = r.get("type").getAsString().equals("pokemon")
                    ? "Pokemon " + (r.has("shiny") && r.get("shiny").getAsBoolean() ? "shiny " : "")
                      + (r.has("hidden_ability") && r.get("hidden_ability").getAsBoolean() ? "con HA " : "") + "(" + r.get("pool").getAsString() + ")"
                    : r.get("count").getAsInt() + "x " + r.get("item").getAsString();
            p.sendSystemMessage(Component.literal(String.format(" - %s: %.1f%%", what, pct)));
        }
    }

    /** Cobra monedas (items) del inventario del jugador. */
    static boolean charge(ServerPlayer p, int price) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(cfg.get("currency_item").getAsString()));
        int need = price, have = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) have += inv.getItem(i).getCount();
        if (have < need) return false;
        for (int i = 0; i < inv.getContainerSize() && need > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(item)) continue;
            int take = Math.min(need, st.getCount());
            st.shrink(take); need -= take;
        }
        return true;
    }

    static void load() {
        try {
            Files.createDirectories(dir);
            if (!Files.exists(cfgFile))
                try (InputStream in = CajasMod.class.getResourceAsStream("/cajas_config.json")) { Files.copy(in, cfgFile); }
            cfg = JsonParser.parseString(Files.readString(cfgFile)).getAsJsonObject();
            if (Files.exists(placeFile)) placements = JsonParser.parseString(Files.readString(placeFile)).getAsJsonObject();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    static void save() {
        try { Files.writeString(placeFile, GSON.toJson(placements)); } catch (IOException ignored) {}
    }
}
