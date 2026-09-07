package com.streamzee.data

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource

@OptIn(UnstableApi::class)
internal fun createOfflineDataSourceFactory(cache: Cache): CacheDataSource.Factory =
    CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(null)
        .setCacheWriteDataSinkFactory(null)
