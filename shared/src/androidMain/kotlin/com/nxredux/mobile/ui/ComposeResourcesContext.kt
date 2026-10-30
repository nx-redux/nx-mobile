package com.nxredux.mobile.ui

import android.content.Context
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext

/**
 * Compose resources find their context through a content provider, and the app's providers start only in the main
 * process: a process of its own (the game's) sets it here before any screen reads a resource.
 */
@OptIn(ExperimentalResourceApi::class)
fun initComposeResources(context: Context) = setResourceReaderAndroidContext(context.applicationContext)
