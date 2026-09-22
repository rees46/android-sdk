package com.personalization.ui.components

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A next page of results is an insert at the end, not a rebuild of every card already bound. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationProductsAdapterTest {

    private val events = mutableListOf<String>()
    private val adapter = PersonalizationProductsAdapter(PersonalizationProductCard.Type.GRID).apply {
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                events += "changed"
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                events += "inserted $positionStart+$itemCount"
            }
        })
    }

    @Test
    fun `a next page is inserted after the cards already shown`() {
        adapter.items = products(0 until 20)
        events.clear()

        adapter.items = products(0 until 40)

        assertEquals(listOf("inserted 20+20"), events)
    }

    @Test
    fun `a different list is a full change`() {
        adapter.items = products(0 until 20)
        events.clear()

        adapter.items = products(100 until 140)

        assertEquals(listOf("changed"), events)
    }

    private fun products(ids: IntRange) = ids.map { PersonalizationProduct(id = "$it", name = "Item $it", price = "$it") }
}
