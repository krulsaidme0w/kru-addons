package dev.krul.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ToolsAddonContractTest {
    @Test
    void categoryRegistrationUsesMeteorCategoryCallback() {
        assertDoesNotThrow(() -> ToolsAddon.class.getDeclaredMethod("onRegisterCategories"));
    }
}
