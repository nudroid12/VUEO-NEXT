package com.vueo.shared.core.player
import androidx.media3.datasource.DataSource
class ProviderHlsDataSource { class Factory(private val source: DataSource.Factory): DataSource.Factory { override fun createDataSource()=source.createDataSource() } }
