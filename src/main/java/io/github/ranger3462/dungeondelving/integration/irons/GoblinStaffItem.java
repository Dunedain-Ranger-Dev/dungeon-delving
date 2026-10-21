package io.github.ranger3462.dungeondelving.integration.irons;

import io.redspace.ironsspellbooks.item.weapons.StaffItem;

/**
 * A goblin-made casting staff. Works like Iron's Spells' own staves (its stats boost spells while held), but draws with
 * an ordinary handheld item model instead of Iron's custom staff renderer.
 */
public class GoblinStaffItem extends StaffItem {
    public GoblinStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCustomRendering() {
        return false;
    }
}
