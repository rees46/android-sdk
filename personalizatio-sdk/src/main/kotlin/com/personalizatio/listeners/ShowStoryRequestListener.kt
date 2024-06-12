package com.personalizatio.listeners

import com.personalizatio.stories.models.Story

internal interface ShowStoryRequestListener {
    fun onShowStoryRequest(story: Story)
    fun onShowStoryRequest(storyId: Int): Boolean
}
