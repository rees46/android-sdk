package com.personalization.api.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.personalization.api.responses.category.CategoryResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

/**
 * A Java implementation written before the variant with the price range and sorting existed has to
 * keep compiling, and gets that variant on top of its own method.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CategoryManagerJavaTest {

    /** Implements only the method the interface had before the price range and sorting. */
    private static final class OldManager implements CategoryManager {
        String category;
        Integer page;
        Function1<? super CategoryResponse, Unit> onSuccess;

        @Override
        public void getCategory(String category, Integer limit, Integer page, String brands, String locations,
                                Map<String, ?> filters,
                                Function1<? super CategoryResponse, Unit> onSuccess,
                                Function2<? super Integer, ? super String, Unit> onError) {
            this.category = category;
            this.page = page;
            this.onSuccess = onSuccess;
        }
    }

    @Test
    public void variantWithPriceAndSortingGoesThroughTheOldMethod() {
        OldManager manager = new OldManager();
        int[] total = {0};

        manager.getCategory("tehnika-dlja-kuhni", 20, 2, null, null, null, 1000.0, 2000.0, "price", "asc",
            response -> {
                total[0] = response.getProductsTotal();
                return Unit.INSTANCE;
            },
            (code, message) -> Unit.INSTANCE);

        assertEquals("tehnika-dlja-kuhni", manager.category);
        assertEquals(Integer.valueOf(2), manager.page);
        assertNotNull(manager.onSuccess);
        manager.onSuccess.invoke(new CategoryResponse());
        assertEquals(0, total[0]);
    }
}
