package com.maxivlock.mixin;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Se engancha al calculo de IVs de Cobbreeding. Si los dos padres tienen 31
 * en una estadistica, el huevo sale con 31 en esa estadistica.
 *
 * Para limitarlo solo a Ataque, deja unicamente Stats.ATTACK en LOCKED.
 */
@Mixin(targets = "ludichat.cobbreeding.BreedingUtilities", remap = false)
public abstract class BreedingUtilitiesMixin {

    private static final Logger MAXIVLOCK_LOG = LoggerFactory.getLogger("maxivlock");

    private static final Stat[] LOCKED = {
            Stats.HP, Stats.ATTACK, Stats.DEFENCE,
            Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };

    @Inject(
            method = "calcStats(Lkotlin/Pair;)Lcom/cobblemon/mod/common/pokemon/IVs;",
            at = @At("RETURN"),
            remap = false
    )
    private void maxivlock$lockSharedMaxIvs(Pair<Pokemon, Pokemon> parents,
                                            CallbackInfoReturnable<IVs> cir) {
        IVs egg = cir.getReturnValue();
        if (egg == null) return;
        IVs a = parents.getFirst().getIvs();
        IVs b = parents.getSecond().getIvs();
        for (Stat stat : LOCKED) {
            Integer va = a.get(stat);
            Integer vb = b.get(stat);
            if (va != null && vb != null && va == 31 && vb == 31) {
                egg.set(stat, 31);
                MAXIVLOCK_LOG.info("Huevo: {} fijado a 31 (ambos padres tenian 31)", stat.getShowdownId());
            }
        }
    }
}
