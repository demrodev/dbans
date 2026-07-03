package me.demro.dbans.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UtilityClass
public final class L {

    private static final Logger LOGGER = LoggerFactory.getLogger("dBans");

    @Contract(pure = true)
    public static Logger get() {
        return LOGGER;
    }
}