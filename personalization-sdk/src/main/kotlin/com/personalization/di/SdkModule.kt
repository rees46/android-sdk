package com.personalization.di

import android.content.Context
import com.personalization.RegisterManager
import com.personalization.api.managers.CartManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.api.managers.ProductsManager
import com.personalization.api.managers.RecommendationManager
import com.personalization.api.managers.SearchManager
import com.personalization.api.managers.TrackEventManager
import com.personalization.features.cart.CartManagerImpl
import com.personalization.features.inAppNotification.impl.InAppNotificationManagerImpl
import com.personalization.features.notification.domain.data.NotificationDataExtractor
import com.personalization.features.products.impl.ProductsManagerImpl
import com.personalization.features.recommendation.impl.RecommendationManagerImpl
import com.personalization.features.search.impl.SearchManagerImpl
import com.personalization.features.trackEvent.impl.TrackEventManagerImpl
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import com.personalization.sdk.domain.usecases.recommendation.GetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.recommendation.SetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import com.personalization.stories.StoriesManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class SdkModule {

    @Singleton
    @Provides
    fun provideRegisterManager(
        getPreferencesValueUseCase: GetPreferencesValueUseCase,
        savePreferencesValueUseCase: SavePreferencesValueUseCase,
        updateUserSettingsValueUseCase: UpdateUserSettingsValueUseCase,
        getUserSettingsValueUseCase: GetUserSettingsValueUseCase,
        sendNetworkMethodUseCase: SendNetworkMethodUseCase,
        executeQueueTasksUseCase: ExecuteQueueTasksUseCase,
        inAppNotificationManager: InAppNotificationManager
    ): RegisterManager = RegisterManager(
        getPreferencesValueUseCase = getPreferencesValueUseCase,
        savePreferencesValueUseCase = savePreferencesValueUseCase,
        updateUserSettingsValueUseCase = updateUserSettingsValueUseCase,
        getUserSettingsValueUseCase = getUserSettingsValueUseCase,
        sendNetworkMethodUseCase = sendNetworkMethodUseCase,
        executeQueueTasksUseCase = executeQueueTasksUseCase,
        inAppNotificationManager = inAppNotificationManager
    )

    @Singleton
    @Provides
    fun provideRecommendationManager(
        sendNetworkMethodUseCase: SendNetworkMethodUseCase
    ): RecommendationManager = RecommendationManagerImpl(
        sendNetworkMethodUseCase = sendNetworkMethodUseCase
    )

    @Singleton
    @Provides
    fun provideProductsManager(
        sendNetworkMethodUseCase: SendNetworkMethodUseCase
    ): ProductsManager = ProductsManagerImpl(
        sendNetworkMethodUseCase = sendNetworkMethodUseCase
    )

    @Singleton
    @Provides
    fun provideCartManager(
        sendNetworkMethodUseCase: SendNetworkMethodUseCase
    ): CartManager = CartManagerImpl(
        sendNetworkMethodUseCase = sendNetworkMethodUseCase
    )

    @Singleton
    @Provides
    fun provideTrackEventManager(
        getRecommendedByUseCase: GetRecommendedByUseCase,
        setRecommendedByUseCase: SetRecommendedByUseCase,
        sendNetworkMethodUseCase: SendNetworkMethodUseCase,
        inAppNotificationManager: InAppNotificationManager
    ): TrackEventManager = TrackEventManagerImpl(
        getRecommendedByUseCase = getRecommendedByUseCase,
        setRecommendedByUseCase = setRecommendedByUseCase,
        sendNetworkMethodUseCase = sendNetworkMethodUseCase,
        inAppNotificationManager = inAppNotificationManager,
    )

    @Singleton
    @Provides
    fun provideStoriesManager(
        setRecommendedByUseCase: SetRecommendedByUseCase,
        sendNetworkMethodUseCase: SendNetworkMethodUseCase
    ): StoriesManager = StoriesManager(
        setRecommendedByUseCase = setRecommendedByUseCase,
        sendNetworkMethodUseCase = sendNetworkMethodUseCase
    )

    @Singleton
    @Provides
    fun provideSearchManager(
        sendNetworkMethodUseCase: SendNetworkMethodUseCase
    ): SearchManager = SearchManagerImpl(
        sendNetworkMethodUseCase = sendNetworkMethodUseCase
    )

    @Singleton
    @Provides
    fun provideInAppNotificationManager(
        context: Context
    ): InAppNotificationManager {
        return InAppNotificationManagerImpl(context)
    }

    @Singleton
    @Provides
    fun provideNotificationDataExtractor(): NotificationDataExtractor {
        return NotificationDataExtractor()
    }

}
