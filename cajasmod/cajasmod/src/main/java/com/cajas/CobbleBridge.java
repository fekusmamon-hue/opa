package com.cajas;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/** Todo el codigo que toca la API de Cobblemon esta aqui, para arreglarlo en un solo sitio. */
public class CobbleBridge {
    static final Set<String> SPECIAL = Set.of("legendary", "mythical", "ultra_beast", "paradox");

    static boolean isSpecial(Species s) {
        for (String l : s.getLabels()) if (SPECIAL.contains(l)) return true;
        return false;
    }

    static List<Species> pool(String pool) {
        boolean special = pool.equals("especial");
        List<Species> out = new ArrayList<>();
        for (Species s : PokemonSpecies.INSTANCE.getImplemented()) {
            if (isSpecial(s) == special) out.add(s);
        }
        return out;
    }

    /** Devuelve el nombre de la habilidad oculta o null si la especie no tiene. */
    static String hiddenAbility(Species s) {
        // VERIFICAR en 1.8.1: forma de leer la habilidad oculta del AbilityPool.
        for (var pa : s.getAbilities()) {
            if (pa.getType().getClass().getSimpleName().toLowerCase().contains("hidden")) {
                return pa.getTemplate().getName();
            }
        }
        return null;
    }

    static boolean give(ServerPlayer player, String poolName, boolean shiny, boolean hidden, Random rnd) {
        List<Species> pool = pool(poolName);
        if (pool.isEmpty()) return false;
        for (int i = 0; i < 80; i++) {
            Species s = pool.get(rnd.nextInt(pool.size()));
            String ab = null;
            if (hidden) { ab = hiddenAbility(s); if (ab == null) continue; }
            PokemonProperties p = PokemonProperties.Companion.parse(
                    s.getResourceIdentifier().getPath() + " level=5");
            p.setShiny(shiny);
            if (ab != null) p.setAbility(ab);
            Pokemon pk = p.create();
            var storage = Cobblemon.INSTANCE.getStorage();
            if (!storage.getParty(player).add(pk)) storage.getPC(player).add(pk);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Recibiste: " + (shiny ? "shiny " : "") + s.getName()));
            return true;
        }
        return false;
    }

    static PokemonEntity spawnStatic(ServerLevel level, String species, double x, double y, double z, String tag) {
        PokemonProperties p = PokemonProperties.Companion.parse(species + " level=50");
        PokemonEntity e = p.createEntity(level);
        e.setPos(x, y, z);
        e.setNoAi(true);
        e.setInvulnerable(true);
        e.setPersistenceRequired();
        e.addTag(tag);
        level.addFreshEntity(e);
        return e;
    }
}
