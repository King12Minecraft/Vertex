package dominion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * DominionStore
 * -------------
 * Save/load for a DominionWorld - its own isolated flat file (per
 * ROADMAP.md's Architecture Decisions: a persistent whole-server game
 * needs its own world-state store, never piggybacking on
 * ServerAccountStore's unrelated format). Same pipe-delimited,
 * type-tagged-line, backward-compatible-by-field-count convention every
 * other store in this project uses (see ServerAccountStore) - one line
 * per record, a leading tag distinguishes which kind since this single
 * file holds all of Dominion's state rather than one file per entity
 * type. Build order step 2 from DOMINION_DESIGN.md: the world now
 * survives a server restart. When to actually call save() (every tick?
 * on every order?) is a networking-layer decision, deliberately not
 * made here - this class only knows how to round-trip a DominionWorld,
 * not when that should happen.
 */
public class DominionStore
{
    private static final String STORE_FILE = "gamehub_dominion.dat";

    public DominionWorld load()
    {
        DominionWorld world = new DominionWorld();
        File file = new File(STORE_FILE);
        if (!file.exists())
        {
            return world;
        }

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.isEmpty())
                {
                    continue;
                }
                loadLine(world, line.split("\\|", -1));
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load Dominion world: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
        return world;
    }

    private void loadLine(DominionWorld world, String[] parts)
    {
        String tag = parts[0];
        if ("TICK".equals(tag) && parts.length >= 2)
        {
            world.setCurrentTick(Integer.parseInt(parts[1]));
        }
        else if ("PROVINCE".equals(tag) && parts.length >= 6)
        {
            Province province = new Province(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]), Terrain.valueOf(parts[4]));
            if (!parts[5].isEmpty())
            {
                province.setOwningNationId(Integer.valueOf(parts[5]));
            }
            world.addProvince(province);
        }
        else if ("NATION".equals(tag) && parts.length >= 6)
        {
            Nation nation = new Nation(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3]);
            nation.setTreasury(Integer.parseInt(parts[4]));
            nation.setHonor(Integer.parseInt(parts[5]));
            world.addNation(nation);
        }
        else if ("ARMY".equals(tag) && parts.length >= 5)
        {
            Army army = new Army(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
            if (parts.length >= 6 && !parts[5].isEmpty())
            {
                army.setMarchOrderTargetProvinceId(Integer.valueOf(parts[5]));
            }
            world.addArmy(army);
        }
        else if ("RELATION".equals(tag) && parts.length >= 4)
        {
            Integer effectiveFromTick = (parts.length >= 5 && !parts[4].isEmpty())
                ? Integer.valueOf(parts[4]) : null;
            world.addRelation(new DiplomaticRelation(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                RelationType.valueOf(parts[3]), effectiveFromTick));
        }
    }

    public synchronized void save(DominionWorld world)
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(STORE_FILE));
            writer.println("TICK|" + world.getCurrentTick());

            for (Province province : world.getProvinces())
            {
                writer.println("PROVINCE|" + province.getId() + "|" + province.getRow() + "|" + province.getCol()
                    + "|" + province.getTerrain().name() + "|"
                    + (province.getOwningNationId() == null ? "" : province.getOwningNationId()));
            }
            for (Nation nation : world.getNations())
            {
                writer.println("NATION|" + nation.getId() + "|" + nation.getAccountId() + "|" + nation.getName()
                    + "|" + nation.getTreasury() + "|" + nation.getHonor());
            }
            for (Army army : world.getArmies())
            {
                writer.println("ARMY|" + army.getId() + "|" + army.getNationId() + "|" + army.getTroopCount()
                    + "|" + army.getLocationProvinceId() + "|"
                    + (army.getMarchOrderTargetProvinceId() == null ? "" : army.getMarchOrderTargetProvinceId()));
            }
            for (DiplomaticRelation relation : world.getRelations())
            {
                writer.println("RELATION|" + relation.getNationAId() + "|" + relation.getNationBId() + "|"
                    + relation.getType().name() + "|"
                    + (relation.getEffectiveFromTick() == null ? "" : relation.getEffectiveFromTick()));
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save Dominion world: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }
}
