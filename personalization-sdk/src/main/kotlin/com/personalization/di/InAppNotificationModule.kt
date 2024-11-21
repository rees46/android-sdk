//package com.personalization.di
//
//import androidx.fragment.app.FragmentManager
//import com.personalization.api.managers.InAppNotificationManager
//import com.personalization.features.inAppNotification.impl.InAppNotificationManagerImpl
//import dagger.Module
//import dagger.Provides
//import javax.inject.Singleton
//
//@Module
//class InAppNotificationModule {
//
//    @Singleton
//    @Provides
//    fun provideInAppNotificationManager(fragmentManager: FragmentManager): InAppNotificationManager {
//        return InAppNotificationManagerImpl(fragmentManager = fragmentManager)
//    }
//}
