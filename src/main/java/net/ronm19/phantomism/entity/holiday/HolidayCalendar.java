package net.ronm19.phantomism.entity.holiday;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import net.ronm19.phantomism.Config;

public final class HolidayCalendar {
    private HolidayCalendar() {}
    public static LocalDate today() { return LocalDate.now(Clock.system(ZoneId.of(Config.holidayTimeZone()))); }
    public static boolean naturalSpawningAllowed(HolidayFestival festival) {
        return naturalSpawningAllowed(festival, today(), Config.allowOutOfSeasonHolidaySpawns(), Config.fireworkFestivalMonth());
    }
    public static boolean naturalSpawningAllowed(HolidayFestival festival, LocalDate date, boolean override, int fireworkMonth) {
        // Halloween remains pumpkin-gated at every natural placement/encounter path.
        // Its environmental encounter is eligible year-round; October remains its festival month.
        return festival == HolidayFestival.HALLOWEEN || override || festival.active(date, fireworkMonth);
    }
    public static LocalDate easter(int year) {
        if (year < 1583 || year > 9999) { throw new IllegalArgumentException("Gregorian Easter supports years 1583 through 9999"); }
        int century = year / 100;
        int goldenNumber = year % 19;
        int correction = (century - 17) / 25;
        int epact = century - century / 4 - (century - correction) / 3 + 19 * goldenNumber + 15;
        epact %= 30;
        epact -= epact / 28 * (1 - epact / 28 * (29 / (epact + 1)) * ((21 - goldenNumber) / 11));
        int weekday = (year + year / 4 + epact + 2 - century + century / 4) % 7;
        int offset = epact - weekday;
        int month = 3 + (offset + 40) / 44;
        int day = offset + 28 - 31 * (month / 4);
        return LocalDate.of(year, month, day);
    }
}
