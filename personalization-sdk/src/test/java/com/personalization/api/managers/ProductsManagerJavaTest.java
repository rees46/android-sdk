package com.personalization.api.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.personalization.api.OnApiCallbackListener;
import com.personalization.api.responses.products.counters.ProductCountersResponse;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

/**
 * A Java implementation written before the typed product methods existed has to keep compiling,
 * and gets the typed methods on top of its listener-based ones.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProductsManagerJavaTest {

    /** Implements only the methods the interface had before the typed ones. */
    private static final class ListenerOnlyManager implements ProductsManager {
        String itemId;
        OnApiCallbackListener listener;

        @Override
        public void getProductsList(String brands, String merchants, String categories, String locations,
                                    Integer limit, Integer page, Map<String, ?> filters,
                                    OnApiCallbackListener listener) {
            this.listener = listener;
        }

        @Override
        public void getProductInfo(String itemId, OnApiCallbackListener listener) {
            this.itemId = itemId;
            this.listener = listener;
        }

        @Override
        public void getProductCounters(String item,
                                       Function1<? super ProductCountersResponse, Unit> onSuccess,
                                       Function2<? super Integer, ? super String, Unit> onError) {
        }
    }

    @Test
    public void typedProductInfoGoesThroughTheListenerMethod() {
        ListenerOnlyManager manager = new ListenerOnlyManager();
        int[] code = {0};

        manager.getProductInfo("868", product -> Unit.INSTANCE, (c, message) -> {
            code[0] = c;
            return Unit.INSTANCE;
        });
        assertEquals("868", manager.itemId);
        assertNotNull(manager.listener);
        manager.listener.onError(400, "bad request");

        assertEquals(400, code[0]);
    }

    @Test
    public void typedProductsListGoesThroughTheListenerMethod() {
        ListenerOnlyManager manager = new ListenerOnlyManager();
        int[] code = {0};

        manager.getProductsList(null, null, null, null, null, null, null, listing -> Unit.INSTANCE, (c, message) -> {
            code[0] = c;
            return Unit.INSTANCE;
        });
        assertNotNull(manager.listener);
        manager.listener.onError(500, "server error");

        assertEquals(500, code[0]);
    }

    @Test
    public void typedProductsListReportsAnArrayAnswerAsAnError() {
        ListenerOnlyManager manager = new ListenerOnlyManager();
        int[] code = {0};

        manager.getProductsList(null, null, null, null, null, null, null, listing -> Unit.INSTANCE, (c, message) -> {
            code[0] = c;
            return Unit.INSTANCE;
        });
        manager.listener.onSuccess(new JSONArray());

        assertEquals(-1, code[0]);
    }
}
