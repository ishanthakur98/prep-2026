package org.prep.arrays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class StockBuySellTest {

    @Test
    void shouldReturnMaxProfit() {
        assertEquals(5, StockBuyAndSell.maxProfit(new int[]{7, 1, 5, 3, 6, 4}));
    }

    @Test
    void shouldReturnZeroWhenNoProfitPossible() {
        assertEquals(0, StockBuyAndSell.maxProfit(new int[]{7, 6, 4, 3, 1}));
    }

    @Test
    void shouldHandleSingleElementArray() {
        assertEquals(0, StockBuyAndSell.maxProfit(new int[]{1}));
    }
}
