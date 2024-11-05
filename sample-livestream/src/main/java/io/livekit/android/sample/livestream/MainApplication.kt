/*
 * Copyright 2023 LiveKit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.livekit.android.sample.livestream

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import io.livekit.android.LiveKit
import io.livekit.android.sample.livestream.di.initKoin
import io.livekit.android.sample.livestream.model.UsbSerialModel
import io.livekit.android.util.LoggingLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import timber.log.Timber
import timber.log.Timber.DebugTree

class MainApplication : Application(), ImageLoaderFactory {
    private val test:UsbSerialModel by inject()
    private val scope:CoroutineScope by inject()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(DebugTree())

        LiveKit.loggingLevel = LoggingLevel.VERBOSE
        LiveKit.create(this).release()

        initKoin {
            androidLogger()
            androidContext(this@MainApplication)
        }

        scope.launch {
            Timber.d("Woot.")
            test.state.collectLatest { state ->
                when(state) {
                    is UsbSerialModel.State.Closed -> Timber.d("closed")
                    is UsbSerialModel.State.Error -> Timber.d("error ${state.msg}")
                    UsbSerialModel.State.Init -> Timber.d("init...")
                    is UsbSerialModel.State.Open -> Timber.d("open pn: ${state.port.portNumber}")
                }
            }
        }

    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
