package com.personalization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Java callers see only the constructors the compiler emits. The seven-argument one is what hosts
 * wrote against 2.38; it has to keep compiling as the config grows, and the parameter added since
 * then must take its default.
 */
public class Rees46ConfigJavaTest {

    @Test
    public void theSevenArgumentConstructorStillCompiles() {
        Rees46Config config = new Rees46Config("shop", "api.rees46.ru", "android", true, false, true, "SDK");

        assertEquals("shop", config.getShopId());
        assertTrue(config.getEnableAutoPopupPresentation());
    }

    @Test
    public void trailingDefaultsMayBeLeftOut() {
        Rees46Config config = new Rees46Config("shop");

        assertEquals("api.rees46.ru", config.getApiDomain());
        assertEquals("SDK", config.getTag());
    }
}
