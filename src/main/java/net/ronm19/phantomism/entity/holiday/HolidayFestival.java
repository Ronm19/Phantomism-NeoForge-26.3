package net.ronm19.phantomism.entity.holiday;

import java.time.LocalDate;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.ronm19.phantomism.Config;

public enum HolidayFestival {
    HALLOWEEN(10, List.of(Items.PUMPKIN_SEEDS, Items.PUMPKIN, Items.CARVED_PUMPKIN)),
    CHRISTMAS(12, List.of(Items.SNOWBALL)),
    EASTER(0, List.of(Items.EGG)),
    FIREWORK(7, List.of(Items.FIREWORK_ROCKET)),
    ST_PATRICKS(3, List.of(Items.EMERALD)),
    NEW_YEARS(1, List.of(Items.FIREWORK_ROCKET)),
    VALENTINES(2, List.of(Items.POPPY, Items.RED_TULIP));

    private final int month;
    private final List<Item> alternativeItems;
    HolidayFestival(int month, List<Item> alternativeItems) { this.month = month; this.alternativeItems = alternativeItems; }
    public List<Item> alternativeItems() { return alternativeItems; }
    public int month(int year, int fireworkMonth) {
        return this == EASTER ? HolidayCalendar.easter(year).getMonthValue() : this == FIREWORK && fireworkMonth >= 1 && fireworkMonth <= 12 ? fireworkMonth : month;
    }
    public boolean active(LocalDate date, int fireworkMonth) { return date.getMonthValue() == month(date.getYear(), fireworkMonth); }
    public float tamingChance(Item item) {
        if (!alternativeItems.contains(item)) { return 0; }
        if (this == HALLOWEEN) { return item == Items.PUMPKIN_SEEDS ? .5F : 1F; }
        return (float)Config.holidayAlternativeTamingChance();
    }
}
