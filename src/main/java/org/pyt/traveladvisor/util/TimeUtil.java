package org.pyt.traveladvisor.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy hh:mm a");

    public static String toReadable(long epoch, int timezoneOffset) {

        Instant local =
                Instant.ofEpochSecond(epoch + timezoneOffset);

        return FORMAT.format(local.atOffset(ZoneOffset.UTC));
    }
}
