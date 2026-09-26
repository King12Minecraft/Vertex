package dominion;

/**
 * Army
 * ----
 * V1 has exactly one generic unit type (see DOMINION_DESIGN.md) - troop
 * count alone, no unit-type variety yet. marchOrderTargetProvinceId is
 * how a queued order survives from the moment it's given until the next
 * tick actually resolves it - null means this army has no standing order
 * and stays put.
 */
public class Army
{
    private final int id;
    private final int nationId;
    private int troopCount;
    private int locationProvinceId;
    private Integer marchOrderTargetProvinceId;

    public Army(int id, int nationId, int troopCount, int locationProvinceId)
    {
        this.id = id;
        this.nationId = nationId;
        this.troopCount = troopCount;
        this.locationProvinceId = locationProvinceId;
    }

    public int getId() { return id; }
    public int getNationId() { return nationId; }
    public int getTroopCount() { return troopCount; }
    public void setTroopCount(int troopCount) { this.troopCount = troopCount; }
    public int getLocationProvinceId() { return locationProvinceId; }
    public void setLocationProvinceId(int locationProvinceId) { this.locationProvinceId = locationProvinceId; }
    public Integer getMarchOrderTargetProvinceId() { return marchOrderTargetProvinceId; }
    public void setMarchOrderTargetProvinceId(Integer marchOrderTargetProvinceId) { this.marchOrderTargetProvinceId = marchOrderTargetProvinceId; }
}
