package dominion;

import java.util.ArrayList;
import java.util.List;

/**
 * DominionTickEngine
 * -------------------
 * The once-per-day resolution pass (see DOMINION_DESIGN.md's core loop):
 * advances the clock, then resolves every army's standing march order -
 * an uncontested claim if the target is unclaimed, a simple relocation if
 * it's already the army's own territory, or combat if marching into
 * contested enemy territory during an active war. Deliberately just this
 * core logic for now, with no networking/UI/persistence attached (build
 * order step 1 in DOMINION_DESIGN.md) - proven here in isolation first,
 * same "prove the core logic before wiring it up" approach ai/search's
 * Minimax took before any game used it.
 *
 * Combat is a decisive win/loss, not partial attrition - the loser's
 * armies at that province are destroyed outright. Matches V1's "no unit
 * types yet" scope; a more nuanced casualty model is a believable
 * follow-up once there's a reason to add it.
 */
public class DominionTickEngine
{
    /** Attacker's effective combat power is troop count scaled by this. A placeholder pending real playtesting to tune against - see DOMINION_DESIGN.md's "deliberately not decided here." */
    private static final double ATTACK_MODIFIER = 1.0;

    public void resolveTick(DominionWorld world)
    {
        int newTick = world.getCurrentTick() + 1;
        world.setCurrentTick(newTick);
        resolveArmyOrders(world, newTick);
    }

    private void resolveArmyOrders(DominionWorld world, int tick)
    {
        // Copies the collection first - resolveSingleMarch can remove armies (a losing
        // attacker, destroyed defenders) from the very map this iterates.
        for (Army army : new ArrayList<Army>(world.getArmies()))
        {
            Integer targetId = army.getMarchOrderTargetProvinceId();
            if (targetId == null)
            {
                continue;
            }

            Province target = world.getProvince(targetId);
            Province origin = world.getProvince(army.getLocationProvinceId());
            army.setMarchOrderTargetProvinceId(null);

            if (target == null || origin == null || !origin.isAdjacentTo(target))
            {
                // Invalid order (stale/out of range) - never trust it, drop it silently
                // rather than letting it corrupt state, same "server re-verifies
                // everything" rule as every other game's move validation.
                continue;
            }

            resolveSingleMarch(world, army, target, tick);
        }
    }

    private void resolveSingleMarch(DominionWorld world, Army army, Province target, int tick)
    {
        Integer defenderNationId = target.getOwningNationId();

        if (defenderNationId == null)
        {
            claimUncontested(army, target);
            return;
        }
        if (defenderNationId.intValue() == army.getNationId())
        {
            army.setLocationProvinceId(target.getId());
            return;
        }
        if (!world.isAtWar(army.getNationId(), defenderNationId.intValue(), tick))
        {
            // Can't march into another nation's territory without an active war - the
            // order simply fizzles, same as an illegal move being rejected elsewhere,
            // just with nobody to send a rejection message to yet (no networking here).
            return;
        }

        resolveCombat(world, army, target, defenderNationId.intValue());
    }

    private void claimUncontested(Army army, Province target)
    {
        army.setLocationProvinceId(target.getId());
        target.setOwningNationId(army.getNationId());
    }

    private void resolveCombat(DominionWorld world, Army attacker, Province target, int defenderNationId)
    {
        List<Army> defenders = new ArrayList<Army>();
        for (Army candidate : world.getArmiesAt(target.getId()))
        {
            if (candidate.getNationId() == defenderNationId)
            {
                defenders.add(candidate);
            }
        }

        int attackPower = (int) Math.round(attacker.getTroopCount() * ATTACK_MODIFIER);
        int defensePower = 0;
        for (Army defender : defenders)
        {
            defensePower += defender.getTroopCount();
        }
        defensePower = (int) Math.round(defensePower * target.getTerrain().getDefenseMultiplier());

        if (attackPower > defensePower)
        {
            for (Army defender : defenders)
            {
                world.removeArmy(defender.getId());
            }
            target.setOwningNationId(attacker.getNationId());
            attacker.setLocationProvinceId(target.getId());
        }
        else
        {
            world.removeArmy(attacker.getId());
        }
    }
}
