package hu.systemmonitor.service.collector;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class DateTimeCollector {

    private DateTimeCollector() {
    }

    public static String getDateTimeInfo() {
        LocalDate now = LocalDate.now();
        LocalTime time = LocalTime.now();

        // Java 19+ szabványos
        Locale huLocale = Locale.of("hu", "HU");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy. MMMM d.", huLocale);
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM", huLocale);
        DateTimeFormatter dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", huLocale);

        String formattedTime = time.format(timeFormatter);
        String formattedDate = now.format(dateFormatter);
        String monthText = now.format(monthFormatter);
        String dayOfWeekText = now.format(dayOfWeekFormatter);

        WeekFields weekFields = WeekFields.of(huLocale);
        int weekOfYear = now.get(weekFields.weekOfYear());
        int dayOfYear = now.getDayOfYear();
        long daysSinceCommonEra = ChronoUnit.DAYS.between(LocalDate.of(1, 1, 1), now) + 1;

        return String.format("Idő: %s | Dátum: %s %s | %d. hét | Az év %d. napja. | Időszámításunk óta: %,d. nap.",
                formattedTime,
                formattedDate,
                dayOfWeekText,
                weekOfYear,
                dayOfYear,
                daysSinceCommonEra
        ).replace(",", " ");
    }
}